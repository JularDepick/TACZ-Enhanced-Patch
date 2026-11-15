package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.shell.ShellTarget;
import com.taczenhancedpatch.shell.ShellSettings;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.faction.FactionData;
import com.taczenhancedpatch.faction.Factions;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.faction.RuinTerritory;
import com.taczenhancedpatch.grenade.GrenadeProjectile;
import com.taczenhancedpatch.item.PatchItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/*
 * 幻机。
 * 沿用原版幻翼的模型与飞行方式, 在遗迹领地的内圈与外圈交替绕圈巡逻;
 * 发现生存玩家或有阵营的敌对生物进入领地时发出警报, 并把目标通报给领地内的其他遗迹生物。
 * 攻击手段是投掷手雷: 对玩家只用只杀伤玩家的档位, 对生物只用只杀伤生物的档位, 因此不会误伤友军。
 */
public class PhantomElite extends Phantom {
    /* 内外巡逻圈相对领地半径的比例 */
    private static final double INNER_RATIO = 0.6D;
    private static final double OUTER_RATIO = 0.9D;
    /* 巡逻高度: 距地面的下限与上下摆动的幅度, 合计上限为十六格 */
    private static final double FLY_MIN_HEIGHT = 8.0D;
    private static final double FLY_HEIGHT_SPAN = 8.0D;
    private static final int SCAN_INTERVAL = 20;
    private static final int ALERT_COOLDOWN = 100;
    private static final double THROW_RANGE = 28.0D;
    private static final int MAX_FUSE_SECONDS = 5;
    private static final int DEFAULT_POWER = 4;
    private static final double PATROL_STEP = 0.02D;
    /* 原版投掷物的重力, 单位 格/tick 的平方 */
    private static final double THROW_GRAVITY = 0.03D;

    private double patrolAngle;
    private boolean outerRing = true;
    private int alertCooldown;
    private int throwCooldown;

    public PhantomElite(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    /* 原版幻翼的属性写在默认属性表里, 这里自行声明生命, 索敌范围与飞行速度 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D);
    }

    /* 遗迹守军不因和平难度消失, 否则遗迹在和平存档里会逐渐空掉 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /* 不使用原版幻翼的绕圈与俯冲目标: 巡逻与投掷都在 tick 里自己完成 */
    @Override
    protected void registerGoals() {
    }

    /* 遗迹生物不受日晒影响 */
    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.alertCooldown > 0) {
            this.alertCooldown--;
        }
        if (this.throwCooldown > 0) {
            this.throwCooldown--;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        /* 出生点可能在方块里: 每 tick 向上抬一点, 直到脱离碰撞 */
        if (!level.noCollision(this, this.getBoundingBox())) {
            this.setPos(this.getX(), this.getY() + 0.4D, this.getZ());
        }
        BlockPos home = FactionData.homeOf(this);
        if (home == null) {
            return;
        }
        RuinSites.Site site = RuinSites.find(level, home);
        if (site == null || site.banner() == null) {
            return;
        }
        this.patrol(level, site);
        if (this.tickCount % SCAN_INTERVAL == 0) {
            this.scan(level, site);
        }
        this.tryThrow(level, site);
    }

    /* 内外两圈交替: 绕满一圈换一圈, 高度取当前位置地面以上八到十六格之间往复 */
    private void patrol(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return;
        }
        this.patrolAngle += PATROL_STEP;
        if (this.patrolAngle > Math.PI * 2.0D) {
            this.patrolAngle -= Math.PI * 2.0D;
            this.outerRing = !this.outerRing;
        }
        double radius = site.radius() * (this.outerRing ? OUTER_RATIO : INNER_RATIO);
        double x = banner.getX() + 0.5D + Math.cos(this.patrolAngle) * radius;
        double z = banner.getZ() + 0.5D + Math.sin(this.patrolAngle) * radius;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int) Math.floor(x), banner.getY(), (int) Math.floor(z)));
        /* 离地高度在八到十六格之间按正弦往复 */
        double wave = 0.5D + 0.5D * Math.sin(this.patrolAngle * 2.0D);
        double y = ground.getY() + FLY_MIN_HEIGHT + FLY_HEIGHT_SPAN * wave;
        this.getMoveControl().setWantedPosition(x, y, z, 1.0D);
    }

    /* 报警: 发出警报声, 把目标通报给领地内其他遗迹生物, 自己也锁定该目标 */
    private void scan(ServerLevel level, RuinSites.Site site) {
        LivingEntity intruder = findIntruder(level, site);
        if (intruder == null) {
            return;
        }
        if (this.getTarget() == null || !this.getTarget().isAlive()) {
            this.setTarget(intruder);
        }
        if (this.alertCooldown > 0) {
            return;
        }
        this.alertCooldown = ALERT_COOLDOWN;
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.8F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F);
        for (Mob ally : RuinTerritory.mobs(level, site)) {
            if (ally == this || !ally.isAlive() || Factions.allied(ally, intruder)) {
                continue;
            }
            ally.setTarget(intruder);
            ally.setLastHurtByMob(intruder);
        }
        TACZEnhancedPatch.LOGGER.debug("[phantom] alert at {} target={}", site.origin(), intruder.getUUID());
    }

    /* 领地内的入侵者: 生存玩家, 或有阵营且与本遗迹敌对的生物 */
    @Nullable
    private LivingEntity findIntruder(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return null;
        }
        double radius = site.radius();
        AABB area = new AABB(banner).inflate(radius);
        String faction = FactionData.factionOf(this);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == this || !candidate.isAlive() || candidate.isSpectator()) {
                continue;
            }
            if (!RuinTerritory.inside(banner, radius, candidate.getX(), candidate.getY(), candidate.getZ())) {
                continue;
            }
            if (candidate instanceof Player player) {
                /* 遗迹机制的参与判定只接受服务端玩家, 因此先确认是 ServerPlayer */
                if (!(player instanceof ServerPlayer serverPlayer) || !RuinTerritory.participates(serverPlayer)) {
                    continue;
                }
            } else if (!Factions.hostile(faction, FactionData.factionOf(candidate))) {
                continue;
            }
            double distance = this.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /* 投掷: 对玩家用只杀伤玩家的档位, 对生物用只杀伤生物的档位, 反弹系数固定为零 */
    private void tryThrow(ServerLevel level, RuinSites.Site site) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.throwCooldown > 0) {
            return;
        }
        if (this.distanceTo(target) > THROW_RANGE || !this.hasLineOfSight(target)) {
            return;
        }
        this.throwCooldown = ServerConfig.phantomThrowDelay(this.getRandom()) * 20;
        boolean againstPlayer = target instanceof Player;
        ShellTarget mode = againstPlayer ? ShellTarget.PLAYER_ONLY : ShellTarget.MOB_ONLY;
        int fuse = 1 + this.getRandom().nextInt(MAX_FUSE_SECONDS);
        DyeColor color = DyeColor.byName(site.bannerColor(), DyeColor.GREEN);
        ShellSettings base = PhantomShells.take(this);
        if (base == null) {
            return;
        }
        /* 背包里存的是威力与引信, 杀伤对象与颜色按投掷时的目标与遗迹阵营决定 */
        ShellSettings settings = new ShellSettings(base.fuseSeconds(), base.sticky(), base.power(),
                mode, color, base.effects());
        int charge = ServerConfig.phantomCharge(this.getRandom());
        double[] range = ServerConfig.grenadeSpeedRange();
        Vec3 start = this.getEyePosition();
        Vec3 velocity = this.aimAt(start, target.getEyePosition(),
                ShellSettings.speedFor(charge, range[0], range[1]) / 20.0D);
        GrenadeProjectile grenade = new GrenadeProjectile(level, start, velocity, settings, this, fuse * 20);
        level.addFreshEntity(grenade);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8F, 0.8F);
    }

    /*
     * 抛物线解算: 已知初速度与重力, 解出仰角再合成方向向量;
     * 无解时直接朝目标投出, 观感上相当于贴脸直掷。
     */
    private Vec3 aimAt(Vec3 start, Vec3 end, double speed) {
        /* 与原版投掷物一致的重力, 单位 格/tick 的平方 */
        double gravity = THROW_GRAVITY;
        Vec3 delta = end.subtract(start);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.1D) {
            return delta.normalize().scale(speed);
        }
        double speedSqr = speed * speed;
        double root = speedSqr * speedSqr - gravity * (gravity * horizontal * horizontal + 2.0D * delta.y * speedSqr);
        if (root < 0.0D) {
            return delta.normalize().scale(speed);
        }
        double tangent = (speedSqr - Math.sqrt(root)) / (gravity * horizontal);
        double angle = Math.atan(tangent);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(delta.x / horizontal * cos * speed, sin * speed, delta.z / horizontal * cos * speed);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
        if (!level.getLevel().isClientSide()) {
            this.setPhantomSize(1);
            this.setHealth(this.getMaxHealth());
        }
        return data;
    }

    /* 击杀掉落一到两个手雷, 数量随抢夺等级略微增加 */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        int count = 1 + this.getRandom().nextInt(2 + Math.max(0, looting));
        for (int i = 0; i < count; i++) {
            this.spawnAtLocation(new ItemStack(PatchItems.GRENADE.get()));
        }
    }
}
