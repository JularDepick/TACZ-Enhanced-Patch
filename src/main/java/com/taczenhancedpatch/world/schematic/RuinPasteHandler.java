package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinGarrison;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/*
 * 遗迹建筑的落地调度。
 * 站点一入队就开始粘贴, 与玩家远近无关: 需要的区块当场加载, 不等待也不暂停。
 * 每座建筑有十秒的实际时间预算, 超时就把已经写过的方块按快照还原, 并放弃这一座遗迹;
 * 放弃的站点不登记间距, 因此同一片区域在后续生成里仍可能重新选中。
 * 成功落地后才登记间距, 夺旗后的死遗迹同样保留这条登记, 继续参与后续选址的距离判定。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinPasteHandler {
    /* 每个 tick 最多写入的方块数, 覆盖式粘贴连空气一起处理 */
    private static final int BLOCKS_PER_TICK = 6000;
    /* 单座遗迹从开始粘贴到完成的时间上限, 单位毫秒 */
    private static final long JOB_DEADLINE_MILLIS = 10000L;
    /* 地基补齐的最大深度, 超过部分留空, 避免在悬崖边上灌出石柱 */
    private static final int MAX_FOUNDATION = 24;
    private static final int MAX_JOBS = 8;
    /* 单个区块的处理阶段 */
    private static final int STAGE_START = 0;
    private static final int STAGE_CLEAR = 1;
    private static final int STAGE_PASTE = 2;
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));
    private static final BlockState[] FOUNDATION = {
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
    private static final Map<JobKey, Job> JOBS = new HashMap<>();
    /* 已经按存档补过间距登记的维度, 避免每 tick 重复播种 */
    private static final Set<ResourceKey<Level>> SEEDED = new HashSet<>();

    /* 任务键带维度, 避免不同维度同名坐标互相干扰 */
    private record JobKey(ResourceKey<Level> dimension, BlockPos origin) {
    }

    public static boolean isPasting(ServerLevel level, BlockPos origin) {
        return JOBS.containsKey(new JobKey(level.dimension(), origin));
    }

    /* 服务端停止时丢弃全部任务; 未完成站点下次进存档重新粘贴 */
    public static void clearJobs() {
        JOBS.clear();
        SEEDED.clear();
    }

    private RuinPasteHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        event.getServer().getAllLevels().forEach(RuinPasteHandler::tickLevel);
    }

    private static void tickLevel(ServerLevel level) {
        RuinSites data = RuinSites.get(level);
        data.drainPending(level);
        seedSpacing(level, data);
        List<RuinSites.Site> sites = data.sites();
        if (sites.isEmpty()) {
            return;
        }
        int budget = BLOCKS_PER_TICK;
        for (int i = 0; i < sites.size() && budget > 0; i++) {
            RuinSites.Site site = sites.get(i);
            if (site.pasted() || site.abandoned() || site.captured()) {
                continue;
            }
            JobKey key = new JobKey(level.dimension(), site.origin());
            Job job = JOBS.get(key);
            if (job == null) {
                if (JOBS.size() >= MAX_JOBS) {
                    continue;
                }
                job = createJob(level, site, data);
                if (job == null) {
                    continue;
                }
                JOBS.put(key, job);
            }
            if (System.currentTimeMillis() - job.startedAt > JOB_DEADLINE_MILLIS) {
                abandon(level, site, job, data);
                continue;
            }
            budget = advance(level, site, job, data, budget);
        }
    }

    /* 存档里的站点补进间距登记, 死遗迹也在内, 因此重启后仍然参与距离判定 */
    private static void seedSpacing(ServerLevel level, RuinSites data) {
        if (!SEEDED.add(level.dimension())) {
            return;
        }
        for (RuinSites.Site site : data.sites()) {
            RuinSites.remember(level.dimension(), site.origin());
        }
    }

    /* 超过时间预算: 还原已经写过的方块, 放弃这一座遗迹, 且不登记间距 */
    private static void abandon(ServerLevel level, RuinSites.Site site, Job job, RuinSites data) {
        for (Map.Entry<Long, BlockState> entry : job.snapshot.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
        }
        JOBS.remove(new JobKey(level.dimension(), site.origin()));
        data.removeSite(site.origin());
        data.setDirty();
        TACZEnhancedPatch.LOGGER.warn("[ruin] paste timed out at {}, terrain restored and site dropped", site.origin());
    }

    private static Job createJob(ServerLevel level, RuinSites.Site site, RuinSites data) {
        String name = site.schematic();
        RuinSchematic schematic = name.isEmpty() ? null : SchematicCatalog.load(name);
        if (schematic == null && !name.isEmpty() && SchematicCatalog.isPending(name)) {
            return null;
        }
        if (schematic == null) {
            if (!replace(level, site, data, name)) {
                site.setAbandoned(true);
                data.setDirty();
            }
            return null;
        }
        int minX = site.origin().getX() - schematic.width() / 2;
        int minZ = site.origin().getZ() - schematic.depth() / 2;
        BlockPos min = new BlockPos(minX, site.baseY(), minZ);
        /* 需要的区块当场加载, 不等玩家靠近 */
        loadChunks(level, schematic, min);
        Job job = new Job(schematic, min, site.baseY());
        job.startedAt = System.currentTimeMillis();
        snapshot(level, job);
        return job;
    }

    private static void loadChunks(ServerLevel level, RuinSchematic schematic, BlockPos min) {
        ServerChunkCache chunks = level.getChunkSource();
        int minChunkX = min.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkX = (min.getX() + schematic.width() - 1) >> 4;
        int maxChunkZ = (min.getZ() + schematic.depth() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }
    }

    /*
     * 记下整片区域的原始方块, 超时还原以此为底。
     * 范围要在建筑盒之外再放宽: 地基会补到盒子下方, 散落物会落在盒子四周, 这些同样需要还原。
     */
    private static void snapshot(ServerLevel level, Job job) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int pad = 4;
        int minX = job.min.getX() - pad;
        int maxX = job.min.getX() + job.schematic.width() + pad;
        int minZ = job.min.getZ() - pad;
        int maxZ = job.min.getZ() + job.schematic.depth() + pad;
        int minY = job.baseY - MAX_FOUNDATION - 1;
        int maxY = job.min.getY() + job.schematic.height() + 1;
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    cursor.set(x, y, z);
                    job.snapshot.put(cursor.asLong(), level.getBlockState(cursor));
                }
            }
        }
    }

    private static boolean replace(ServerLevel level, RuinSites.Site site, RuinSites data, String failed) {
        Optional<String> picked = SchematicCatalog.pickOther(failed, level.getRandom());
        if (picked.isEmpty()) {
            TACZEnhancedPatch.LOGGER.error("[ruin] no usable blueprint for site at {}, ruin skipped", site.origin());
            return false;
        }
        site.setSchematic(picked.get());
        data.setDirty();
        TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} unusable at {}, replaced by {}",
                failed, site.origin(), picked.get());
        return true;
    }

    private static int advance(ServerLevel level, RuinSites.Site site, Job job, RuinSites data, int budget) {
        ServerChunkCache chunks = level.getChunkSource();
        int remaining = budget;
        while (remaining > 0 && !job.pending.isEmpty()) {
            int slot = job.pending.nextSetBit(0);
            int chunkX = job.chunkMinX + slot % job.chunkWidth;
            int chunkZ = job.chunkMinZ + slot / job.chunkWidth;
            if (chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true) == null) {
                break;
            }
            SchematicPaster.Slice slice = SchematicPaster.slice(job.schematic, job.min, chunkX, chunkZ);
            if (slice.total() <= 0) {
                job.pending.clear(slot);
                job.chunkCursor = 0;
                continue;
            }
            if (job.chunkStage == STAGE_START) {
                fillFoundation(level, job, chunkX, chunkZ);
                job.chunkStage = STAGE_CLEAR;
                job.chunkCursor = 0;
            }
            if (job.chunkStage == STAGE_CLEAR) {
                int cleared = SchematicPaster.clear(level, job.schematic, job.min, slice, job.chunkCursor, remaining);
                remaining -= cleared - job.chunkCursor;
                job.chunkCursor = cleared;
                if (cleared < slice.total()) {
                    break;
                }
                job.chunkStage = STAGE_PASTE;
                job.chunkCursor = 0;
            }
            int pasted = SchematicPaster.paste(level, job.schematic, job.min, slice, job.chunkCursor, remaining);
            remaining -= pasted - job.chunkCursor;
            job.chunkCursor = pasted;
            if (pasted >= slice.total()) {
                job.pending.clear(slot);
                job.chunkStage = STAGE_START;
                job.chunkCursor = 0;
            }
        }
        if (!job.pending.isEmpty()) {
            return remaining;
        }
        RandomSource random = level.getRandom();
        placeMarkers(level, job, random);
        scatterDebris(level, job, random);
        if (!site.suppliesPlaced()) {
            site.setSuppliesPlaced(true);
            data.setDirty();
            RuinLoot.placeSupplyChests(level, job.min, job.schematic.width(), job.schematic.height(),
                    job.schematic.depth(), job.baseY, random, level.getDifficulty());
        }
        discardTrappedItems(level, job);
        RuinGarrison.onPasted(level, site, data, job.schematic.width(), job.schematic.height(),
                job.schematic.depth(), job.min, job.baseY);
        /* 成功落地才登记间距, 死遗迹也保留这条登记 */
        RuinSites.remember(level.dimension(), site.origin());
        JOBS.remove(new JobKey(level.dimension(), site.origin()));
        TACZEnhancedPatch.LOGGER.info("[ruin] pasted {} at {}", job.schematic.id(), job.min);
        return remaining;
    }

    private static void fillFoundation(ServerLevel level, Job job, int chunkX, int chunkZ) {
        int minX = job.min.getX();
        int minZ = job.min.getZ();
        int startX = Math.max(chunkX << 4, minX);
        int endX = Math.min((chunkX << 4) + 15, minX + job.schematic.width() - 1);
        int startZ = Math.max(chunkZ << 4, minZ);
        int endZ = Math.min((chunkZ << 4) + 15, minZ + job.schematic.depth() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (surface >= job.baseY) {
                    continue;
                }
                int bottom = Math.max(surface, job.baseY - MAX_FOUNDATION);
                for (int y = bottom; y < job.baseY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    level.setBlock(cursor, foundation(level.getRandom()), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void discardTrappedItems(ServerLevel level, Job job) {
        int minX = job.min.getX();
        int minZ = job.min.getZ();
        AABB area = new AABB(minX, job.baseY, minZ,
                minX + job.schematic.width(), job.baseY + job.schematic.height(),
                minZ + job.schematic.depth());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (!level.getBlockState(item.blockPosition()).isAir()) {
                item.discard();
            }
        }
    }

    private static void placeMarkers(ServerLevel level, Job job, RandomSource random) {
        BlockPos center = job.min.offset(job.schematic.width() / 2, 0, job.schematic.depth() / 2);
        BlockPos chestPos = findInterior(level, center, 12, job.baseY);
        if (chestPos == null) {
            chestPos = new BlockPos(center.getX(), job.baseY, center.getZ());
        }
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(LOOT_TABLE, random.nextLong());
        }
    }

    private static BlockPos findInterior(ServerLevel level, BlockPos center, int radius, int baseY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    for (int y = baseY; y <= baseY + 3; y++) {
                        cursor.set(x, y - 1, z);
                        if (!level.getBlockState(cursor).isSolidRender(level, cursor)) {
                            continue;
                        }
                        cursor.set(x, y, z);
                        if (!level.getBlockState(cursor).isAir()) {
                            continue;
                        }
                        cursor.set(x, y + 1, z);
                        if (!level.getBlockState(cursor).isAir()) {
                            continue;
                        }
                        if (!hasCeiling(level, x, y + 2, z)) {
                            continue;
                        }
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasCeiling(ServerLevel level, int x, int fromY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = fromY; y <= fromY + 6; y++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).isSolidRender(level, cursor)) {
                return true;
            }
        }
        return false;
    }

    private static void scatterDebris(ServerLevel level, Job job, RandomSource random) {
        BlockPos center = job.min.offset(job.schematic.width() / 2, 0, job.schematic.depth() / 2);
        int halfX = job.schematic.width() / 2 + 3;
        int halfZ = job.schematic.depth() / 2 + 3;
        int count = 6 + random.nextInt(7);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * halfX);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * halfZ);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, job.baseY, z));
            if (Math.abs(surface.getY() - job.baseY) > 4) {
                continue;
            }
            level.setBlock(surface, foundation(level.getRandom()), Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState foundation(RandomSource random) {
        return FOUNDATION[random.nextInt(FOUNDATION.length)];
    }

    private static final class Job {
        private final RuinSchematic schematic;
        private final BlockPos min;
        private final int baseY;
        private final int chunkMinX;
        private final int chunkMinZ;
        private final int chunkWidth;
        private final BitSet pending = new BitSet();
        private final Map<Long, BlockState> snapshot = new HashMap<>();
        private int chunkStage = STAGE_START;
        private int chunkCursor;
        private long startedAt;

        private Job(RuinSchematic schematic, BlockPos min, int baseY) {
            this.schematic = schematic;
            this.min = min;
            this.baseY = baseY;
            this.chunkMinX = min.getX() >> 4;
            this.chunkMinZ = min.getZ() >> 4;
            int maxChunkX = (min.getX() + schematic.width() - 1) >> 4;
            int maxChunkZ = (min.getZ() + schematic.depth() - 1) >> 4;
            this.chunkWidth = maxChunkX - this.chunkMinX + 1;
            int chunkCount = this.chunkWidth * (maxChunkZ - this.chunkMinZ + 1);
            this.pending.set(0, chunkCount);
        }
    }
}
