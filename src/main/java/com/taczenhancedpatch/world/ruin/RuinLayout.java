package com.taczenhancedpatch.world.ruin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * 遗迹布局的运行时形态。
 * 由构建期生成的数据类装配而成, 只描述尺寸, 符号矩阵, 调色板与标记点,
 * 不包含任何生成逻辑, 因此改造型只需要改 JSON 后重新构建。
 */
public record RuinLayout(String id, int width, int depth, int height, int clearHeight, int floorY, char floorSymbol,
                         List<List<String>> layers, Map<Character, List<Entry>> palette,
                         Map<Character, Float> gapChance, Marker chest, String chestFacing,
                         Marker spawner, String spawnerEntity, Debris debris) {

    /* 调色板条目: 方块与抽取权重 */
    public record Entry(Block block, int weight) {
    }

    /* 标记点: 以布局左下角为原点的相对坐标 */
    public record Marker(int x, int y, int z) {
    }

    /* 地面碎块的散布规则 */
    public record Debris(int min, int max, char symbol, int maxOffset, int innerHalfX, int innerHalfZ) {
    }

    /* 按符号抽一个方块状态, 符号没有方块时返回空 */
    public BlockState pick(RandomSource random, char symbol) {
        List<Entry> entries = palette.get(symbol);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Entry entry : entries) {
            total += Math.max(1, entry.weight());
        }
        int roll = random.nextInt(total);
        for (Entry entry : entries) {
            roll -= Math.max(1, entry.weight());
            if (roll < 0) {
                return entry.block().defaultBlockState();
            }
        }
        return entries.get(entries.size() - 1).block().defaultBlockState();
    }

    /* 该符号在摆放时的随机缺口概率, 未声明则为零 */
    public float gapChanceOf(char symbol) {
        Float chance = gapChance.get(symbol);
        return chance == null ? 0.0F : chance;
    }
}
