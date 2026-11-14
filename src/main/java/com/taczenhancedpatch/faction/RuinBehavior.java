package com.taczenhancedpatch.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * 遗迹生物的领地行为。
 * 非战斗时越界就走回领地, 战斗时最多追出领地边界一段距离, 超过即放弃目标;
 * 离开领地不会清除归属, 因此走回后仍然属于原遗迹。
 */
public final class RuinBehavior {
    /* 每十个 tick 判定一次即可, 不需要逐 tick 计算 */
    private static final int INTERVAL = 10;

    private RuinBehavior() {
    }

    /* 夺旗期间的聚拢: 已经在打核心区内的玩家就继续打, 否则换目标或走向旗帜 */
    private static void converge(Mob mob, ServerLevel level, BlockPos banner) {
        LivingEntity target = mob.getTarget();
        if (target instanceof Player player && player.isAlive()
                && RuinTerritory.inCore(banner, player.getX(), player.getY(), player.getZ())) {
            return;
        }
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!RuinTerritory.participates(player)
                    || !RuinTerritory.inCore(banner, player.getX(), player.getY(), player.getZ())) {
                continue;
            }
            double distance = player.distanceToSqr(mob);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
            mob.getNavigation().moveTo(nearest, 1.2D);
            return;
        }
        mob.setTarget(null);
        mob.getNavigation().moveTo(banner.getX() + 0.5D, banner.getY(), banner.getZ() + 0.5D, 1.1D);
    }

    public static void tick(Mob mob) {
        if (mob.level().isClientSide || mob.tickCount % INTERVAL != 0) {
            return;
        }
        BlockPos home = FactionData.homeOf(mob);
        if (home == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        RuinSites.Site site = RuinSites.find(level, home);
        if (site == null || site.banner() == null) {
            return;
        }
        BlockPos banner = site.banner();
        /* 夺旗期间守军放弃追击, 全部向核心区聚拢并优先攻击核心区内的玩家 */
        if (site.capturing()) {
            converge(mob, level, banner);
            return;
        }
        /* 领地是等高圆柱: 水平超出半径或竖直超出半径都算越界 */
        boolean outside = !RuinTerritory.inside(banner, site.radius(), mob.getX(), mob.getY(), mob.getZ());
        double distance = RuinTerritory.horizontalDistance(banner, mob.getX(), mob.getZ());
        LivingEntity target = mob.getTarget();
        if (target != null) {
            if (distance > site.radius() + RuinTerritory.PURSUIT_MARGIN) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            return;
        }
        if (outside) {
            mob.getNavigation().moveTo(banner.getX() + 0.5D, banner.getY(), banner.getZ() + 0.5D, 1.0D);
        }
    }
}
