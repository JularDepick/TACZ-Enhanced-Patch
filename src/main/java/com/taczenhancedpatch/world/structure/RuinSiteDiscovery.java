package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.faction.RuinTerritory;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * 遗迹站点的发现与物化。
 * 区块加载事件里一个订阅者都不留: 站点只在玩家靠近时按需物化,
 * 候选区块由选址索引的纯数学给出, 取区块一律先 hasChunkAt 再 getChunkNow,
 * 因此这里既不会隐式加载区块, 也不会把开销压到区块加载与存盘的关键路径上。
 * 物化幂等: 同一座遗迹重复调用得到同一个站点, 已登记的站点直接跳过。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinSiteDiscovery {
    private static final ResourceLocation RUIN_ID = new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost");
    /* 发现周期, 与守军调度同频 */
    private static final int INTERVAL = 20;
    /* 每 tick 的常数预算: 结构起点查询次数与新物化站点数 */
    private static final int MAX_STARTS = 16;
    private static final int MAX_SITES = 4;
    /* 以玩家所在格为中心向外展开的格数 */
    private static final int EXPAND_CELLS = 1;
    /* 玩家离遗迹中心多远才值得物化 */
    private static final double ACTIVE_MARGIN = 32.0D;

    private static final Map<UUID, Long> LAST_CELL = new HashMap<>();
    private static int tick;
    private static boolean stopping;

    private RuinSiteDiscovery() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        stopping = false;
        RuinSiteIndexManager.preload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stopping = true;
        clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stopping) {
            return;
        }
        if (++tick % INTERVAL != 0) {
            return;
        }
        if (!ServerConfig.ruinsEnabled()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            scan(level);
        }
    }

    public static void clear() {
        LAST_CELL.clear();
        tick = 0;
    }

    private static void scan(ServerLevel level) {
        RuinSiteIndex index = RuinSiteIndexManager.indexFor(level);
        if (index == null) {
            return;
        }
        Structure structure = structure(level);
        if (structure == null) {
            return;
        }
        int starts = 0;
        int created = 0;
        double reach = RuinTerritory.RADIUS_MAX + ACTIVE_MARGIN;
        for (ServerPlayer player : level.players()) {
            BlockPos at = player.blockPosition();
            int cellX = index.cellOf(at.getX());
            int cellZ = index.cellOf(at.getZ());
            long key = index.cellKey(cellX, cellZ);
            Long previous = LAST_CELL.put(player.getUUID(), key);
            if (previous == null || previous.longValue() != key) {
                RuinSiteIndexManager.enqueueCells(level, cellX, cellZ, EXPAND_CELLS);
            }
            for (int dx = -EXPAND_CELLS; dx <= EXPAND_CELLS; dx++) {
                for (int dz = -EXPAND_CELLS; dz <= EXPAND_CELLS; dz++) {
                    if (starts >= MAX_STARTS || created >= MAX_SITES) {
                        return;
                    }
                    ChunkPos candidate = index.candidate(cellX + dx, cellZ + dz);
                    double offsetX = candidate.getMiddleBlockX() + 0.5D - at.getX();
                    double offsetZ = candidate.getMiddleBlockZ() + 0.5D - at.getZ();
                    if (offsetX * offsetX + offsetZ * offsetZ > reach * reach) {
                        continue;
                    }
                    BlockPos center = new BlockPos(candidate.getMiddleBlockX(), at.getY(), candidate.getMiddleBlockZ());
                    if (!level.hasChunkAt(center)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(candidate.x, candidate.z);
                    if (chunk == null) {
                        continue;
                    }
                    starts++;
                    StructureStart start = chunk.getAllStarts().get(structure);
                    if (start == null) {
                        RuinSiteIndexManager.onChunkWithoutRuin(level, candidate);
                        continue;
                    }
                    created += materialize(level, start, MAX_SITES - created);
                }
            }
        }
    }

    /* 把一座结构起点里的遗迹片段物化成站点, 返回本次新建的数量 */
    private static int materialize(ServerLevel level, StructureStart start, int budget) {
        int created = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (created >= budget) {
                return created;
            }
            if (!(piece instanceof RuinPiece ruin)) {
                continue;
            }
            BoundingBox box = ruin.getBoundingBox();
            int width = box.getXSpan();
            int height = box.getYSpan();
            int depth = box.getZSpan();
            BlockPos center = new BlockPos(box.minX() + width / 2, box.minY(), box.minZ() + depth / 2);
            if (RuinSites.find(level, center) != null) {
                RuinSiteIndexManager.onSiteBound(level, center, ruin.schematic(), box);
                continue;
            }
            if (SchematicCatalog.load(ruin.schematic()) == null) {
                TACZEnhancedPatch.LOGGER.debug("[ruin] blueprint {} not ready, discovery skipped", ruin.schematic());
                continue;
            }
            RuinSites.Site site = RuinSites.materialize(level, center, ruin.schematic(), ruin.seed(),
                    box.minY(), width, height, depth);
            if (site == null) {
                continue;
            }
            created++;
            RuinSiteIndexManager.onSiteBound(level, center, ruin.schematic(), box);
            RuinLoot.fillSiteSupplies(level, site);
            TACZEnhancedPatch.LOGGER.debug("[ruin] site materialized at {} ({}x{}x{})", center, width, height, depth);
        }
        return created;
    }

    @Nullable
    private static Structure structure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RUIN_ID);
    }
}
