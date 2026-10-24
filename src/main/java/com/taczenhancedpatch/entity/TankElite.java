package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 坦克精英怪。
 * 远程炮击型精英怪, 行为接近恶魂: 发现目标后保持距离并周期性发射炮弹,
 * 但炮弹威力更大, 破坏地形更明显, 冷却也更长。体型明显大于普通生物以形成威压感。
 * 不做近战, 也不参与枪手系统, 死亡后按战利品表发放金属材料与火药。
 */
public class TankElite extends Monster {
    /* 炮塔与炮管的瞄准速度, 单位角度每 tick */
    private static final float AIM_SPEED = 20.0F;
    /* 炮弹出膛后的初速, 比恶魂火球更慢, 给玩家留出躲避窗口 */
    private static final double SHELL_SPEED = 0.55D;

    private int cooldown;

    public TankElite(EntityType<? extends TankElite> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new TankCannonGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.cooldown > 0) {
            this.cooldown--;
        }
    }

    /* 朝目标发射一发炮弹, 只在服务端执行 */
    void fireShell(LivingEntity target) {
        if (level().isClientSide()) {
            return;
        }
        Vec3 muzzle = position().add(0.0D, 2.1D, 0.0D).add(getLookAngle().scale(2.2D));
        Vec3 direction = target.getEyePosition().subtract(muzzle).normalize().scale(SHELL_SPEED);
        LargeFireball shell = new LargeFireball(level(), this, direction.x, direction.y, direction.z,
                ServerConfig.tankExplosionPower());
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        level().addFreshEntity(shell);
        playSound(SoundEvents.GHAST_SHOOT, 4.0F, 0.5F);
    }

    int cooldown() {
        return this.cooldown;
    }

    void resetCooldown(int ticks) {
        this.cooldown = Math.max(1, ticks);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    /**
     * 炮击行为。
     * 目标进入开炮距离后停下移动并转向目标, 冷却结束时发射一发炮弹;
     * 目标离开距离或失效时释放控制权, 交回普通游走行为。
     */
    private static final class TankCannonGoal extends Goal {
        private final TankElite tank;

        private TankCannonGoal(TankElite tank) {
            this.tank = tank;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.tank.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double range = ServerConfig.tankCannonRange();
            return this.tank.distanceToSqr(target) <= range * range;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = this.tank.getTarget();
            if (target == null) {
                return;
            }
            this.tank.getLookControl().setLookAt(target, AIM_SPEED, AIM_SPEED);
            this.tank.getNavigation().stop();
            if (this.tank.cooldown() > 0) {
                return;
            }
            this.tank.resetCooldown(ServerConfig.tankCannonCooldown());
            this.tank.fireShell(target);
        }
    }
}
