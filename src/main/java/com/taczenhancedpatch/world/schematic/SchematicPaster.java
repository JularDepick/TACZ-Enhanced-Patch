package com.taczenhancedpatch.world.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 建筑粘贴器。
 * 只做一件事: 把 schematic 落在指定区块范围内的方块写进世界, 调用方负责分帧与区块加载,
 * 因此这里不持有任何跨 tick 状态, 也不做地形适配与容器摆放。
 */
public final class SchematicPaster {
    /* 只通知客户端: 绝大多数方块不需要邻接更新, 逐块触发会让粘贴帧变慢 */
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /* 玻璃板, 栅栏, 围墙与楼梯的连接形状依赖邻接更新, 这几类才付额外开销 */
    private static final int SHAPE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;

    private SchematicPaster() {
    }

    public static int pasteChunk(ServerLevel level, RuinSchematic schematic, BlockPos min, int chunkX, int chunkZ) {
        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int startX = Math.max(chunkX << 4, minX);
        int endX = Math.min((chunkX << 4) + 15, minX + schematic.width() - 1);
        int startZ = Math.max(chunkZ << 4, minZ);
        int endZ = Math.min((chunkZ << 4) + 15, minZ + schematic.depth() - 1);
        if (startX > endX || startZ > endZ) {
            return 0;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int written = 0;
        for (int y = 0; y < schematic.height(); y++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {
                    BlockState state = schematic.stateAt(x - minX, y, z - minZ);
                    if (state == null) {
                        continue;
                    }
                    cursor.set(x, minY + y, z);
                    level.setBlock(cursor, state, needsShape(state) ? SHAPE_FLAGS : FLAGS);
                    written++;
                }
            }
        }
        return written;
    }

    private static boolean needsShape(BlockState state) {
        return state.hasProperty(BlockStateProperties.NORTH)
                || state.hasProperty(BlockStateProperties.STAIRS_SHAPE);
    }
}
