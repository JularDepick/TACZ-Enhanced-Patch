package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinGarrison;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 遗迹站点的接线。
 * 世界生成只写方块, 站点数据(领地半径, 守军基数, 旗帜)在区块加载后由这里补齐:
 * 按结构起点里的片段重建站点, 已登记的直接跳过, 因此崩溃或中途退出都能自愈, 不会重复建守军。
 * 建筑跨多个区块时, 其余区块随玩家靠近自然生成; 这里绝不主动加载区块,
 * 在服务端线程上强制加载会连带生成整片邻域, 把 tick 与存档一起卡死。
 * 物资箱内容按箱子所在区块补填: 位置由蓝图与片段种子决定, 每次接线重算一遍,
 * 已填过的箱子因为非空被跳过, 因此中途退出或重启后都能补齐。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinSiteBinder {
    private static final ResourceLocation RUIN_ID = new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost");
    /* 待填物资箱: 维度 -> 区块 -> 该区块内的箱子坐标 */
    private static final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> PENDING_SUPPLY = new ConcurrentHashMap<>();
    /* 已排过物资箱的站点, 避免同一座遗迹每加载一个区块就重算一遍位置 */
    private static final Set<String> SUPPLY_SCHEDULED = ConcurrentHashMap.newKeySet();

    private RuinSiteBinder() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Structure structure = structure(level);
        if (structure == null) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        /* 区块加载事件给出的永远是完整区块, 这里直接收窄, 后续不再查任何区块 */
        fillQueuedSupply((LevelChunk) event.getChunk());
        /* 一座高建筑会横跨多个 section, 同一个结构起点只处理一次 */
        Set<StructureStart> starts = new LinkedHashSet<>();
        for (int section = level.getMinSection(); section < level.getMaxSection(); section++) {
            starts.addAll(level.structureManager().startsForStructure(SectionPos.of(chunk.x, section, chunk.z), structure));
        }
        boolean found = false;
        for (StructureStart start : starts) {
            found |= bind(level, start);
        }
        if (!found) {
            RuinSiteIndexManager.onChunkWithoutRuin(level, chunk);
        }
        RuinSiteIndexManager.onChunkLoaded(level, chunk);
    }

    @Nullable
    private static Structure structure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RUIN_ID);
    }

    private static boolean bind(ServerLevel level, StructureStart start) {
        boolean bound = false;
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof RuinPiece ruin)) {
                continue;
            }
            BoundingBox box = ruin.getBoundingBox();
            int width = box.getXSpan();
            int height = box.getYSpan();
            int depth = box.getZSpan();
            BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
            BlockPos center = new BlockPos(box.minX() + width / 2, box.minY(), box.minZ() + depth / 2);
            bound = true;
            RuinSiteIndexManager.onSiteBound(level, center, ruin.schematic(), box);
            /* 蓝图没解析好就先不接线: 否则会立起一座空壳遗迹与一批悬空的守军 */
            if (SchematicCatalog.load(ruin.schematic()) == null) {
                TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} not ready, binding skipped", ruin.schematic());
                continue;
            }
            scheduleSupply(level, ruin, box);
            if (RuinSites.find(level, center) != null) {
                continue;
            }
            RuinSites data = RuinSites.get(level);
            RuinSites.queue(level.dimension(), center, ruin.schematic(), box.minY());
            data.drainPending(level);
            RuinSites.Site site = RuinSites.find(level, center);
            if (site == null) {
                continue;
            }
            RuinGarrison.onPasted(level, site, data, width, height, depth, min, box.minY());
            TACZEnhancedPatch.LOGGER.info("[ruin] site ready at {} ({}x{}x{})", center, width, height, depth);
        }
        return bound;
    }

    /*
     * 物资箱内容: 箱子由世界生成放好, 这里只填空箱。
     * 所在区块已加载的直接填, 没加载的按区块排队, 等那个区块加载时再填。
     */
    private static void scheduleSupply(ServerLevel level, RuinPiece ruin, BoundingBox box) {
        RuinSchematic blueprint = SchematicCatalog.load(ruin.schematic());
        if (blueprint == null) {
            return;
        }
        String siteKey = level.dimension().location() + "@" + box.minX() + "," + box.minY() + "," + box.minZ();
        if (!SUPPLY_SCHEDULED.add(siteKey)) {
            return;
        }
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        for (BlockPos spot : RuinLoot.supplySpots(blueprint, min, ruin.seed(), level.getDifficulty())) {
            LevelChunk loaded = RuinLoot.loadedChunk(level, spot);
            if (loaded != null) {
                RuinLoot.fillSupplyChest(loaded, spot, level.getRandom(), level.getDifficulty());
            } else {
                PENDING_SUPPLY.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>())
                        .computeIfAbsent(ChunkPos.asLong(spot.getX() >> 4, spot.getZ() >> 4),
                                key -> new LinkedHashSet<>())
                        .add(spot);
            }
        }
    }

    /* 刚加载的区块由事件直接给出, 填充时不再做任何区块查询 */
    private static void fillQueuedSupply(LevelChunk chunk) {
        ServerLevel level = (ServerLevel) chunk.getLevel();
        Map<Long, Set<BlockPos>> byChunk = PENDING_SUPPLY.get(level.dimension());
        if (byChunk == null) {
            return;
        }
        Set<BlockPos> spots = byChunk.remove(chunk.getPos().toLong());
        if (spots == null) {
            return;
        }
        for (BlockPos spot : spots) {
            RuinLoot.fillSupplyChest(chunk, spot, level.getRandom(), level.getDifficulty());
        }
    }

    public static void clear() {
        PENDING_SUPPLY.clear();
        SUPPLY_SCHEDULED.clear();
    }
}
