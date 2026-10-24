package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import com.taczenhancedpatch.faction.FactionData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
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
                .add(Attributes.MAX_HEALTH, CombatPreset.tankHealth(net.minecraft.world.Difficulty.NORMAL))
                .add(Attributes.ARMOR, CombatPreset.tankArmor(net.minecraft.world.Difficulty.NORMAL))
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
    }

    /* 属性按难度预设, 不读取模组配置 */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
        if (!level.getLevel().isClientSide()) {
            var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(CombatPreset.tankHealth(difficulty.getDifficulty()));
                this.setHealth(this.getMaxHealth());
            }
            var armor = this.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.setBaseValue(CombatPreset.tankArmor(difficulty.getDifficulty()));
            }
        }
        return data;
    }

    /* 最多四名小兵骑在肩部; 玩家只能驾驶无阵营且空载的坦克, 且此时不再接受小兵 */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (passenger instanceof Player) {
            return FactionData.factionOf(this).isEmpty() && this.getPassengers().isEmpty();
        }
        if (!(passenger instanceof GunnerGrunt)) {
            return false;
        }
        if (this.hasExactlyOnePlayerPassenger()) {
            return false;
        }
        return this.getPassengers().size() < 4;
    }

    @Override
    public void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        if (passenger instanceof Player) {
            move.accept(passenger, 0.0D, 2.05D, -0.15D);
            return;
        }
        int index = Math.floorMod(this.getPassengers().indexOf(passenger), 4);
        double[][] offsets = {
                {-0.95D, 1.55D, -0.55D},
                {0.95D, 1.55D, -0.55D},
                {-0.95D, 1.55D, 0.65D},
                {0.95D, 1.55D, 0.65D}
        };
        double[] offset = offsets[index];
        double yaw = Math.toRadians(this.getYRot());
        double x = offset[0] * Math.cos(yaw) - offset[2] * Math.sin(yaw);
        double z = offset[0] * Math.sin(yaw) + offset[2] * Math.cos(yaw);
        move.accept(passenger, x, offset[1], z);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide()) {
            adoptFaction(passenger);
            TankCrew.refresh(this);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide()) {
            TankCrew.refresh(this);
        }
    }

    /* 有阵营的小兵骑上无阵营坦克后, 坦克归入该小兵的阵营 */
    private void adoptFaction(Entity passenger) {
        if (!(passenger instanceof GunnerGrunt)) {
            return;
        }
        if (!FactionData.factionOf(this).isEmpty() || FactionData.factionOf(passenger).isEmpty()) {
            return;
        }
        FactionData.setFaction(this, FactionData.factionOf(passenger));
        FactionData.setHome(this, FactionData.homeOf(passenger));
    }

    /* 无阵营坦克允许玩家骑乘 */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (FactionData.factionOf(this).isEmpty() && this.getPassengers().isEmpty()
                && !player.isShiftKeyDown() && !this.level().isClientSide()) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        if (FactionData.factionOf(this).isEmpty()) {
            for (Entity passenger : this.getPassengers()) {
                if (passenger instanceof Player player) {
                    return player;
                }
            }
        }
        return super.getControllingPassenger();
    }

    /* 玩家驾驶时由玩家输入驱动移动与瞄准, AI 不再接管寻路 */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.getControllingPassenger() instanceof Player player) {
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setYHeadRot(player.getYRot());
            this.yHeadRotO = this.getYHeadRot();
            this.setXRot(Mth.clamp(player.getXRot(), -20.0F, 12.0F));
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.2F);
            super.travel(new Vec3(player.xxa, 0.0D, player.zza));
            return;
        }
        super.travel(travelVector);
    }

    @Override
    protected void customServerAiStep() {
        if (this.getControllingPassenger() instanceof Player) {
            this.getNavigation().stop();
            return;
        }
        super.customServerAiStep();
    }

    /* 玩家驾驶时按准星方向开炮, 冷却与威力与其他坦克一致 */
    public void tryFire(Player player) {
        if (this.level().isClientSide() || this.cooldown > 0) {
            return;
        }
        Vec3 look = player.getViewVector(1.0F);
        Vec3 muzzle = this.position().add(0.0D, 2.1D, 0.0D).add(look.scale(2.2D));
        LargeFireball shell = new LargeFireball(this.level(), this, look.x * SHELL_SPEED, look.y * SHELL_SPEED,
                look.z * SHELL_SPEED, CombatPreset.tankExplosionPower(this.level().getDifficulty()));
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        this.level().addFreshEntity(shell);
        this.playSound(SoundEvents.GHAST_SHOOT, 4.0F, 0.5F);
        this.resetCooldown(ServerConfig.tankCannonCooldown());
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
                CombatPreset.tankExplosionPower(level().getDifficulty()));
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