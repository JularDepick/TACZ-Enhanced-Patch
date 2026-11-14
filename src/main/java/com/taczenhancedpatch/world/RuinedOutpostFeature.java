package com.taczenhancedpatch.world;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * 遗迹生成入口。
 * 建筑本体来自 assets/tacz_enhanced_patch/ruins/ 下的 .schem, 每次生成随机选一座;
 * 本类只做选址与登记, 方块写入交给 RuinPasteHandler 在服务端 tick 里分帧完成,
 * 因此建筑多大都不会撞上世界生成的区块边界, 也不会把生成线程卡住。
 */
public class RuinedOutpostFeature extends Feature<NoneFeatureConfiguration> {
    /* 选址时的地形平整判定范围与允许高差 */
    private static final int FLAT_RADIUS = 4;
    private static final int FLAT_TOLERANCE = 2;
    private static boolean warnedEmpty;

    public RuinedOutpostFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ServerConfig.ruinsEnabled()) {
            return false;
        }
        Optional<String> picked = SchematicCatalog.pick(context.random());
        if (picked.isEmpty()) {
            if (!warnedEmpty) {
                warnedEmpty = true;
                TACZEnhancedPatch.LOGGER.error("[ruin] no blueprint found under assets/{}/ruins, ruin generation disabled",
                        TACZEnhancedPatch.MOD_ID);
            }
            return false;
        }
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (origin.getY() <= level.getSeaLevel()) {
            return false;
        }
        if (!isFlatEnough(level, origin)) {
            return false;
        }
        /* 遗迹之间保持最小直线距离, 避免扎堆; 距离不足时本次不生成 */
        ResourceKey<Level> dimension = level.getLevel().dimension();
        if (RuinSites.tooClose(dimension, origin, ServerConfig.ruinMinDistance())) {
            return false;
        }
        RuinSites.remember(dimension, origin);
        /* 站点只记录中心, 建筑名与底面高度, 粘贴与守军补齐都交给服务端 tick */
        RuinSites.queue(dimension, origin, picked.get(), origin.getY());
        return true;
    }

    /* 中心附近的地表高差不能太大, 否则大建筑会有一半埋在坡里 */
    private boolean isFlatEnough(WorldGenLevel level, BlockPos origin) {
        int base = origin.getY();
        for (int dx = -FLAT_RADIUS; dx <= FLAT_RADIUS; dx++) {
            for (int dz = -FLAT_RADIUS; dz <= FLAT_RADIUS; dz++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                        origin.getX() + dx, origin.getZ() + dz);
                if (Math.abs(surface - base) > FLAT_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }
}
