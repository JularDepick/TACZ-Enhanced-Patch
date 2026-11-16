package com.taczenhancedpatch.world.structure;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.config.WorldgenConfig;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
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
        RuinSchematic blueprint = SchematicCatalog.loadSync(picked.get());
        if (blueprint == null) {
            return Optional.empty();
        }
        int width = blueprint.width();
        int height = blueprint.height();
        int depth = blueprint.depth();
        int useful = blueprint.usefulCount();
        if (useful > WorldgenConfig.maxUsefulBlocks()) {
            if (REJECTED.add(picked.get())) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} has {} useful blocks, over the {} limit, skipped",
                        picked.get(), useful, WorldgenConfig.maxUsefulBlocks());
            }
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        int surface = surfaceAt(context.chunkGenerator(), context.heightAccessor(), context.randomState(),
                centerX, centerZ);
        if (surface <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        if (!flatEnough(context.chunkGenerator(), context.heightAccessor(), context.randomState(),
                centerX, surface, centerZ)) {
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
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(new BlockPos(centerX, surface, centerZ),
                (StructurePiecesBuilder builder) -> builder.addPiece(new RuinPiece(name, min, width, height, depth, seed))));
    }

    /* 某一列的世界表面高度 */
    public static int surfaceAt(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                RandomState randomState, int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
    }

    /* 中心附近的地表高差不能太大, 否则大建筑会有一半埋在坡里; 选址索引也用它校验候选点 */
    public static boolean flatEnough(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                     RandomState randomState, int x, int y, int z) {
        return flatEnough(generator, heightAccessor, randomState, x, y, z, null);
    }

    /* 带采样计数的版本, 供选址索引按 tick 预算限流 */
    public static boolean flatEnough(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                     RandomState randomState, int x, int y, int z, SampleCounter counter) {
        for (int dx = -FLAT_RADIUS; dx <= FLAT_RADIUS; dx++) {
            for (int dz = -FLAT_RADIUS; dz <= FLAT_RADIUS; dz++) {
                if (counter != null) {
                    counter.add();
                }
                int surface = surfaceAt(generator, heightAccessor, randomState, x + dx, z + dz);
                if (Math.abs(surface - y) > FLAT_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    /* 选址判定的结果: 合格, 不合格, 预算耗尽需要重排 */
    public static final int FLAT_OK = 1;
    public static final int FLAT_REJECTED = 0;
    public static final int FLAT_ABORTED = -1;

    /*
     * 地表采样计数与时间预算。
     * 预算以时间为准, 采样次数只作兜底: 一格最多 82 次整列采样, 只按次数记账时一次展开就能吃掉好几个 tick。
     */
    public static final class SampleCounter {
        private final long deadline;
        private int value;

        public SampleCounter(long budgetNanos) {
            this.deadline = System.nanoTime() + Math.max(0L, budgetNanos);
        }

        public void add() {
            value++;
        }

        public int value() {
            return value;
        }

        public boolean expired() {
            return System.nanoTime() >= deadline;
        }
    }

    /* 带预算的平整判定: 预算耗尽返回 FLAT_ABORTED, 调用方把这一格重新排队 */
    public static int flatState(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                RandomState randomState, int x, int y, int z, SampleCounter counter) {
        return flatState(generator, heightAccessor, randomState, x, y, z, counter, FLAT_RADIUS);
    }

    /*
     * 带范围参数的版本。
     * 选址索引的即时查询用半径一的粗筛: 只要中心三格见方的高差合格就当作候选,
     * 采样量从八十一列降到九列, 一次右键才能在三秒内扫完十环;
     * 真正的生成期判定仍用完整半径, 因此粗筛只会多指, 不会漏指。
     */
    public static int flatState(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                RandomState randomState, int x, int y, int z, SampleCounter counter, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                counter.add();
                if (counter.expired()) {
                    return FLAT_ABORTED;
                }
                int surface = surfaceAt(generator, heightAccessor, randomState, x + dx, z + dz);
                if (Math.abs(surface - y) > FLAT_TOLERANCE) {
                    return FLAT_REJECTED;
                }
            }
        }
        return FLAT_OK;
    }

    @Override
    public StructureType<?> type() {
        return RuinStructures.RUIN.get();
    }
}
