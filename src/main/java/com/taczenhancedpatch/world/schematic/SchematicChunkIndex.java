package com.taczenhancedpatch.world.schematic;

import javax.annotation.Nullable;

/*
 * 蓝图的分区块非空气格索引。
 * 解析期一次建好: 每个 16x16 的局部区块一份紧凑 int 数组,
 * 元素把区块内 x 与 z 各四位, 高度八位与调色板下标十六位打包在一起。
 * 世界生成时按区块直接取该数组, 只写真正有方块的格子,
 * 因此不再遍历整柱, 也不再为空气格读世界方块。
 */
public final class SchematicChunkIndex {
    private static final int CHUNK = 16;
    private static final int MASK = CHUNK - 1;
    private final int countX;
    private final int countZ;
    private final int[][] cells;
    private final int total;

    private SchematicChunkIndex(int countX, int countZ, int[][] cells, int total) {
        this.countX = countX;
        this.countZ = countZ;
        this.cells = cells;
        this.total = total;
    }

    /*
     * 建索引。
     * 先数每个局部区块有多少格, 再按精确长度分配并填充, 避免扩容与装箱;
     * 调色板下标为零或越界的格子按空气处理, 与 stateAt 的越界取空语义一致。
     */
    public static SchematicChunkIndex build(int width, int height, int depth, short[] blocks, int paletteSize) {
        int countX = (width + MASK) / CHUNK;
        int countZ = (depth + MASK) / CHUNK;
        int[][] cells = new int[countX * countZ][];
        int[] sizes = new int[cells.length];
        for (int y = 0; y < height; y++) {
            int layer = y * depth;
            for (int z = 0; z < depth; z++) {
                int row = (layer + z) * width;
                int bucketBase = (z >> 4) * countX;
                for (int x = 0; x < width; x++) {
                    int slot = blocks[row + x];
                    if (slot <= 0 || slot >= paletteSize) {
                        continue;
                    }
                    sizes[bucketBase + (x >> 4)]++;
                }
            }
        }
        int total = 0;
        for (int slot = 0; slot < cells.length; slot++) {
            cells[slot] = new int[sizes[slot]];
            total += sizes[slot];
            sizes[slot] = 0;
        }
        for (int y = 0; y < height; y++) {
            int layer = y * depth;
            for (int z = 0; z < depth; z++) {
                int row = (layer + z) * width;
                int bucketBase = (z >> 4) * countX;
                int localZ = z & MASK;
                for (int x = 0; x < width; x++) {
                    int slot = blocks[row + x];
                    if (slot <= 0 || slot >= paletteSize) {
                        continue;
                    }
                    int bucket = bucketBase + (x >> 4);
                    cells[bucket][sizes[bucket]++] = pack(x & MASK, localZ, y, slot);
                }
            }
        }
        return new SchematicChunkIndex(countX, countZ, cells, total);
    }

    public static int pack(int localX, int localZ, int y, int slot) {
        return (slot << 16) | ((y & 0xFF) << 8) | ((localZ & MASK) << 4) | (localX & MASK);
    }

    public static int x(int packed, int chunkX) {
        return (chunkX << 4) | (packed & MASK);
    }

    public static int y(int packed) {
        return (packed >> 8) & 0xFF;
    }

    public static int z(int packed, int chunkZ) {
        return (chunkZ << 4) | ((packed >> 4) & MASK);
    }

    public static int palette(int packed) {
        return packed >>> 16;
    }

    public int countX() {
        return countX;
    }

    public int countZ() {
        return countZ;
    }

    /* 非空气格总数, 与世界生成配置的实用方块数上限同一口径 */
    public int total() {
        return total;
    }

    /* 某个局部区块的格子数组, 空区块返回空 */
    @Nullable
    public int[] cellsOf(int chunkX, int chunkZ) {
        if (chunkX < 0 || chunkZ < 0 || chunkX >= countX || chunkZ >= countZ) {
            return null;
        }
        int[] found = cells[chunkZ * countX + chunkX];
        return found == null || found.length == 0 ? null : found;
    }
}
