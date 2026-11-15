package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * 建筑粘贴器。
 * 分两段工作: 先把预定区域整片清空, 再写入建筑方块。
 * 清空是独立的步骤而不是"顺便写空气", 因为空心建筑的内部必须保证是空的,
 * 否则原地形会残留在房间里。
 * 两段都按方块游标推进, 调用方负责分帧与预算, 这里不持有跨 tick 状态。
 */
public final class SchematicPaster {
    /* 只通知客户端: 绝大多数方块不需要邻接更新, 逐块触发会让粘贴帧变慢 */
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /* 玻璃板, 栅栏, 围墙与楼梯的连接形状依赖邻接更新, 这几类才付额外开销 */
    private static final int SHAPE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
    /* 多方块结构匹配向四周看几格, 南瓜召唤傀儡时用到的图案就是一格 */
    private static final int PATTERN_REACH = 1;

    /* 单个区块切片的遍历范围与方块总数 */
    public record Slice(int startX, int endX, int startZ, int endZ, int spanX, int spanZ, int total) {
    }

    private SchematicPaster() {
    }

    public static Slice slice(RuinSchematic schematic, BlockPos min, int chunkX, int chunkZ) {
        int minX = min.getX();
        int minZ = min.getZ();
        int startX = Math.max(chunkX << 4, minX);
        int endX = Math.min((chunkX << 4) + 15, minX + schematic.width() - 1);
        int startZ = Math.max(chunkZ << 4, minZ);
        int endZ = Math.min((chunkZ << 4) + 15, minZ + schematic.depth() - 1);
        if (startX > endX || startZ > endZ) {
            return new Slice(0, -1, 0, -1, 0, 0, 0);
        }
        int spanX = endX - startX + 1;
        int spanZ = endZ - startZ + 1;
        return new Slice(startX, endX, startZ, endZ, spanX, spanZ, spanX * spanZ * schematic.height());
    }

    /* 清空预定区域: 把范围内的非空气方块全部置空, 已经是空气的直接跳过 */
    public static int clear(ServerLevel level, RuinSchematic schematic, BlockPos min, Slice slice,
                            int cursor, int budget) {
        if (slice.total() <= 0) {
            return 0;
        }
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        int minY = min.getY();
        int perLayer = slice.spanX() * slice.spanZ();
        int index = Math.max(0, cursor);
        int end = Math.min(slice.total(), index + Math.max(1, budget));
        for (; index < end; index++) {
            int y = index / perLayer;
            int rest = index % perLayer;
            target.set(slice.startX() + rest % slice.spanX(), minY + y,
                    slice.startZ() + rest / slice.spanX());
            if (!level.getBlockState(target).isAir()) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        return index;
    }

    /* 写入建筑方块: 空气已经由清空阶段处理, 这里只写实体方块 */
    public static int paste(ServerLevel level, RuinSchematic schematic, BlockPos min, Slice slice,
                            int cursor, int budget) {
        if (slice.total() <= 0) {
            return 0;
        }
        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int perLayer = slice.spanX() * slice.spanZ();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        int index = Math.max(0, cursor);
        int end = Math.min(slice.total(), index + Math.max(1, budget));
        for (; index < end; index++) {
            int y = index / perLayer;
            int rest = index % perLayer;
            int x = slice.startX() + rest % slice.spanX();
            int z = slice.startZ() + rest / slice.spanX();
            BlockState state = schematic.stateAt(x - minX, y, z - minZ);
            if (state == null) {
                continue;
            }
            target.set(x, minY + y, z);
            place(level, target, state, needsShape(state) ? SHAPE_FLAGS : FLAGS);
        }
        return index;
    }

    /*
     * 落一个方块。
     * 南瓜在 onPlace 里做多方块结构匹配, 匹配用的 BlockInWorld 不允许加载区块,
     * 邻块还没加载时取到的状态是 null, 会直接抛空指针; 遗迹在远处落地时邻块可能已经卸载, 因此先把邻块备好。
     * 单块失败只记一条日志: 方块本身已经写入, 缺的只是它的 onPlace 副作用, 不该让整个服务端 tick 崩掉。
     */
    public static void place(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        if (state.getBlock() instanceof CarvedPumpkinBlock) {
            ensurePatternChunks(level, pos.getX(), pos.getZ());
        }
        try {
            level.setBlock(pos, state, flags);
        } catch (RuntimeException error) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] placing {} at {} failed: {}", state, pos, error.toString());
        }
    }

    /* 结构匹配只向四周看一格, 把这一圈涉及的区块先加载好 */
    private static void ensurePatternChunks(ServerLevel level, int x, int z) {
        ServerChunkCache chunks = level.getChunkSource();
        int minChunkX = (x - PATTERN_REACH) >> 4;
        int maxChunkX = (x + PATTERN_REACH) >> 4;
        int minChunkZ = (z - PATTERN_REACH) >> 4;
        int maxChunkZ = (z + PATTERN_REACH) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }
    }

    private static boolean needsShape(BlockState state) {
        return state.hasProperty(BlockStateProperties.NORTH)
                || state.hasProperty(BlockStateProperties.STAIRS_SHAPE);
    }
}
