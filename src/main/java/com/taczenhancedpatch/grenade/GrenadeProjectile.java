package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/*
 * 手雷投掷物。
 * 速度以 格/秒 记, 重力以 格/秒平方 记, 每 tick 按二十分之一秒积分; 位移分成小步推进, 避免高速穿墙。
 * 碰撞后法向速度乘反弹系数并反向, 切向速度每次固定损失一成; 法向速度低于阈值即整体静止。
 * 引信以 tick 计: 触发式碰到方块或生物立即爆炸, 延时式到点爆炸。
 */
public class GrenadeProjectile extends Entity {
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double SUBSTEP_LENGTH = 0.2D;
    private static final int MAX_SUBSTEPS = 8;

    private GrenadeSettings settings = GrenadeSettings.DEFAULT;
    @Nullable
    private UUID ownerId;
    private int fuseTicks = -1;
    private boolean resting;
    private boolean detonated;

    public GrenadeProjectile(EntityType<? extends GrenadeProjectile> type, Level level) {
        super(type, level);
        /* 重力由本类自己积分, 不走原版的重力字段 */
        this.setNoGravity(true);
    }

    public GrenadeProjectile(Level level, Vec3 position, Vec3 velocity, GrenadeSettings settings,
                             @Nullable Entity owner, int fuseTicks) {
        this(GrenadeRegistry.GRENADE.get(), level);
        this.setPos(position.x, position.y, position.z);
        this.setDeltaMovement(velocity);
        this.settings = GrenadeSettings.sanitize(settings);
        this.ownerId = owner == null ? null : owner.getUUID();
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
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTick();
            return;
        }
        if (this.fuseTicks >= 0 && this.tickCount >= this.fuseTicks) {
            this.detonate();
            return;
        }
        if (this.resting) {
            return;
        }
        if (this.hitEntity()) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement().add(0.0D, -ServerConfig.grenadeGravity() * TICK_SECONDS, 0.0D);
        double distance = velocity.length() * TICK_SECONDS;
        int steps = Mth.clamp((int) Math.ceil(distance / SUBSTEP_LENGTH), 1, MAX_SUBSTEPS);
        Vec3 step = velocity.scale(TICK_SECONDS / steps);
        for (int i = 0; i < steps; i++) {
            if (this.moveStep(step)) {
                return;
            }
        }
        this.setDeltaMovement(velocity);
    }

    /* 命中生物: 触发式立即爆炸, 延时引信直接穿过 */
    private boolean hitEntity() {
        List<Entity> hits = this.level().getEntities(this, this.getBoundingBox().inflate(0.2D),
                entity -> entity instanceof LivingEntity && entity.isAlive()
                        && !entity.getUUID().equals(this.ownerId));
        if (hits.isEmpty() || !this.settings.impactFuse()) {
            return false;
        }
        this.detonate();
        return true;
    }

    /* 逐轴推进: 某一轴被挡住就记为一次碰撞, 三轴都通过才算这一步走完 */
    private boolean moveStep(Vec3 step) {
        Vec3 position = this.position();
        boolean hitX = false;
        boolean hitY = false;
        boolean hitZ = false;
        if (step.x != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(step.x, 0.0D, 0.0D))) {
                position = position.add(step.x, 0.0D, 0.0D);
            } else {
                hitX = true;
            }
            this.setPos(position.x, position.y, position.z);
        }
        if (step.y != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(0.0D, step.y, 0.0D))) {
                position = position.add(0.0D, step.y, 0.0D);
            } else {
                hitY = true;
            }
            this.setPos(position.x, position.y, position.z);
        }
        if (step.z != 0.0D) {
            if (this.level().noCollision(this, this.getBoundingBox().move(0.0D, 0.0D, step.z))) {
                position = position.add(0.0D, 0.0D, step.z);
            } else {
                hitZ = true;
            }
            this.setPos(position.x, position.y, position.z);
        }
        if (!hitX && !hitY && !hitZ) {
            return false;
        }
        if (this.settings.impactFuse()) {
            this.detonate();
            return true;
        }
        this.bounce(hitX, hitY, hitZ);
        return false;
    }

    /*
     * 反弹: 法向分量乘反弹系数并反向, 切向分量固定损失一成;
     * 系数为零或法向速度衰减到阈值以下时整体静止, 不再参与位移与碰撞。
     */
    private void bounce(boolean hitX, boolean hitY, boolean hitZ) {
        Vec3 velocity = this.getDeltaMovement();
        double coefficient = this.settings.bounce();
        double x = velocity.x;
        double y = velocity.y;
        double z = velocity.z;
        double normal;
        if (hitY) {
            normal = Math.abs(y);
            y = -y * coefficient;
            x *= 1.0D - GrenadeSettings.TANGENT_LOSS;
            z *= 1.0D - GrenadeSettings.TANGENT_LOSS;
        } else if (hitX) {
            normal = Math.abs(x);
            x = -x * coefficient;
            y *= 1.0D - GrenadeSettings.TANGENT_LOSS;
            z *= 1.0D - GrenadeSettings.TANGENT_LOSS;
        } else {
            normal = Math.abs(z);
            z = -z * coefficient;
            x *= 1.0D - GrenadeSettings.TANGENT_LOSS;
            y *= 1.0D - GrenadeSettings.TANGENT_LOSS;
        }
        Vec3 next = new Vec3(x, y, z);
        if (coefficient <= 0.0D || normal * coefficient < GrenadeSettings.REST_SPEED
                || next.length() < GrenadeSettings.REST_SPEED) {
            this.setDeltaMovement(Vec3.ZERO);
            this.resting = true;
        } else {
            this.setDeltaMovement(next);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.35F, 1.2F);
    }

    private void detonate() {
        if (this.detonated) {
            return;
        }
        this.detonated = true;
        if (this.level() instanceof ServerLevel serverLevel) {
            GrenadeExplosion.detonate(serverLevel, this.position(), this.settings, this.owner());
        }
        this.discard();
    }

    private void clientTick() {
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1D, this.getZ(),
                    0.0D, 0.01D, 0.0D);
        }
    }

    @Nullable
    public Entity owner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(this.ownerId);
    }

    public GrenadeSettings settings() {
        return this.settings;
    }

    public boolean resting() {
        return this.resting;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuse", this.settings.fuseSeconds());
        tag.putDouble("Bounce", this.settings.bounce());
        tag.putInt("Power", this.settings.power());
        tag.putString("Target", this.settings.target().id());
        tag.putString("Color", this.settings.color().getName());
        tag.putBoolean("Effects", this.settings.effects());
        tag.putInt("FuseTicks", this.fuseTicks);
        tag.putBoolean("Resting", this.resting);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.settings = GrenadeSettings.sanitize(new GrenadeSettings(
                tag.getInt("Fuse"),
                tag.getDouble("Bounce"),
                tag.getInt("Power"),
                GrenadeTarget.byId(tag.getString("Target"), GrenadeTarget.NO_MOB),
                DyeColor.byName(tag.getString("Color"), DyeColor.GREEN),
                !tag.contains("Effects") || tag.getBoolean("Effects")));
        this.fuseTicks = tag.getInt("FuseTicks");
        this.resting = tag.getBoolean("Resting");
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
