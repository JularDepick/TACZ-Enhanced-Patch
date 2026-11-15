package com.taczenhancedpatch.grenade;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/*
 * 手雷投掷物。
 * 运动完全交给原版投掷物逻辑: 重力取原版实体的重力值, 空气阻力与碰撞判定都由游戏负责,
 * 本类只接管三件事: 引信计时, 命中后的粘附或反弹, 以及到点爆炸。
 * 速度沿用原版单位 格/tick, 与配置里的 格/秒 之间按二十换算。
 */
public class GrenadeProjectile extends ThrowableProjectile {
    /* 反弹后切向速度保留比例, 与每次固定损失一成一致 */
    private static final double TANGENT_KEEP = 0.9D;
    /* 静止阈值换算到 格/tick */
    private static final double REST_SPEED_TICKS = GrenadeSettings.REST_SPEED / 20.0D;
    /* 碰撞后沿法线推出的距离, 避免同一面反复判定 */
    private static final double SURFACE_OFFSET = 0.02D;

    private GrenadeSettings settings = GrenadeSettings.DEFAULT;
    private int fuseTicks = -1;
    private boolean resting;
    private boolean detonated;
    @Nullable
    private UUID attachedEntityId;
    private Vec3 attachOffset = Vec3.ZERO;

    public GrenadeProjectile(EntityType<? extends GrenadeProjectile> type, Level level) {
        super(type, level);
    }

    public GrenadeProjectile(Level level, Vec3 position, Vec3 velocity, GrenadeSettings settings,
                             @Nullable Entity owner, int fuseTicks) {
        this(GrenadeRegistry.GRENADE.get(), level);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.settings = GrenadeSettings.sanitize(settings);
        this.setOwner(owner);
        this.fuseTicks = fuseTicks;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        if (this.detonated) {
            return;
        }
        if (this.level().isClientSide()) {
            super.tick();
            this.clientTick();
            return;
        }
        if (this.fuseTicks >= 0 && this.tickCount >= this.fuseTicks) {
            this.detonate();
            return;
        }
        if (this.attachedEntityId != null) {
            /* 粘在生物或玩家身上时不再走原版运动, 只跟着对方移动 */
            this.tickCount++;
            this.followAttached();
            return;
        }
        if (this.resting) {
            this.tickCount++;
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (this.detonated || this.level().isClientSide()) {
            return;
        }
        if (this.settings.sticky()) {
            this.stickToSurface(hit);
            return;
        }
        if (this.settings.impactFuse()) {
            this.detonate();
            return;
        }
        this.bounce(hit);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (this.detonated || this.level().isClientSide()) {
            return;
        }
        if (this.settings.sticky()) {
            this.attachTo(hit.getEntity());
            return;
        }
        if (this.settings.impactFuse()) {
            this.detonate();
        }
    }

    /* 不撞投掷者自己 */
    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !target.equals(this.getOwner());
    }

    private void followAttached() {
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

    private void attachTo(Entity target) {
        this.attachedEntityId = target.getUUID();
        this.attachOffset = this.position().subtract(target.position());
        this.setDeltaMovement(Vec3.ZERO);
        this.resting = true;
    }

    private void stickToSurface(BlockHitResult hit) {
        this.setDeltaMovement(Vec3.ZERO);
        this.resting = true;
        this.setPos(this.position().add(normalOf(hit).scale(SURFACE_OFFSET)));
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_BLOCK_STEP, SoundSource.NEUTRAL, 0.5F, 1.4F);
    }

    /*
     * 自适应反弹: 系数由碰撞方块硬度决定, 取 (0,1], 越硬反弹越弱;
     * 法向速度乘系数并反向, 切向速度固定损失一成, 法向过低即整体静止。
     */
    private void bounce(BlockHitResult hit) {
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
        } else {
            this.setDeltaMovement(next);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.35F, 1.2F);
    }

    private static Vec3 normalOf(BlockHitResult hit) {
        Direction direction = hit.getDirection();
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    /* 方块越硬反弹越弱: 硬度零给满系数, 硬度越大越接近零 */
    private double bounceCoefficient(BlockHitResult hit) {
        BlockState state = this.level().getBlockState(hit.getBlockPos());
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness < 0.0F) {
            hardness = 50.0F;
        }
        return Mth.clamp(1.0D / (1.0D + hardness), 0.02D, 1.0D);
    }

    private void detonate() {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        if (this.level() instanceof ServerLevel serverLevel) {
            GrenadeExplosion.detonate(serverLevel, this.position(), this.settings, this.getOwner());
        }
        this.discard();
    }

    private void clientTick() {
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1D, this.getZ(),
                    0.0D, 0.01D, 0.0D);
        }
    }

    public GrenadeSettings settings() {
        return this.settings;
    }

    public boolean resting() {
        return this.resting;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", this.settings.fuseSeconds());
        tag.putBoolean("Sticky", this.settings.sticky());
        tag.putInt("Power", this.settings.power());
        tag.putString("Target", this.settings.target().id());
        tag.putString("Color", this.settings.color().getName());
        tag.putBoolean("Effects", this.settings.effects());
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
        this.settings = GrenadeSettings.sanitize(new GrenadeSettings(
                tag.getInt("Fuse"),
                tag.getBoolean("Sticky"),
                tag.getInt("Power"),
                GrenadeTarget.byId(tag.getString("Target"), GrenadeTarget.NO_MOB),
                DyeColor.byName(tag.getString("Color"), DyeColor.GREEN),
                !tag.contains("Effects") || tag.getBoolean("Effects")));
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
