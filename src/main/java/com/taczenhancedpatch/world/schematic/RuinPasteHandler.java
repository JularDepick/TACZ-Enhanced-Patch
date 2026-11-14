package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinGarrison;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 遗迹建筑的分帧粘贴调度。
 * 世界生成线程只登记站点, 真正的方块写入放在服务端 tick:
 * 玩家靠近到设定距离内才开始粘贴, 离开就暂停; 每个 tick 按固定的方块预算推进,
 * 因此大建筑不会在单帧里灌入上万方块, 也不会在没人看的时候占用算力。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinPasteHandler {
    /* 每个 tick 最多写入的方块数, 覆盖式粘贴连空气一起处理 */
    private static final int BLOCKS_PER_TICK = 6000;
    /* 玩家进入该距离内才开始粘贴, 单位方块 */
    private static final double PASTE_DISTANCE = 128.0D;
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
    private static final Map<BlockPos, Job> JOBS = new HashMap<>();

    /* 站点是否正在粘贴: 守军调度据此延后刷新, 避免新方块卡住刚生成的守军 */
    public static boolean isPasting(BlockPos origin) {
        return JOBS.containsKey(origin);
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
        data.drainPending();
        List<RuinSites.Site> sites = data.sites();
        if (sites.isEmpty()) {
            return;
        }
        int budget = BLOCKS_PER_TICK;
        for (int i = 0; i < sites.size() && budget > 0; i++) {
            RuinSites.Site site = sites.get(i);
            if (site.pasted() || !hasPlayerNear(level, site, PASTE_DISTANCE)) {
                continue;
            }
            Job job = JOBS.get(site.origin());
            if (job == null) {
                if (JOBS.size() >= MAX_JOBS) {
                    continue;
                }
                job = createJob(level, site, data);
                if (job == null) {
                    continue;
                }
                JOBS.put(site.origin(), job);
            }
            budget = advance(level, site, job, data, budget);
        }
    }

    private static boolean hasPlayerNear(ServerLevel level, RuinSites.Site site, double distance) {
        double limit = distance * distance;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(site.origin()) <= limit) {
                return true;
            }
        }
        return false;
    }

    private static Job createJob(ServerLevel level, RuinSites.Site site, RuinSites data) {
        String name = site.schematic();
        RuinSchematic schematic = name.isEmpty() ? null : SchematicCatalog.load(name);
        if (schematic == null && !name.isEmpty() && SchematicCatalog.isPending(name)) {
            /* 后台还在解析, 下一 tick 再取 */
            return null;
        }
        if (schematic == null) {
            /*
             * 站点登记的建筑不可用: 文件被删除, 或文件格式不合法。
             * 弃用该文件并换一座可用的建筑, 下一 tick 再取替代文件; 连替代都没有时才放弃本座遗迹。
             */
            if (!replace(level, site, data, name)) {
                site.setPasted(true);
                data.setDirty();
            }
            return null;
        }
        int minX = site.origin().getX() - schematic.width() / 2;
        int minZ = site.origin().getZ() - schematic.depth() / 2;
        return new Job(schematic, new BlockPos(minX, site.baseY(), minZ), site.baseY());
    }

    /* 把站点换成另一座可用的建筑, 返回是否换成功 */
    private static boolean replace(ServerLevel level, RuinSites.Site site, RuinSites data, String failed) {
        Optional<String> picked = SchematicCatalog.pickOther(failed, level.getRandom());
        if (picked.isEmpty()) {
            TACZEnhancedPatch.LOGGER.error("[ruin] no usable blueprint for site at {}, ruin skipped",
                    site.origin());
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
            if (chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                /* 区块还没加载, 留到下一 tick, 不阻塞其他站点 */
                break;
            }
            SchematicPaster.Slice slice = SchematicPaster.slice(job.schematic, job.min, chunkX, chunkZ);
            if (slice.total() <= 0) {
                job.pending.clear(slot);
                job.chunkCursor = 0;
                continue;
            }
            if (job.chunkStage == STAGE_START) {
                /* 先补地基再清空: 清空会拉低高度图, 之后就没法判断哪里需要补地基 */
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
        /* 物资箱: 建筑内部各层地面按概率散布, 概率随生成时的难度缩放 */
        RuinLoot.placeSupplyChests(level, job.min, job.schematic.width(), job.schematic.height(),
                job.schematic.depth(), job.baseY, random, level.getDifficulty());
        discardTrappedItems(level, job);
        RuinGarrison.onPasted(level, site, data, job.schematic.width(), job.schematic.height(),
                job.schematic.depth(), job.min, job.baseY);
        JOBS.remove(site.origin());
        TACZEnhancedPatch.LOGGER.info("[ruin] pasted {} at {}", job.schematic.id(), job.min);
        return remaining;
    }

    /* 地基补齐: 建筑底面以下的空洞填成石头系方块, 避免建筑悬空 */
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

    /*
     * 清掉被压在建筑方块里的掉落物。
     * 清空预选区与写入方块都不会产生掉落, 但之前卡在方块中窒息而死的守军会留下战利品,
     * 这些物品玩家看不到也捡不到, 一并回收, 避免遗迹内部凭空出现物品。
     */
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

    /* 宝箱: 在建筑内部找有地有顶的空位, 找不到时退回中心点 */
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

    /* 一座建筑的分帧状态, 只在内存里维护, 重启后按未完成站点重贴一次, 写入本身是幂等的 */
    private static final class Job {
        private final RuinSchematic schematic;
        private final BlockPos min;
        private final int baseY;
        private final int chunkMinX;
        private final int chunkMinZ;
        private final int chunkWidth;
        private final BitSet pending = new BitSet();
        /* 当前区块的处理阶段: 起步, 清空, 粘贴 */
        private int chunkStage = STAGE_START;
        /* 当前阶段已经处理到的游标, 换阶段或换区块时归零 */
        private int chunkCursor;

        private Job(RuinSchematic schematic, BlockPos min, int baseY) {
            this.schematic = schematic;
            this.min = min;
            this.baseY = baseY;
            this.chunkMinX = min.getX() >> 4;
            this.chunkMinZ = min.getZ() >> 4;
            int chunkMaxX = (min.getX() + schematic.width() - 1) >> 4;
            int chunkMaxZ = (min.getZ() + schematic.depth() - 1) >> 4;
            this.chunkWidth = chunkMaxX - this.chunkMinX + 1;
            this.pending.set(0, this.chunkWidth * (chunkMaxZ - this.chunkMinZ + 1));
        }
    }
}
