package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/*
 * 遗迹选址索引的调度。
 * 索引按需扩展: 区块加载时把周围几格排队, 指南针查询时再从玩家所在格向外一环一环补算,
 * 两者共用每 tick 的采样预算, 因此不会卡住 tick。
 * 生成期的真实结果由区块加载回写, 预测与实测的偏差会在这里被纠正。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinSiteIndexManager {
    /* 指南针一次查询最多扫多少格环, 约一万三千格半径 */
    public static final int QUERY_RADIUS = 24;
    /* 区块加载时向外预热的格数, 每边 2 格 */
    private static final int EXPAND_RADIUS = 2;
    /* 每 tick 允许的地表采样次数, 一格最多 82 次, 按需调整 */
    private static final int SAMPLE_BUDGET_PER_TICK = 256;
    private static final int SAVE_INTERVAL_TICKS = 1200;
    private static final String KEY = "item.tacz_enhanced_patch.ruin_compass";
    private static final ResourceKey<Structure> RUIN_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost"));

    private static final Map<ResourceKey<Level>, RuinSiteIndex> INDEXES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Deque<Long>> QUEUES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> QUEUED = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<Completeness>> PENDING = new HashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static int ticks;

    private RuinSiteIndexManager() {
    }

    /* 指南针的查询入口: 先查索引, 查不到就开一个扩展任务 */
    public static void query(ServerPlayer player, ItemStack stack, BiConsumer<ItemStack, BlockPos> onFound) {
        ServerLevel level = player.serverLevel();
        if (!ServerConfig.ruinsEnabled()) {
            notice(player, "disabled");
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            notice(player, "none");
            return;
        }
        RuinSiteIndex index = index(level, placement);
        RuinSiteIndex.Hit hit = index.nearest(level, player.blockPosition(), QUERY_RADIUS);
        if (hit != null) {
            respond(player, stack, hit, onFound);
            return;
        }
        JOBS.put(player.getUUID(), new Job(player.getUUID(), level.dimension(),
                player.blockPosition().immutable(), stack, onFound));
        player.displayClientMessage(Component.translatable(KEY + ".searching")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /* 当前维度真正生效的选址参数, 取不到说明这个维度不会生成遗迹 */
    @Nullable
    public static RandomSpreadStructurePlacement placement(ServerLevel level) {
        for (Holder<StructureSet> holder : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                if (entry.structure().is(RUIN_KEY)) {
                    return spread;
                }
            }
        }
        return null;
    }

    /* 区块加载后预热周围的格, 并顺带补齐正在生成的遗迹 */
    public static void onChunkLoaded(ServerLevel level, ChunkPos pos) {
        if (!ServerConfig.ruinsEnabled()) {
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            return;
        }
        RuinSiteIndex index = index(level, placement);
        int cellX = index.cellOf(pos.getMinBlockX());
        int cellZ = index.cellOf(pos.getMinBlockZ());
        for (int dx = -EXPAND_RADIUS; dx <= EXPAND_RADIUS; dx++) {
            for (int dz = -EXPAND_RADIUS; dz <= EXPAND_RADIUS; dz++) {
                enqueue(level.dimension(), index.cellKey(cellX + dx, cellZ + dz));
            }
        }
        checkCompleteness(level);
    }

    /* 候选区块生成完了却没有遗迹, 把预测纠正为没有 */
    public static void onChunkWithoutRuin(ServerLevel level, ChunkPos pos) {
        if (!ServerConfig.ruinsEnabled()) {
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            return;
        }
        RuinSiteIndex index = index(level, placement);
        int cellX = index.cellOf(pos.getMinBlockX());
        int cellZ = index.cellOf(pos.getMinBlockZ());
        ChunkPos candidate = index.candidate(cellX, cellZ);
        if (candidate.x != pos.x || candidate.z != pos.z) {
            return;
        }
        index.markRejected(cellX, cellZ);
    }

    /* 站点接线完成, 回填建筑名与状态, 并把覆盖区块的补齐检查排进队列 */
    public static void onSiteBound(ServerLevel level, BlockPos center, String name, BoundingBox box) {
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            return;
        }
        RuinSiteIndex index = index(level, placement);
        int cellX = index.cellOf(center.getX());
        int cellZ = index.cellOf(center.getZ());
        index.markBound(cellX, cellZ, center.getY(), name);
        PENDING.computeIfAbsent(level.dimension(), key -> new ArrayList<>())
                .add(new Completeness(cellX, cellZ, box));
        checkCompleteness(level);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        MinecraftServer server = event.getServer();
        if (!JOBS.isEmpty() || !QUEUES.isEmpty()) {
            RuinStructure.SampleCounter counter = new RuinStructure.SampleCounter();
            for (Job job : List.copyOf(JOBS.values())) {
                if (counter.value() >= SAMPLE_BUDGET_PER_TICK) {
                    break;
                }
                stepJob(server, job, counter);
            }
            for (ResourceKey<Level> dimension : List.copyOf(QUEUES.keySet())) {
                if (counter.value() >= SAMPLE_BUDGET_PER_TICK) {
                    break;
                }
                drainQueue(server, dimension, counter);
            }
        }
        if (ticks % SAVE_INTERVAL_TICKS == 0) {
            saveAll(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        saveAll(event.getServer());
        clear();
    }

    public static void clear() {
        INDEXES.clear();
        QUEUES.clear();
        QUEUED.clear();
        PENDING.clear();
        JOBS.clear();
        ticks = 0;
    }

    /* 已生成的遗迹靠区块加载回写, 这里只在需要时逐个维度落盘 */
    private static void saveAll(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, RuinSiteIndex> entry : INDEXES.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null) {
                entry.getValue().save(level);
            }
        }
    }

    private static RuinSiteIndex index(ServerLevel level, RandomSpreadStructurePlacement placement) {
        RuinSiteIndex cached = INDEXES.get(level.dimension());
        if (cached != null && cached.matches(level.getSeed(), placement)) {
            return cached;
        }
        RuinSiteIndex index = RuinSiteIndex.load(level, placement);
        INDEXES.put(level.dimension(), index);
        return index;
    }

    private static void enqueue(ResourceKey<Level> dimension, long key) {
        Set<Long> queued = QUEUED.computeIfAbsent(dimension, k -> new HashSet<>());
        if (!queued.add(key)) {
            return;
        }
        QUEUES.computeIfAbsent(dimension, k -> new ArrayDeque<>()).add(key);
    }

    private static void drainQueue(MinecraftServer server, ResourceKey<Level> dimension,
                                   RuinStructure.SampleCounter counter) {
        ServerLevel level = server.getLevel(dimension);
        Deque<Long> queue = QUEUES.get(dimension);
        if (level == null || queue == null) {
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            queue.clear();
            QUEUED.getOrDefault(dimension, Set.of()).clear();
            return;
        }
        RuinSiteIndex index = index(level, placement);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        Set<Long> queued = QUEUED.get(dimension);
        while (counter.value() < SAMPLE_BUDGET_PER_TICK) {
            Long key = queue.poll();
            if (key == null) {
                break;
            }
            if (queued != null) {
                queued.remove(key);
            }
            index.evaluate(level, generator, randomState, (int) (key >> 32), key.intValue(), counter);
        }
    }

    private static void stepJob(MinecraftServer server, Job job, RuinStructure.SampleCounter counter) {
        ServerLevel level = server.getLevel(job.dimension);
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (level == null || player == null) {
            JOBS.remove(job.player);
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            notice(player, "none");
            JOBS.remove(job.player);
            return;
        }
        RuinSiteIndex index = index(level, placement);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        int cellX = index.cellOf(job.origin.getX());
        int cellZ = index.cellOf(job.origin.getZ());
        while (counter.value() < SAMPLE_BUDGET_PER_TICK) {
            if (job.ring > QUERY_RADIUS) {
                notice(player, "none");
                JOBS.remove(job.player);
                return;
            }
            int size = RuinSiteIndex.ringSize(job.ring);
            if (job.cursor >= size) {
                RuinSiteIndex.Hit hit = index.nearest(level, job.origin, QUERY_RADIUS);
                if (hit != null && job.ring >= hit.ring() + 1) {
                    respond(player, job.stack, hit, job.onFound);
                    JOBS.remove(job.player);
                    return;
                }
                job.ring++;
                job.cursor = 0;
                continue;
            }
            long key = RuinSiteIndex.ringCell(cellX, cellZ, job.ring, job.cursor);
            job.cursor++;
            index.evaluate(level, generator, randomState, (int) (key >> 32), (int) key, counter);
        }
    }

    private static void checkCompleteness(ServerLevel level) {
        List<Completeness> pending = PENDING.get(level.dimension());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            pending.clear();
            return;
        }
        RuinSiteIndex index = index(level, placement);
        Iterator<Completeness> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Completeness item = iterator.next();
            if (!complete(level, item.box())) {
                continue;
            }
            index.markGenerated(item.cellX(), item.cellZ());
            iterator.remove();
        }
    }

    /* 建筑覆盖的区块是否都已经生成 */
    private static boolean complete(ServerLevel level, BoundingBox box) {
        for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void respond(ServerPlayer player, ItemStack stack, RuinSiteIndex.Hit hit,
                                BiConsumer<ItemStack, BlockPos> onFound) {
        onFound.accept(stack, hit.pos());
        int distance = (int) Math.round(Math.sqrt(hit.pos().distSqr(player.blockPosition())));
        Component name = hit.name().isEmpty()
                ? Component.translatable(KEY + ".name.unknown")
                : Component.literal(hit.name());
        Component status = Component.translatable(stateKey(hit.state()));
        player.displayClientMessage(Component.translatable(KEY + ".result", distance,
                hit.pos().getX(), hit.pos().getY(), hit.pos().getZ(), name, status)
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void notice(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable(KEY + "." + suffix)
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static String stateKey(RuinSiteIndex.State state) {
        return switch (state) {
            case GENERATING -> KEY + ".state.generating";
            case GENERATED -> KEY + ".state.done";
            default -> KEY + ".state.pending";
        };
    }

    /* 一次查询的进度: 环号与环内游标, 跨 tick 续算 */
    private static final class Job {
        private final UUID player;
        private final ResourceKey<Level> dimension;
        private final BlockPos origin;
        private final ItemStack stack;
        private final BiConsumer<ItemStack, BlockPos> onFound;
        private int ring;
        private int cursor;

        private Job(UUID player, ResourceKey<Level> dimension, BlockPos origin, ItemStack stack,
                    BiConsumer<ItemStack, BlockPos> onFound) {
            this.player = player;
            this.dimension = dimension;
            this.origin = origin;
            this.stack = stack;
            this.onFound = onFound;
        }
    }

    private record Completeness(int cellX, int cellZ, BoundingBox box) {
    }
}
