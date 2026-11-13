package com.taczenhancedpatch.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

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
        double distance = RuinTerritory.horizontalDistance(banner, mob.getX(), mob.getZ());
        LivingEntity target = mob.getTarget();
        if (target != null) {
            if (distance > site.radius() + RuinTerritory.PURSUIT_MARGIN) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            return;
        }
        if (distance > site.radius()) {
            mob.getNavigation().moveTo(banner.getX() + 0.5D, banner.getY(), banner.getZ() + 0.5D, 1.0D);
        }
    }
}
