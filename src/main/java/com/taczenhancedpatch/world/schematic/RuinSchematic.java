package com.taczenhancedpatch.world.schematic;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 一座遗迹建筑的运行时形态。
 * 只保留尺寸, 调色板索引与调色板本身, 空方块统一记为索引零,
 * 写入时先判空再取状态, 避免大量无效写入。
 * 解析期同时建好按区块的非空气格索引, 世界生成按区块直接取列表, 不再遍历整柱也不读空气格。
 */
public record RuinSchematic(String id, int width, int height, int depth, short[] blocks, BlockState[] palette,
                            SchematicChunkIndex chunks) {

    public int volume() {
        return width * height * depth;
    }

    public int index(int x, int y, int z) {
        return (y * depth + z) * width + x;
    }

    /* 索引处的方块状态, 空方块返回空; 越界一律当空, 防止蓝图被外部改动后尺寸对不上 */
    public BlockState stateAt(int index) {
        if (index < 0 || index >= blocks.length) {
            return null;
        }
        short slot = blocks[index];
        return slot <= 0 || slot >= palette.length ? null : palette[slot];
    }

    public BlockState stateAt(int x, int y, int z) {
        return stateAt(index(x, y, z));
    }

    /* 实用方块数: 只数非空气方块, 空气在解析时已被跳过; 世界生成用它按上限挡掉过大的蓝图 */
    public int usefulCount() {
        return chunks.total();
    }
}
