package com.taczenhancedpatch.world.schematic;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
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

/**
 * 遗迹建筑的分帧粘贴调度。
 * 世界生成线程只登记站点与选中的建筑, 真正的方块写入放到服务端 tick:
 * 每个 tick 只处理若干个已加载的区块, 因此跨区块的大建筑不会撞上世界生成的区块边界,
 * 也不会因为一次性写入几十万方块造成卡顿。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinPasteHandler {
    private static final int CHUNKS_PER_TICK = 3;
    /* 地基补齐的最大深度, 超过部分留空, 避免在悬崖边上灌出石柱 */
    private static final int MAX_FOUNDATION = 24;
    private static final int MAX_JOBS = 8;
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));
    private static final BlockState[] FOUNDATION = {
            Blocks.STONE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
    private static final Map<BlockPos, Job> JOBS = new HashMap<>();

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
        int budget = CHUNKS_PER_TICK;
        for (int i = 0; i < sites.size() && budget > 0; i++) {
            RuinSites.Site site = sites.get(i);
            if (site.pasted()) {
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

    /* 站点里的建筑名在生成时已经定下, 这里只负责加载与摆放原点换算 */
    private static Job createJob(ServerLevel level, RuinSites.Site site, RuinSites data) {
        if (site.schematic().isEmpty()) {
            data.markPasted(site, site.radius());
            return null;
        }
        RuinSchematic schematic = SchematicCatalog.load(site.schematic());
        if (schematic == null) {
            data.markPasted(site, site.radius());
            return null;
        }
        int minX = site.origin().getX() - schematic.width() / 2;
        int minZ = site.origin().getZ() - schematic.depth() / 2;
        return new Job(schematic, new BlockPos(minX, site.baseY(), minZ), site.baseY());
    }

    private static int advance(ServerLevel level, RuinSites.Site site, Job job, RuinSites data, int budget) {
        ServerChunkCache chunks = level.getChunkSource();
        int processed = 0;
        for (int slot = job.pending.nextSetBit(0); slot >= 0 && processed < budget;
             slot = job.pending.nextSetBit(slot + 1)) {
            int chunkX = job.chunkMinX + slot % job.chunkWidth;
            int chunkZ = job.chunkMinZ + slot / job.chunkWidth;
            if (chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                continue;
            }
            /* 先补地基再贴建筑, 否则高度图会把新墙顶当成地表, 漏掉墙下的空洞 */
            fillFoundation(level, job, chunkX, chunkZ);
            SchematicPaster.pasteChunk(level, job.schematic, job.min, chunkX, chunkZ);
            job.pending.clear(slot);
            processed++;
        }
        if (!job.pending.isEmpty()) {
            return budget - processed;
        }
        RandomSource random = level.getRandom();
        placeMarkers(level, job, random);
        scatterDebris(level, job, random);
        data.markPasted(site, Math.max(site.radius(), Math.max(job.schematic.width(), job.schematic.depth()) / 2));
        JOBS.remove(site.origin());
        TACZEnhancedPatch.LOGGER.info("[ruin] pasted {} at {}", job.schematic.id(), job.min);
        return budget - processed;
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

    /* 宝箱与刷怪笼: 在建筑内部找有地有顶的空位, 找不到时退回中心点 */
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

        BlockPos spawnerPos = findInterior(level, center.offset(0, 0, 4), 10, job.baseY);
        if (spawnerPos == null || spawnerPos.equals(chestPos)) {
            spawnerPos = chestPos.offset(3, 0, 0);
        }
        level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 3);
        if (level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(EntityType.ZOMBIE, random);
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
                    /* 建筑可能自带地板, 因此从底面往上试几层, 取第一个有地有顶的空位 */
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
