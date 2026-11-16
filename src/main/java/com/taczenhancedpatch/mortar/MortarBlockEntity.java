package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
    /* 反馈文案前缀 */
    private static final String KEY = "tacz_enhanced_patch.mortar";
    public static final int CALIBER_MIN = 60;
    public static final int CALIBER_MAX = 240;
    public static final float PITCH_MIN = 0.0F;
    public static final float PITCH_MAX = 90.0F;
    private static final int FIRE_DELAY = 20;
    private static final int SYNC_INTERVAL = 2;
    /* 出射速度区间, 单位 格/秒 */
    private static final double MUZZLE_SPEED_MIN = 60.0D;
    private static final double MUZZLE_SPEED_MAX = 240.0D;

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
    /* 最近一次按下发射的玩家, 用于把开炮反馈发回给他 */
    @Nullable
    private UUID operator;

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

    /* 口径线性映射到出射速度, 单位 格/秒; 六十到二百四十毫米对应每秒六十到二百四十格 */
    public double muzzleSpeed() {
        double ratio = (this.caliberMm - CALIBER_MIN) / (double) (CALIBER_MAX - CALIBER_MIN);
        return MUZZLE_SPEED_MIN + ratio * (MUZZLE_SPEED_MAX - MUZZLE_SPEED_MIN);
    }

    /* 调校状态随方块物品走: 挖下来再放下时接着用上一次的水平角, 仰角与口径 */
    public CompoundTag writeState() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Yaw", this.yawDeg);
        tag.putFloat("Pitch", this.pitchDeg);
        tag.putInt("Caliber", this.caliberMm);
        return tag;
    }

    public void readState(@Nullable CompoundTag tag) {
        if (tag == null) {
            return;
        }
        if (tag.contains("Yaw")) {
            this.setYaw(tag.getFloat("Yaw"));
        }
        if (tag.contains("Pitch")) {
            this.setPitch(tag.getFloat("Pitch"));
        }
        if (tag.contains("Caliber")) {
            this.setCaliber(tag.getInt("Caliber"));
        }
        this.setChanged();
        this.sync();
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
        this.startFire(null);
    }

    /* 谁按下发射就反馈给谁: 驾驶时是驾驶者, 遥控时是遥控的那位 */
    public void startFire(@Nullable Player source) {
        if (!this.loadedShell() || this.fireTicks > 0) {
            return;
        }
        this.fireTicks = FIRE_DELAY;
        this.setChanged();
        Player listener = source != null ? source : this.controllerPlayer();
        this.operator = listener == null ? null : listener.getUUID();
        /* 倒计时反馈: 玩家按下发射后立刻知道炮弹已经进入引信流程 */
        if (listener != null) {
            listener.displayClientMessage(Component.translatable(KEY + ".msg.firing"), true);
        }
        this.level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    @Nullable
    private Player operatorPlayer() {
        if (this.operator == null || !(this.level instanceof ServerLevel server)) {
            return null;
        }
        return server.getPlayerByUUID(this.operator);
    }

    @Nullable
    private Player controllerPlayer() {
        if (this.controller == null || !(this.level instanceof ServerLevel server)) {
            return null;
        }
        return server.getPlayerByUUID(this.controller);
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

    /*
     * 下方没有支撑时整体下落, 走原版落沙实体的路子, 与铁砧一致:
     * 逐 tick 受重力下落并带伤害, 落地后重新变成方块, 方块数据随实体一起带走。
     */
    private void fall(Level level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        if (below.getY() <= level.getMinBuildHeight()) {
            return;
        }
        /* 先把方块数据拍一份: 清方块会触发 onRemove, 把这一份的装填与控制者清掉 */
        CompoundTag saved = this.saveWithoutMetadata();
        this.migrating = true;
        try {
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
            if (falling != null) {
                CompoundTag tag = new CompoundTag();
                falling.saveWithoutId(tag);
                tag.put("TileEntityData", saved);
                falling.load(tag);
                /* 与铁砧同口径: 砸到生物按落差造成伤害 */
                falling.setHurtsEntities(2.0F, 40);
            }
        } finally {
            this.migrating = false;
        }
        /* 座位没法跟着飞: 先把驾驶者放下, 落地后再重新上炮 */
        this.dismount();
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
        /* 发射反馈: 爆炸声加推进声, 远处也能听出这一炮打出去了 */
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.55F);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 0.7F);
        Player listener = this.operatorPlayer();
        if (listener != null) {
            listener.displayClientMessage(Component.translatable(KEY + ".msg.fired"), true);
        }
        this.operator = null;
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
