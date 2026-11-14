package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.TankElite;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * 阵营战斗规则。
 * 同阵营或合作阵营之间不互相伤害; 成员被攻击时立刻还击, 并让附近同阵营成员一起锁定攻击者;
 * 有阵营且记住遗迹中心的生物会主动攻击进入领地范围的其他生物, 玩家靠近同样会被攻击。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class FactionCombat {
    private static final double ALLY_ALERT_RADIUS = 48.0D;
    private static final double TERRITORY_RADIUS = 28.0D;
    private static final double TERRITORY_HEIGHT = 12.0D;
    private static final int TARGET_SCAN_INTERVAL = 20;

    private FactionCombat() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        /* 同乘一个组合体的成员之间不误伤 */
        if (victim.isPassengerOfSameVehicle(attacker)) {
            event.setCanceled(true);
            return;
        }
        /* 同阵营与合作阵营之间不误伤 */
        if (Factions.allied(victim, attacker)) {
            event.setCanceled(true);
            return;
        }
        if (victim.level().isClientSide()) {
            return;
        }
        /* 创造与旁观玩家不引发敌意: 既不还击, 也不通知同阵营 */
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        /* 被攻击者立刻还击 */
        if (victim instanceof Mob mob && acceptable(mob, attacker)) {
            mob.setTarget(attacker);
            mob.setLastHurtByMob(attacker);
        }
        /* 同阵营共享敌对状态, 附近的同阵营成员一起锁定攻击者 */
        String faction = FactionData.factionOf(victim);
        if (faction.isEmpty()) {
            return;
        }
        for (Mob ally : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(ALLY_ALERT_RADIUS))) {
            if (ally == victim || !Factions.same(faction, FactionData.factionOf(ally))) {
                continue;
            }
            if (!acceptable(ally, attacker)) {
                continue;
            }
            ally.setTarget(attacker);
            ally.setLastHurtByMob(attacker);
        }
    }

    /* 有阵营的坦克被击杀后复活为无阵营坦克, 递归深度上限三次 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TankElite tank) || tank.level().isClientSide()) {
            return;
        }
        String faction = FactionData.factionOf(tank);
        int depth = FactionData.reviveDepth(tank);
        if (faction.isEmpty() || depth >= 3 || !(tank.level() instanceof ServerLevel level)) {
            return;
        }
        TankElite reborn = TankRegistry.TANK_ELITE.get().create(level);
        if (reborn == null) {
            return;
        }
        reborn.moveTo(tank.getX(), tank.getY(), tank.getZ(), tank.getYRot(), 0.0F);
        FactionData.setFaction(reborn, Factions.NONE);
        FactionData.setHome(reborn, null);
        FactionData.setReviveDepth(reborn, depth + 1);
        reborn.finalizeSpawn(level, level.getCurrentDifficultyAt(tank.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        reborn.setHealth(reborn.getMaxHealth());
        level.addFreshEntity(reborn);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % TARGET_SCAN_INTERVAL != 0) {
            return;
        }
        String faction = FactionData.factionOf(mob);
        if (faction.isEmpty()) {
            return;
        }
        LivingEntity current = mob.getTarget();
        if (current != null && (!current.isAlive() || Factions.allied(mob, current) || !acceptable(mob, current))) {
            mob.setTarget(null);
            current = null;
        }
        if (current != null) {
            return;
        }
        LivingEntity intruder = findIntruder(mob);
        if (intruder != null) {
            mob.setTarget(intruder);
        }
    }

    /*
     * 敌意目标的口径。
     * 玩家只认生存与冒险模式, 创造与旁观一律豁免;
     * 坦克会破坏地形, 只对玩家开火, 不因其它生物的攻击或入侵而锁定它们。
     */
    private static boolean acceptable(Mob mob, LivingEntity candidate) {
        if (candidate instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return !(mob instanceof TankElite);
    }

    /* 在领地范围内找最近的入侵者, 同阵营与合作阵营以及同乘组合体都跳过 */
    @Nullable
    private static LivingEntity findIntruder(Mob mob) {
        BlockPos home = FactionData.homeOf(mob);
        if (home == null) {
            return null;
        }
        /* 遗迹生物按站点自己的领地圆柱判定, 其他阵营生物沿用固定范围 */
        double radius = TERRITORY_RADIUS;
        BlockPos center = home;
        RuinSites.Site site = mob.level() instanceof net.minecraft.server.level.ServerLevel level
                ? RuinSites.find(level, home) : null;
        if (site != null) {
            radius = site.radius();
            if (site.banner() != null) {
                center = site.banner();
            }
        }
        AABB area = site == null
                ? new AABB(home).inflate(TERRITORY_RADIUS, TERRITORY_HEIGHT, TERRITORY_RADIUS)
                : new AABB(center).inflate(radius, radius, radius);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == mob || candidate.isSpectator() || !candidate.isAlive()) {
                continue;
            }
            if (!acceptable(mob, candidate)) {
                continue;
            }
            if (candidate.isPassengerOfSameVehicle(mob)) {
                continue;
            }
            /* 遗迹生物额外按圆柱筛选, 盒子的四个角不算领地内 */
            if (site != null && !RuinTerritory.inside(center, radius,
                    candidate.getX(), candidate.getY(), candidate.getZ())) {
                continue;
            }
            if (Factions.allied(mob, candidate) || Factions.same(FactionData.factionOf(mob), FactionData.factionOf(candidate))) {
                continue;
            }
            double distance = mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}