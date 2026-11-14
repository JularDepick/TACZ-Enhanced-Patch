package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.entity.GunnerGrunt;
import com.taczenhancedpatch.entity.TankElite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 遗迹领地的几何与查询。
 * 领地与核心区都是以旗帜高度为中心的圆柱, 高与水平直径相同, 因此竖直半高等于水平半径;
 * 追出上限在领地半径之外再加一段, 只用于战斗追击。
 */
public final class RuinTerritory {
    /* 核心区半径, 夺旗判定与提示都以它为准 */
    public static final double CORE_RADIUS = 12.0D;
    /* 战斗时可以越出领地边界的格数 */
    public static final double PURSUIT_MARGIN = 16.0D;
    /* 领地半径的余量, 下限与上限 */
    private static final double RADIUS_MARGIN = 16.0D;
    private static final int RADIUS_MIN = 48;
    private static final int RADIUS_MAX = 160;

    private RuinTerritory() {
    }

    /* 按建筑水平外接圆半径自适应, 向上取整到八的倍数 */
    public static int radiusFor(int width, int depth) {
        double half = Math.sqrt(width * width + depth * depth) * 0.5D;
        int radius = Mth.ceil((half + RADIUS_MARGIN) / 8.0D) * 8;
        return Mth.clamp(radius, RADIUS_MIN, RADIUS_MAX);
    }

    public static double horizontalDistance(BlockPos center, double x, double z) {
        double dx = center.getX() + 0.5D - x;
        double dz = center.getZ() + 0.5D - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /* 圆柱判定: 水平半径与竖直半高都取 radius, 因此高恰为水平直径 */
    public static boolean inside(BlockPos banner, double radius, double x, double y, double z) {
        if (horizontalDistance(banner, x, z) > radius) {
            return false;
        }
        return Math.abs(y - (banner.getY() + 0.5D)) <= radius;
    }

    public static boolean inCore(BlockPos banner, double x, double y, double z) {
        return inside(banner, CORE_RADIUS, x, y, z);
    }

    /* 领地内的遗迹生物: 归属该遗迹且位置落在领地圆柱内 */
    public static List<Mob> mobs(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return List.of();
        }
        int radius = site.radius();
        AABB area = new AABB(banner.getX() - radius, level.getMinBuildHeight(), banner.getZ() - radius,
                banner.getX() + radius + 1, level.getMaxBuildHeight(), banner.getZ() + radius + 1);
        return level.getEntitiesOfClass(Mob.class, area, mob -> isRuinMob(mob)
                && site.origin().equals(FactionData.homeOf(mob))
                && inside(banner, radius, mob.getX(), mob.getY(), mob.getZ()));
    }

    /* 在编守军数量: 战斗追出领地时仍算在编, 否则追击期间会被误判为领地已清空 */
    public static int countMobs(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return 0;
        }
        int radius = Mth.ceil(site.radius() + PURSUIT_MARGIN);
        AABB area = new AABB(banner.getX() - radius, level.getMinBuildHeight(), banner.getZ() - radius,
                banner.getX() + radius + 1, level.getMaxBuildHeight(), banner.getZ() + radius + 1);
        return level.getEntitiesOfClass(Mob.class, area, mob -> isRuinMob(mob)
                && site.origin().equals(FactionData.homeOf(mob))
                && inside(banner, radius, mob.getX(), mob.getY(), mob.getZ())).size();
    }

    public static boolean isRuinMob(Mob mob) {
        return mob instanceof GunnerGrunt || mob instanceof TankElite;
    }

    /* 参与遗迹机制的玩家: 创造与旁观不参与夺旗与统计, 与敌意口径保持一致 */
    public static boolean participates(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && !player.isDeadOrDying();
    }

    /* 领地内的参与玩家 */
    public static List<ServerPlayer> playersInside(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        List<ServerPlayer> players = new ArrayList<>();
        if (banner == null) {
            return players;
        }
        for (ServerPlayer player : level.players()) {
            if (participates(player) && inside(banner, site.radius(), player.getX(), player.getY(), player.getZ())) {
                players.add(player);
            }
        }
        return players;
    }

    public static List<ServerPlayer> playersInCore(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        List<ServerPlayer> players = new ArrayList<>();
        if (banner == null) {
            return players;
        }
        for (ServerPlayer player : level.players()) {
            if (participates(player) && inCore(banner, player.getX(), player.getY(), player.getZ())) {
                players.add(player);
            }
        }
        return players;
    }

    /* 补兵位置: 领地内随机水平位置, 排除核心区, 取该列地表 */
    public static BlockPos randomSpawnPos(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return null;
        }
        int radius = site.radius();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = CORE_RADIUS + 2.0D + random.nextDouble() * Math.max(1.0D, radius - CORE_RADIUS - 2.0D);
            int x = banner.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = banner.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, banner.getY(), z));
            if (Math.abs(surface.getY() - banner.getY()) > 16) {
                continue;
            }
            if (!level.getBlockState(surface).isAir() || !level.getBlockState(surface.above()).isAir()) {
                continue;
            }
            if (!level.getBlockState(surface.below()).isSolidRender(level, surface.below())) {
                continue;
            }
            return surface;
        }
        return null;
    }
}
