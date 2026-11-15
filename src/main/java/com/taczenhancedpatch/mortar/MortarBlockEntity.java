package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/*
 * 迫击炮方块实体。
 * 记录水平角, 仰角, 炮口口径, 已装填炮弹与控制者; 负责发射倒计时, 失控下落与座位管理。
 */
public class MortarBlockEntity extends BlockEntity {
    public static final int CALIBER_MIN = 60;
    public static final int CALIBER_MAX = 240;
    public static final float PITCH_MIN = 0.0F;
    public static final float PITCH_MAX = 90.0F;
    private static final int FIRE_DELAY = 20;
    private static final int SYNC_INTERVAL = 2;

    private float yawDeg;
    private float pitchDeg = 45.0F;
    private int caliberMm = 120;
    private ItemStack loaded = ItemStack.EMPTY;
    private int fireTicks;
    /* 迁移中: 清方块会触发 onRemove, 那时不能弹出炮弹也不能拆座位 */
    private boolean migrating;
    /* 角度与口径的同步节流, 驾驶时每 tick 变化一次, 不必每 tick 都发全量包 */
    private boolean syncPending;
    private int syncCooldown;
    @Nullable
    private UUID controller;
    @Nullable
    private UUID seat;

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(MortarRegistry.MORTAR_ENTITY.get(), pos, state);
    }

    public float yawDeg() {
        return this.yawDeg;
    }

    public float pitchDeg() {
        return this.pitchDeg;
    }

    public int caliberMm() {
        return this.caliberMm;
    }

    public ItemStack loaded() {
        return this.loaded;
    }

    public boolean loadedShell() {
        return !this.loaded.isEmpty();
    }

    @Nullable
    public UUID controller() {
        return this.controller;
    }

    public void setYaw(float value) {
        this.yawDeg = ((value % 360.0F) + 360.0F) % 360.0F;
        this.markSynced();
    }

    public void setPitch(float value) {
        this.pitchDeg = Math.max(PITCH_MIN, Math.min(PITCH_MAX, value));
        this.markSynced();
    }

    public void setCaliber(int value) {
        this.caliberMm = Math.max(CALIBER_MIN, Math.min(CALIBER_MAX, value));
        this.markSynced();
    }

    /* 连续调节只记脏, 由 tick 合并成较少的同步包 */
    private void markSynced() {
        this.setChanged();
        this.syncPending = true;
    }

    public boolean migrating() {
        return this.migrating;
    }

    /* 口径线性映射到出射速度, 单位 格/秒 */
    public double muzzleSpeed() {
        double ratio = (this.caliberMm - CALIBER_MIN) / (double) (CALIBER_MAX - CALIBER_MIN);
        return 120.0D + ratio * 360.0D;
    }

    public boolean loadShell(ItemStack stack) {
        if (this.loadedShell() || stack.isEmpty()) {
            return false;
        }
        this.loaded = stack.copyWithCount(1);
        this.setChanged();
        this.sync();
        return true;
    }

    public ItemStack ejectShell() {
        ItemStack out = this.loaded;
        this.loaded = ItemStack.EMPTY;
        this.setChanged();
        this.sync();
        return out;
    }

    public void startFire() {
        if (!this.loadedShell() || this.fireTicks > 0) {
            return;
        }
        this.fireTicks = FIRE_DELAY;
        this.setChanged();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MortarBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            entity.fall(level, pos, state);
            return;
        }
        if (entity.fireTicks > 0) {
            entity.fireTicks--;
            if (entity.fireTicks == 0) {
                entity.fire((ServerLevel) level, pos);
            }
        }
        if (entity.syncPending && ++entity.syncCooldown >= SYNC_INTERVAL) {
            entity.syncPending = false;
            entity.syncCooldown = 0;
            entity.sync();
        }
    }

    /* 下方没有支撑时整体下落一格, 角度与装填保留 */
    private void fall(Level level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        if (below.getY() <= level.getMinBuildHeight()) {
            return;
        }
        /* 先把要带走的状态取到局部: 清方块会触发 onRemove, 把这一份的装填与控制者清掉 */
        float yaw = this.yawDeg;
        float pitch = this.pitchDeg;
        int caliber = this.caliberMm;
        ItemStack loaded = this.loaded;
        int fireTicks = this.fireTicks;
        UUID controller = this.controller;
        UUID seat = this.seat;
        this.migrating = true;
        try {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(below, state, Block.UPDATE_ALL);
        } finally {
            this.migrating = false;
        }
        if (level.getBlockEntity(below) instanceof MortarBlockEntity moved) {
            moved.yawDeg = yaw;
            moved.pitchDeg = pitch;
            moved.caliberMm = caliber;
            moved.loaded = loaded;
            moved.fireTicks = fireTicks;
            moved.controller = controller;
            moved.seat = seat;
            moved.setChanged();
            moved.sync();
        }
        /* 座位跟着下移一格, 驾驶者不会悬在旧位置 */
        if (seat != null && level instanceof ServerLevel server) {
            Entity seatEntity = server.getEntity(seat);
            if (seatEntity != null) {
                seatEntity.setPos(below.getX() + 0.5D, below.getY() + 0.2D, below.getZ() + 0.5D);
            }
        }
    }

    private void fire(ServerLevel level, BlockPos pos) {
        if (!this.loadedShell()) {
            return;
        }
        ItemStack stack = this.loaded;
        ShellSettings settings = stack.getItem() instanceof ShellItem shell ? shell.settingsOf(stack) : ShellSettings.DEFAULT;
        this.loaded = ItemStack.EMPTY;
        this.setChanged();
        this.sync();
        double yawRad = Math.toRadians(this.yawDeg);
        double pitchRad = Math.toRadians(this.pitchDeg);
        Vec3 direction = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 start = Vec3.atCenterOf(pos).add(0.0D, 1.1D, 0.0D).add(direction.scale(0.6D));
        double speed = this.muzzleSpeed() / 20.0D;
        int fuse = settings.impactFuse() ? -1 : settings.fuseSeconds() * 20;
        Entity owner = this.controller == null ? null : level.getPlayerByUUID(this.controller);
        MortarShellProjectile shell = new MortarShellProjectile(level, start, direction.scale(speed), settings,
                owner, fuse);
        level.addFreshEntity(shell);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 0.6F);
    }

    /* 进入驾驶: 生成隐形座位并让玩家坐上 */
    public boolean mount(Player player) {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (this.controller != null && !this.controller.equals(player.getUUID())) {
            return false;
        }
        if (this.seat != null) {
            Entity existing = level.getEntity(this.seat);
            if (existing != null) {
                existing.discard();
            }
        }
        ArmorStand seat = new ArmorStand(level, this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.2D, this.worldPosition.getZ() + 0.5D);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setCustomNameVisible(false);
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
        this.seat = seat.getUUID();
        this.controller = player.getUUID();
        this.setChanged();
        this.sync();
        return true;
    }

    public void dismount() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        if (this.seat != null) {
            Entity seatEntity = level.getEntity(this.seat);
            if (seatEntity != null) {
                for (Entity passenger : seatEntity.getPassengers()) {
                    passenger.stopRiding();
                }
                seatEntity.discard();
            }
        }
        this.seat = null;
        this.controller = null;
        this.setChanged();
        this.sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Yaw", this.yawDeg);
        tag.putFloat("Pitch", this.pitchDeg);
        tag.putInt("Caliber", this.caliberMm);
        tag.putInt("FireTicks", this.fireTicks);
        if (!this.loaded.isEmpty()) {
            tag.put("Loaded", this.loaded.save(new CompoundTag()));
        }
        if (this.controller != null) {
            tag.putUUID("Controller", this.controller);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.yawDeg = tag.getFloat("Yaw");
        this.pitchDeg = tag.contains("Pitch") ? tag.getFloat("Pitch") : 45.0F;
        this.caliberMm = tag.contains("Caliber") ? tag.getInt("Caliber") : 120;
        this.fireTicks = tag.getInt("FireTicks");
        this.loaded = tag.contains("Loaded") ? ItemStack.of(tag.getCompound("Loaded")) : ItemStack.EMPTY;
        this.controller = tag.hasUUID("Controller") ? tag.getUUID("Controller") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Yaw", this.yawDeg);
        tag.putFloat("Pitch", this.pitchDeg);
        tag.putInt("Caliber", this.caliberMm);
        tag.putBoolean("Loaded", this.loadedShell());
        tag.putInt("FireTicks", this.fireTicks);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
