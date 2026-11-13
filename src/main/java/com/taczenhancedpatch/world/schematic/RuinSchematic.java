package com.taczenhancedpatch.world.schematic;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 一座遗迹建筑的运行时形态。
 * 只保留尺寸, 调色板索引与调色板本身, 空方块统一记为索引零,
 * 粘贴时先判空再取状态, 避免大量无效写入。
 */
public record RuinSchematic(String id, int width, int height, int depth, short[] blocks, BlockState[] palette,
                            int solidCount) {

    public int volume() {
        return width * height * depth;
    }

    public int index(int x, int y, int z) {
        return (y * depth + z) * width + x;
    }

    /* 索引处的方块状态, 空方块返回空 */
    public BlockState stateAt(int index) {
        short slot = blocks[index];
        return slot <= 0 ? null : palette[slot];
    }

    public BlockState stateAt(int x, int y, int z) {
        return stateAt(index(x, y, z));
    }
}
