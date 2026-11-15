package com.taczenhancedpatch.shell;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/*
 * 炮弹投射物基类。
 * 负责两种炮弹共用的部分: 属性快照, 引信计时, 粘性附着, 按方块硬度自适应反弹, 命中判定与爆炸派发。
 * 运动本身由派生类决定: 手雷走原版投掷物, 迫击炮炮弹按自己的重力积分。
 */
public abstract class ShellProjectile extends ThrowableProjectile {
    protected static final double TANGENT_KEEP = 0.9D;
    protected static final double REST_SPEED_TICKS = ShellSettings.REST_SPEED / 20.0D;
    protected static final double SURFACE_OFFSET = 0.12D;

    protected ShellSettings settings = ShellSettings.DEFAULT;
    protected int fuseTicks = -1;
    protected boolean resting;
    protected boolean detonated;
    @Nullable
    protected UUID attachedEntityId;
    protected Vec3 attachOffset = Vec3.ZERO;
    /* 属性打包下发: 客户端渲染与面板都要读同一份, 只靠 NBT 客户端拿不到 */
    private static final EntityDataAccessor<Integer> DATA_SHELL =
            SynchedEntityData.defineId(ShellProjectile.class, EntityDataSerializers.INT);

    protected ShellProjectile(EntityType<? extends ShellProjectile> type, Level level) {
        super(type, level);
    }

    protected ShellProjectile(EntityType<? extends ShellProjectile> type, Level level, Vec3 position, Vec3 velocity,
                              ShellSettings settings, @Nullable Entity owner, int fuseTicks) {
        this(type, level);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.settings = ShellSettings.sanitize(settings);
        this.setOwner(owner);
        this.fuseTicks = fuseTicks;
        this.pushSettings();
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_SHELL, pack(ShellSettings.DEFAULT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SHELL.equals(key)) {
            this.settings = unpack(this.getEntityData().get(DATA_SHELL));
        }
    }

    /* 属性打包成整数: 引信 4 位, 威力 5 位, 杀伤档 3 位, 配色 5 位, 粘性与特效各 1 位 */
    private static int pack(ShellSettings settings) {
        int fuse = Mth.clamp(settings.fuseSeconds() + 1, 0, 15);
        int power = Mth.clamp(settings.power(), ShellSettings.POWER_MIN, ShellSettings.POWER_MAX);
        int target = settings.target() == null ? 0 : settings.target().ordinal();
        int color = settings.color() == null ? 0 : settings.color().ordinal();
        return fuse | (power << 4) | (target << 9) | (color << 12)
                | ((settings.sticky() ? 1 : 0) << 17) | ((settings.effects() ? 1 : 0) << 18);
    }

    private static ShellSettings unpack(int value) {
        ShellTarget[] targets = ShellTarget.values();
        ShellColor[] colors = ShellColor.values();
        return ShellSettings.sanitize(new ShellSettings(
                (value & 0xF) - 1,
                ((value >> 17) & 1) != 0,
                (value >> 4) & 0x1F,
                targets[((value >> 9) & 0x7) % targets.length],
                colors[((value >> 12) & 0x1F) % colors.length],
                ((value >> 18) & 1) != 0));
    }

    /* 服务端改动属性后调用, 让客户端拿到最新一份 */
    protected void pushSettings() {
        if (!this.level().isClientSide()) {
            this.getEntityData().set(DATA_SHELL, pack(this.settings));
        }
    }

    /* 共用状态推进; 返回真表示本 tick 不需要再走运动 */
    protected boolean sharedTick() {
        if (this.detonated) {
            return true;
        }
        if (this.level().isClientSide()) {
            return false;
        }
        if (this.fuseTicks >= 0 && this.tickCount >= this.fuseTicks) {
            this.detonate();
            return true;
        }
        if (this.attachedEntityId != null) {
            this.tickCount++;
            this.followAttached();
            return true;
        }
        if (this.resting) {
            this.tickCount++;
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (sharedTick()) {
            return;
        }
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTick();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (this.detonated || this.level().isClientSide()) {
            return;
        }
        if (this.settings.impactFuse()) {
            this.detonate();
            return;
        }
        if (this.settings.sticky()) {
            this.stickToSurface(hit);
            return;
        }
        this.bounce(hit);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (this.detonated || this.level().isClientSide()) {
            return;
        }
        if (this.settings.impactFuse()) {
            this.detonate();
            return;
        }
        if (this.settings.sticky()) {
            this.attachTo(hit.getEntity());
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !target.equals(this.getOwner());
    }

    protected void followAttached() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity target = serverLevel.getEntity(this.attachedEntityId);
        if (target == null || !target.isAlive()) {
            this.attachedEntityId = null;
            return;
        }
        Vec3 next = target.position().add(this.attachOffset);
        this.setPos(next.x, next.y, next.z);
    }

    protected void attachTo(Entity target) {
        this.attachedEntityId = target.getUUID();
        this.attachOffset = this.position().subtract(target.position());
        this.setDeltaMovement(Vec3.ZERO);
        this.resting = true;
    }

    protected void stickToSurface(BlockHitResult hit) {
        this.setDeltaMovement(Vec3.ZERO);
        this.resting = true;
        this.setPos(this.position().add(normalOf(hit).scale(SURFACE_OFFSET)));
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_BLOCK_STEP, SoundSource.NEUTRAL, 0.5F, 1.4F);
    }

    protected void bounce(BlockHitResult hit) {
        Vec3 normal = normalOf(hit);
        double coefficient = bounceCoefficient(hit);
        Vec3 velocity = this.getDeltaMovement();
        double normalSpeed = velocity.dot(normal);
        Vec3 tangent = velocity.subtract(normal.scale(normalSpeed));
        Vec3 next = tangent.scale(TANGENT_KEEP).subtract(normal.scale(normalSpeed * coefficient));
        this.setPos(this.position().add(normal.scale(SURFACE_OFFSET)));
        if (Math.abs(normalSpeed * coefficient) < REST_SPEED_TICKS || next.length() < REST_SPEED_TICKS) {
            this.setDeltaMovement(Vec3.ZERO);
            this.resting = true;
            /* 触发式引信只要停下来就当作撞上, 避免永远不炸 */
            if (this.settings.impactFuse()) {
                this.detonate();
                return;
            }
        } else {
            this.setDeltaMovement(next);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.35F, 1.2F);
    }

    protected static Vec3 normalOf(BlockHitResult hit) {
        Direction direction = hit.getDirection();
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    protected double bounceCoefficient(BlockHitResult hit) {
        BlockState state = this.level().getBlockState(hit.getBlockPos());
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness < 0.0F) {
            hardness = 50.0F;
        }
        return Mth.clamp(1.0D / (1.0D + hardness), 0.02D, 1.0D);
    }

    protected void detonate() {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        if (this.level() instanceof ServerLevel serverLevel) {
            this.explode(serverLevel);
        }
        this.discard();
    }

    protected abstract void explode(ServerLevel level);

    protected void clientTick() {
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1D, this.getZ(),
                    0.0D, 0.01D, 0.0D);
        }
    }

    public ShellSettings settings() {
        return this.settings;
    }

    public boolean resting() {
        return this.resting;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Shell", this.settings.toTag());
        tag.putInt("FuseTicks", this.fuseTicks);
        tag.putBoolean("Resting", this.resting);
        if (this.attachedEntityId != null) {
            tag.putUUID("Attached", this.attachedEntityId);
            tag.putDouble("OffsetX", this.attachOffset.x);
            tag.putDouble("OffsetY", this.attachOffset.y);
            tag.putDouble("OffsetZ", this.attachOffset.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Shell")) {
            this.settings = ShellSettings.fromTag(tag.getCompound("Shell"));
        }
        this.pushSettings();
        this.fuseTicks = tag.getInt("FuseTicks");
        this.resting = tag.getBoolean("Resting");
        this.attachedEntityId = tag.hasUUID("Attached") ? tag.getUUID("Attached") : null;
        this.attachOffset = new Vec3(tag.getDouble("OffsetX"), tag.getDouble("OffsetY"), tag.getDouble("OffsetZ"));
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
