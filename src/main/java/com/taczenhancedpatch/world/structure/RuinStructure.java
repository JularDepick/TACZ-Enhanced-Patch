package com.taczenhancedpatch.world.structure;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.config.WorldgenConfig;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 遗迹结构。
 * 与原版结构同一套流程: 由结构集挑候选区块, 这里判断地形与蓝图是否可用, 再把整座建筑交给一个片段。
 * 方块写入发生在区块自己的生成阶段, 不经过服务端 tick, 也不需要回滚:
 * 区块没走到完整状态就不会写盘, 下次按种子重新生成即可。
 */
public class RuinStructure extends Structure {
    public static final Codec<RuinStructure> CODEC = Structure.simpleCodec(RuinStructure::new);
    /* 选址时的地表平整判定范围与允许高差 */
    private static final int FLAT_RADIUS = 4;
    private static final int FLAT_TOLERANCE = 2;
    /* 超限蓝图只告警一次, 避免每个候选区块刷一条日志 */
    private static final Set<String> REJECTED = ConcurrentHashMap.newKeySet();

    public RuinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ServerConfig.ruinsEnabled()) {
            return Optional.empty();
        }
        Optional<String> picked = SchematicCatalog.pick(context.random());
        if (picked.isEmpty()) {
            return Optional.empty();
        }
        RuinSchematic blueprint = SchematicCatalog.load(picked.get());
        if (blueprint == null) {
            return Optional.empty();
        }
        int width = blueprint.width();
        int height = blueprint.height();
        int depth = blueprint.depth();
        int solid = blueprint.solidCount();
        if (solid > WorldgenConfig.maxSolidBlocks()) {
            if (REJECTED.add(picked.get())) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} has {} solid blocks, over the {} limit, skipped",
                        picked.get(), solid, WorldgenConfig.maxSolidBlocks());
            }
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        int surface = context.chunkGenerator().getBaseHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (surface <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        if (!flatEnough(context, centerX, surface, centerZ)) {
            return Optional.empty();
        }
        int minX = centerX - width / 2;
        int minZ = centerZ - depth / 2;
        int spanX = ((minX + width - 1) >> 4) - (minX >> 4) + 1;
        int spanZ = ((minZ + depth - 1) >> 4) - (minZ >> 4) + 1;
        if (spanX * spanZ > WorldgenConfig.maxChunks()) {
            if (REJECTED.add(picked.get())) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} covers {} chunks, over the {} limit, skipped",
                        picked.get(), spanX * spanZ, WorldgenConfig.maxChunks());
            }
            return Optional.empty();
        }
        BlockPos min = new BlockPos(minX, surface, minZ);
        String name = picked.get();
        return Optional.of(new GenerationStub(new BlockPos(centerX, surface, centerZ),
                (StructurePiecesBuilder builder) -> builder.addPiece(new RuinPiece(name, min, width, height, depth))));
    }

    /* 中心附近的地表高差不能太大, 否则大建筑会有一半埋在坡里 */
    private static boolean flatEnough(GenerationContext context, int x, int y, int z) {
        for (int dx = -FLAT_RADIUS; dx <= FLAT_RADIUS; dx++) {
            for (int dz = -FLAT_RADIUS; dz <= FLAT_RADIUS; dz++) {
                int surface = context.chunkGenerator().getBaseHeight(x + dx, z + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                if (Math.abs(surface - y) > FLAT_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return RuinStructures.RUIN.get();
    }
}
