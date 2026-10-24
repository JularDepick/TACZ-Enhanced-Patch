package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
        /* 同阵营与合作阵营之间不误伤 */
        if (Factions.allied(victim, attacker)) {
            event.setCanceled(true);
            return;
        }
        if (victim.level().isClientSide()) {
            return;
        }
        /* 被攻击者立刻还击 */
        if (victim instanceof Mob mob) {
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
            ally.setTarget(attacker);
            ally.setLastHurtByMob(attacker);
        }
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
        if (current != null && (!current.isAlive() || Factions.allied(mob, current))) {
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

    /* 在领地范围内找最近的入侵者, 同阵营与合作阵营以及同乘组合体都跳过 */
    @Nullable
    private static LivingEntity findIntruder(Mob mob) {
        BlockPos home = FactionData.homeOf(mob);
        if (home == null) {
            return null;
        }
        AABB area = new AABB(home).inflate(TERRITORY_RADIUS, TERRITORY_HEIGHT, TERRITORY_RADIUS);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == mob || candidate.isSpectator() || !candidate.isAlive()) {
                continue;
            }
            if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (candidate.isPassengerOfSameVehicle(mob)) {
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
