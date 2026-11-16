package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

/*
 * 遗迹选址索引。
 * 每个维度一份文件, 放在该维度的 region 目录旁边, 与区块数据同级, 跟着存档走。
 * 每格只记一次选址判定的结果: 候选区块, 地表高度, 建筑名与生成状态。
 * 判定只用存档种子与噪声采样, 不加载区块, 也不触发生成, 所以未探索的区域也能查。
 */
public final class RuinSiteIndex {
    public static final String FILE_NAME = "tacz_enhanced_patch_ruin_index.dat";
    private static final int FORMAT_VERSION = 1;
    /* 索引表上限: 超过后先丢已判定无遗迹的格, 仍然超限就整表重来 */
    private static final int MAX_CELLS = 200_000;

    /* 一格的判定结果 */
    public enum State {
        /* 地形不合格, 这格不会有遗迹 */
        NO_CANDIDATE,
        /* 候选点存在, 区块还没生成 */
        PREDICTED,
        /* 区块已生成, 但那里没有遗迹 */
        REJECTED,
        /* 遗迹已生成, 建筑还没铺齐 */
        GENERATING,
        /* 建筑已铺齐 */
        GENERATED
    }

    public record Cell(State state, int y, String name) {
        public boolean site() {
            return state == State.PREDICTED || state == State.GENERATING || state == State.GENERATED;
        }
    }

    public record Hit(BlockPos pos, State state, String name, int ring) {
    }

    private final long seed;
    private final int spacing;
    private final int separation;
    /* 参考格的候选区块, 盐值变了它会跟着变, 用来识别数据包改动 */
    private final ChunkPos probe;
    private final RandomSpreadStructurePlacement placement;
    private final Map<Long, Cell> cells = new HashMap<>();
    private boolean dirty;
    /* 异步写是否在飞: 在飞时跳过新一轮快照, 写线程不会积压 */
    private boolean writing;

    private RuinSiteIndex(long seed, RandomSpreadStructurePlacement placement) {
        this.seed = seed;
        this.spacing = placement.spacing();
        this.separation = placement.separation();
        this.probe = placement.getPotentialStructureChunk(seed, 0, 0);
        this.placement = placement;
    }

    /* 读取存档里的索引, 文件缺失或参数对不上时返回空索引 */
    public static RuinSiteIndex load(ServerLevel level, RandomSpreadStructurePlacement placement) {
        long seed = level.getSeed();
        ChunkPos probe = placement.getPotentialStructureChunk(seed, 0, 0);
        Path path = file(level);
        if (Files.isRegularFile(path)) {
            try {
                CompoundTag tag = NbtIo.readCompressed(path.toFile());
                if (tag.getInt("version") == FORMAT_VERSION
                        && tag.getLong("seed") == seed
                        && tag.getInt("spacing") == placement.spacing()
                        && tag.getInt("separation") == placement.separation()
                        && tag.getInt("probeX") == probe.x
                        && tag.getInt("probeZ") == probe.z) {
                    return read(seed, placement, tag);
                }
                TACZEnhancedPatch.LOGGER.info("[ruin] site index header mismatch, rebuilt");
            } catch (IOException | RuntimeException exception) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] failed to read site index, rebuilt", exception);
            }
        }
        RuinSiteIndex index = new RuinSiteIndex(seed, placement);
        index.dirty = true;
        return index;
    }

    /* 落盘走单独线程: 整表写盘不该占服务端线程 */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tacz-enhanced-patch-ruin-index-writer");
        thread.setDaemon(true);
        return thread;
    });

    public void save(ServerLevel level) {
        writeTo(level, false);
    }

    /* 服务端停止时用: 直接在当前线程写完, 避免进程退出丢掉最后一次索引 */
    public void saveNow(ServerLevel level) {
        writeTo(level, true);
    }

    private void writeTo(ServerLevel level, boolean sync) {
        if (!dirty) {
            return;
        }
        /* 上一次异步写还没跑完: 保留 dirty, 下一轮再拍快照, 避免整表副本堆在写线程队列里 */
        if (!sync && writing) {
            return;
        }
        Path path = file(level);
        /* 先在当前线程拍一份快照, 之后表的改动不会影响这次落盘 */
        CompoundTag tag = write();
        dirty = false;
        Runnable job = () -> {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                /* 先写临时文件再原子替换: 写到一半崩溃不会留下半个 NBT */
                Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                NbtIo.writeCompressed(tag, temp.toFile());
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | UnsupportedOperationException exception) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException exception) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] failed to save site index", exception);
            }
        };
        if (sync) {
            job.run();
        } else {
            writing = true;
            WRITER.execute(() -> {
                try {
                    job.run();
                } finally {
                    writing = false;
                }
            });
        }
    }

    /* 存档种子与选址参数是否都与当前维度一致 */
    public boolean matches(long levelSeed, RandomSpreadStructurePlacement other) {
        if (levelSeed != seed || spacing != other.spacing() || separation != other.separation()) {
            return false;
        }
        ChunkPos candidate = other.getPotentialStructureChunk(seed, 0, 0);
        return candidate.x == probe.x && candidate.z == probe.z;
    }

    public int spacing() {
        return spacing;
    }

    /* 一格的边长, 单位方块 */
    public int cellBlocks() {
        return spacing * 16;
    }

    public int cellOf(int block) {
        return Math.floorDiv(block, cellBlocks());
    }

    public long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
    }

    /* 格内的候选区块, 与生成期同一套数学 */
    public ChunkPos candidate(int cellX, int cellZ) {
        return placement.getPotentialStructureChunk(seed, cellX * spacing, cellZ * spacing);
    }

    /*
     * 判定一格。
     * 只查生成期与地形有关的条件, 与蓝图有关的条件等区块真正生成后再回填,
     * 因此预测结果只会多指, 不会漏指, 多指的部分由生成后的状态纠正。
     * 返回假表示本 tick 预算在格内耗尽, 调用方需要把这一格重新排队。
     */
    public boolean evaluate(ServerLevel level, ChunkGenerator generator, RandomState randomState,
                            int cellX, int cellZ, RuinStructure.SampleCounter counter) {
        long key = cellKey(cellX, cellZ);
        if (cells.containsKey(key)) {
            return true;
        }
        ChunkPos chunk = candidate(cellX, cellZ);
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        counter.add();
        int surface = RuinStructure.surfaceAt(generator, level, randomState, x, z);
        if (surface <= generator.getSeaLevel()) {
            mark(cellX, cellZ, State.NO_CANDIDATE, surface, "");
            return true;
        }
        int flat = RuinStructure.flatState(generator, level, randomState, x, surface, z, counter);
        if (flat == RuinStructure.FLAT_ABORTED) {
            return false;
        }
        if (flat == RuinStructure.FLAT_REJECTED) {
            mark(cellX, cellZ, State.NO_CANDIDATE, surface, "");
            return true;
        }
        mark(cellX, cellZ, State.PREDICTED, surface, "");
        return true;
    }

    /* 区块生成完了但没有遗迹 */
    public void markRejected(int cellX, int cellZ) {
        Cell current = cells.get(cellKey(cellX, cellZ));
        if (current != null && (current.state() == State.GENERATING || current.state() == State.GENERATED)) {
            return;
        }
        mark(cellX, cellZ, State.REJECTED, current == null ? 0 : current.y(), "");
    }

    /* 建筑覆盖的区块全部铺齐 */
    public void markGenerated(int cellX, int cellZ) {
        Cell current = cells.get(cellKey(cellX, cellZ));
        if (current == null) {
            return;
        }
        mark(cellX, cellZ, State.GENERATED, current.y(), current.name());
    }

    /* 站点接线后回填真实建筑名与底面高度 */
    public void markBound(int cellX, int cellZ, int y, String name) {
        Cell current = cells.get(cellKey(cellX, cellZ));
        if (current != null && current.state() == State.GENERATED) {
            return;
        }
        mark(cellX, cellZ, State.GENERATING, y, name);
    }

    public void mark(int cellX, int cellZ, State state, int y, @Nullable String name) {
        long key = cellKey(cellX, cellZ);
        Cell current = cells.get(key);
        if (current != null) {
            if (current.state() == State.GENERATED) {
                return;
            }
            if (state == State.REJECTED && current.state() == State.GENERATING) {
                return;
            }
        }
        cells.put(key, new Cell(state, y, name == null ? "" : name));
        dirty = true;
        if (cells.size() > MAX_CELLS) {
            prune();
        }
    }

    /*
     * 收缩索引表。
     * 已判定无遗迹的格最没价值, 先丢它们; 仍然超限说明长期存档走得太远, 整表重来,
     * 已生成的状态会在玩家再次靠近时由站点回写补回。
     */
    private void prune() {
        Iterator<Map.Entry<Long, Cell>> iterator = cells.entrySet().iterator();
        while (iterator.hasNext() && cells.size() > MAX_CELLS * 3 / 4) {
            Cell cell = iterator.next().getValue();
            if (cell.state() == State.NO_CANDIDATE || cell.state() == State.REJECTED) {
                iterator.remove();
            }
        }
        if (cells.size() > MAX_CELLS) {
            cells.clear();
        }
        dirty = true;
    }

    /*
     * 距离给定位置最近的一格遗迹。
     * 按格环从内向外扫, 命中后多扫一圈再定案, 因为相邻格的遗迹有可能比上一圈更近;
     * 已夺旗的遗迹跳过。
     */
    @Nullable
    public Hit nearest(ServerLevel level, BlockPos from, int maxCells) {
        int cellX = cellOf(from.getX());
        int cellZ = cellOf(from.getZ());
        Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        int bestRing = -1;
        for (int ring = 0; ring <= maxCells; ring++) {
            int size = ringSize(ring);
            for (int index = 0; index < size; index++) {
                long key = ringCell(cellX, cellZ, ring, index);
                Cell cell = cells.get(key);
                if (cell == null || !cell.site()) {
                    continue;
                }
                ChunkPos chunk = candidate((int) (key >> 32), (int) key);
                BlockPos pos = new BlockPos(chunk.getMiddleBlockX(), cell.y(), chunk.getMiddleBlockZ());
                if (captured(level, pos)) {
                    continue;
                }
                double distance = pos.distSqr(from);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Hit(pos, cell.state(), cell.name(), ring);
                    bestRing = ring;
                }
            }
            if (best != null && ring >= bestRing + 1) {
                return best;
            }
        }
        return best;
    }

    /* 环上的格数, 第 0 环是一格, 之后每环八格递增 */
    public static int ringSize(int ring) {
        return ring <= 0 ? 1 : ring * 8;
    }

    /* 环上第 index 个格, 按上边, 下边, 左边, 右边的顺序 */
    public static long ringCell(int cellX, int cellZ, int ring, int index) {
        if (ring <= 0) {
            return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        }
        int span = ring * 2 + 1;
        int rest = index;
        if (rest < span) {
            return ((long) (cellX - ring + rest) << 32) ^ ((cellZ - ring) & 0xffffffffL);
        }
        rest -= span;
        if (rest < span) {
            return ((long) (cellX - ring + rest) << 32) ^ ((cellZ + ring) & 0xffffffffL);
        }
        rest -= span;
        int side = ring * 2 - 1;
        if (rest < side) {
            return ((long) (cellX - ring) << 32) ^ ((cellZ - ring + 1 + rest) & 0xffffffffL);
        }
        rest -= side;
        return ((long) (cellX + ring) << 32) ^ ((cellZ - ring + 1 + rest) & 0xffffffffL);
    }

    private static boolean captured(ServerLevel level, BlockPos pos) {
        RuinSites.Site site = RuinSites.find(level, pos);
        return site != null && site.captured();
    }

    private static Path file(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        return DimensionType.getStorageFolder(level.dimension(), root).resolve(FILE_NAME);
    }

    private CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", FORMAT_VERSION);
        tag.putLong("seed", seed);
        tag.putInt("spacing", spacing);
        tag.putInt("separation", separation);
        tag.putInt("probeX", probe.x);
        tag.putInt("probeZ", probe.z);
        long[] keys = new long[cells.size()];
        byte[] states = new byte[cells.size()];
        int[] heights = new int[cells.size()];
        ListTag names = new ListTag();
        int slot = 0;
        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            keys[slot] = entry.getKey();
            states[slot] = (byte) entry.getValue().state().ordinal();
            heights[slot] = entry.getValue().y();
            names.add(StringTag.valueOf(entry.getValue().name()));
            slot++;
        }
        tag.putLongArray("keys", keys);
        tag.putByteArray("states", states);
        tag.putIntArray("heights", heights);
        tag.put("names", names);
        return tag;
    }

    private static RuinSiteIndex read(long seed, RandomSpreadStructurePlacement placement, CompoundTag tag) {
        RuinSiteIndex index = new RuinSiteIndex(seed, placement);
        long[] keys = tag.getLongArray("keys");
        byte[] states = tag.getByteArray("states");
        int[] heights = tag.getIntArray("heights");
        ListTag names = tag.getList("names", Tag.TAG_STRING);
        int size = Math.min(keys.length, Math.min(states.length, heights.length));
        State[] values = State.values();
        for (int slot = 0; slot < size; slot++) {
            int state = states[slot];
            if (state < 0 || state >= values.length) {
                continue;
            }
            String name = slot < names.size() ? names.getString(slot) : "";
            index.cells.put(keys[slot], new Cell(values[state], heights[slot], name));
        }
        index.dirty = false;
        return index;
    }
}
