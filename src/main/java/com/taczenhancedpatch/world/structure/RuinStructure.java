package com.taczenhancedpatch.world.structure;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/*
 * 遗迹结构。
 * 与原版结构同一套流程: 由结构集挑候选区块, 这里只判断地形与蓝图是否可用,
 * 然后把整座建筑交给一个片段, 方块写入发生在区块自己的生成阶段, 不经过服务端 tick。
 */
public class RuinStructure extends Structure {
    public static final Codec<RuinStructure> CODEC = Structure.simpleCodec(RuinStructure::new);
    /* 选址时的地表平整判定范围与允许高差 */
    private static final int FLAT_RADIUS = 4;
    private static final int FLAT_TOLERANCE = 2;
    /* 第一阶段只放一个石方块标记, 用来验证结构管线 */
    private static final int MARKER_SIZE = 3;

    public RuinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!ServerConfig.ruinsEnabled()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMinBlockX() + 8;
        int z = chunk.getMinBlockZ() + 8;
        int surface = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        if (surface <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        if (!flatEnough(context, x, surface, z)) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, surface, z);
        return Optional.of(new GenerationStub(origin, (StructurePiecesBuilder builder) -> builder.addPiece(
                new RuinPiece(origin, MARKER_SIZE, MARKER_SIZE, MARKER_SIZE))));
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
