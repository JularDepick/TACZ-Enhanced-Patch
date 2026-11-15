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
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 遗迹站点的接线。
 * 世界生成只写方块, 站点数据(领地半径, 守军基数, 旗帜)在区块加载后由这里补齐:
 * 按结构起点里的片段重建站点, 已登记的直接跳过, 因此崩溃或中途退出都能自愈, 不会重复建守军。
 * 建筑跨多个区块时, 起点区块加载后把其余区块按每 tick 一个的节奏补齐, 让遗迹尽快完整。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinSiteBinder {
    private static final ResourceLocation RUIN_ID = new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost");
    private static final Map<ResourceKey<Level>, Deque<ChunkPos>> PENDING = new ConcurrentHashMap<>();
    /* 每 tick 最多补几个区块: 生成是同步的, 一次补一个不会让 tick 明显变长 */
    private static final int CHUNKS_PER_TICK = 1;

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
        for (int section = level.getMinSection(); section < level.getMaxSection(); section++) {
            SectionPos pos = SectionPos.of(chunk.x, section, chunk.z);
            for (StructureStart start : level.structureManager().startsForStructure(pos, structure)) {
                bind(level, start);
            }
        }
    }

    /* 补区块: 只请求还没加载的, 已加载的直接跳过 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Deque<ChunkPos>> entry : PENDING.entrySet()) {
            ServerLevel level = event.getServer().getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            Deque<ChunkPos> queue = entry.getValue();
            int budget = CHUNKS_PER_TICK;
            while (budget > 0) {
                ChunkPos next = queue.poll();
                if (next == null) {
                    break;
                }
                if (level.getChunkSource().hasChunk(next.x, next.z)) {
                    continue;
                }
                level.getChunk(next.x, next.z);
                budget--;
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    @Nullable
    private static Structure structure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RUIN_ID);
    }

    private static void bind(ServerLevel level, StructureStart start) {
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
            fillSupply(level, ruin, box);
            enqueue(level, box);
            TACZEnhancedPatch.LOGGER.info("[ruin] site ready at {} ({}x{}x{})", center, width, height, depth);
        }
    }

    /* 物资箱内容: 箱子已经由世界生成放好, 这里只填空箱, 重复进存档不会重填 */
    private static void fillSupply(ServerLevel level, RuinPiece ruin, BoundingBox box) {
        RuinSchematic blueprint = SchematicCatalog.load(ruin.schematic());
        if (blueprint == null) {
            return;
        }
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        for (BlockPos spot : RuinLoot.supplySpots(blueprint, min, ruin.seed(), level.getDifficulty())) {
            RuinLoot.fillSupplyChest(level, spot, level.getRandom(), level.getDifficulty());
        }
    }

    private static void enqueue(ServerLevel level, BoundingBox box) {
        Deque<ChunkPos> queue = PENDING.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                if (!queue.contains(pos)) {
                    queue.add(pos);
                }
            }
        }
    }
}
