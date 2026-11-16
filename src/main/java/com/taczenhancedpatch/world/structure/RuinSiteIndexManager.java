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
    /* 指南针一次查询最多扫多少格环, 约五千格半径; 更远的遗迹靠玩家移动时逐步扩展索引 */
    public static final int QUERY_RADIUS = 10;
    /* 单次查询最多评估多少格, 超出就回一条范围外未探明, 避免一次右键占用太久 */
    private static final int MAX_CELLS_PER_QUERY = 512;
    /* 单次查询的超时时间, 三秒还没结果就判定失败 */
    private static final int QUERY_TIMEOUT_TICKS = 60;
    /* 以玩家所在格为中心向外预热的格数, 每边 1 格 */
    private static final int EXPAND_RADIUS = 1;
    /*
     * 每 tick 的索引预算。
     * 以时间为准, 采样次数只作兜底上限: 一格最多 82 次整列噪声采样, 只按次数记账时一次展开就能吃掉好几个 tick。
     */
    private static final long BUDGET_NANOS = 2_000_000L;
    /* 指南针查询的预算比后台预热宽一些: 一次右键要等结果, 后台可以慢慢来 */
    private static final long JOB_BUDGET_NANOS = 4_000_000L;
    private static final int SAMPLE_CAP_PER_TICK = 4096;
    /* 每 tick 最多检查几项建筑是否已经铺齐 */
    private static final int COMPLETENESS_PER_TICK = 4;
    /* 补齐检查的最大重试次数, 超过后不再追, 索引停在已生成但未铺齐 */
    private static final int COMPLETENESS_ATTEMPTS = 60;
    private static final int SAVE_INTERVAL_TICKS = 1200;
    private static final String KEY = "item.tacz_enhanced_patch.ruin_compass";
    private static final ResourceKey<Structure> RUIN_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(TACZEnhancedPatch.MOD_ID, "ruined_outpost"));

    private static final Map<ResourceKey<Level>, RuinSiteIndex> INDEXES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Deque<Long>> QUEUES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> QUEUED = new HashMap<>();
    private static final Map<ResourceKey<Level>, Deque<Completeness>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> PENDING_CELLS = new HashMap<>();
    private static final Map<ResourceKey<Level>, PlacementEntry> PLACEMENTS = new HashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static int ticks;
    private static boolean stopping;

    /* 维度缓存: 结构集列表换了实例说明数据包重载过, 缓存的选址参数随之失效 */
    private record PlacementEntry(List<Holder<StructureSet>> sets, RandomSpreadStructurePlacement placement) {
    }

    private RuinSiteIndexManager() {
    }

    /* 指南针的查询入口: 先查索引, 查不到就开一个扩展任务 */
    public static void query(ServerPlayer player, ItemStack stack, BiConsumer<ItemStack, BlockPos> onFound) {
        ServerLevel level = player.serverLevel();
        /* 限流: 上一次查询还没出结果时静默拒绝, 不给任何提示, 免得刷屏 */
        if (JOBS.containsKey(player.getUUID())) {
            return;
        }
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
                player.blockPosition().immutable(), stack, onFound, level.getGameTime()));
        player.displayClientMessage(Component.translatable(KEY + ".searching")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /*
     * 当前维度真正生效的选址参数, 取不到说明这个维度不会生成遗迹。
     * 结果按维度缓存: 结构集列表换成新实例时缓存失效, 因此数据包重载后仍取到新的参数。
     */
    @Nullable
    public static RandomSpreadStructurePlacement placement(ServerLevel level) {
        List<Holder<StructureSet>> sets = level.getChunkSource().getGeneratorState().possibleStructureSets();
        PlacementEntry cached = PLACEMENTS.get(level.dimension());
        if (cached != null && cached.sets() == sets) {
            return cached.placement();
        }
        for (Holder<StructureSet> holder : sets) {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                if (entry.structure().is(RUIN_KEY)) {
                    PLACEMENTS.put(level.dimension(), new PlacementEntry(sets, spread));
                    return spread;
                }
            }
        }
        PLACEMENTS.remove(level.dimension());
        return null;
    }

    /* 供发现任务使用: 当前维度的索引, 取不到说明这个维度不会生成遗迹 */
    @Nullable
    public static RuinSiteIndex indexFor(ServerLevel level) {
        RandomSpreadStructurePlacement placement = placement(level);
        return placement == null ? null : index(level, placement);
    }

    /* 开档时把各维度的索引读进来: 读盘只发生在这里, 不落在区块加载或存盘路径上 */
    public static void preload(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            indexFor(level);
        }
    }

    /* 以玩家所在格为中心向外预热若干格, 与查询任务共用每 tick 预算 */
    public static void enqueueCells(ServerLevel level, int cellX, int cellZ, int radius) {
        if (!ServerConfig.ruinsEnabled()) {
            return;
        }
        RandomSpreadStructurePlacement placement = placement(level);
        if (placement == null) {
            return;
        }
        RuinSiteIndex index = index(level, placement);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                enqueue(level.dimension(), index.cellKey(cellX + dx, cellZ + dz));
            }
        }
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
        /* 同一座遗迹的每个区块都会调到这里, 按格去重, 否则待检查列表会随区块数线性膨胀 */
        Set<Long> queued = PENDING_CELLS.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        if (!queued.add(index.cellKey(cellX, cellZ))) {
            return;
        }
        PENDING.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>())
                .add(new Completeness(cellX, cellZ, box));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stopping) {
            return;
        }
        ticks++;
        MinecraftServer server = event.getServer();
        /* 查询超时: 三秒还没出结果就提示查询失败并放开限流, 玩家可以再试一次 */
        for (Job job : List.copyOf(JOBS.values())) {
            if (server.getTickCount() - job.startTick < QUERY_TIMEOUT_TICKS) {
                continue;
            }
            JOBS.remove(job.player);
            ServerPlayer player = server.getPlayerList().getPlayer(job.player);
            if (player == null) {
                continue;
            }
            /* 超时也要给个结论: 索引里已有遗迹就报最近的, 一个都没有才说查询失败 */
            ServerLevel level = server.getLevel(job.dimension);
            RuinSiteIndex index = level == null ? null : indexFor(level);
            RuinSiteIndex.Hit hit = index == null ? null : index.nearest(level, job.origin, QUERY_RADIUS);
            if (hit != null) {
                respond(player, job.stack, hit, job.onFound);
            } else {
                notice(player, "failed");
            }
        }
        /* 索引的读盘由 RuinSiteDiscovery 在开档时完成, 这里不再承担预热 */
        if (!JOBS.isEmpty()) {
            RuinStructure.SampleCounter jobCounter = new RuinStructure.SampleCounter(JOB_BUDGET_NANOS);
            for (Job job : List.copyOf(JOBS.values())) {
                if (jobCounter.expired()) {
                    break;
                }
                stepJob(server, job, jobCounter);
            }
        }
        if (!QUEUES.isEmpty()) {
            RuinStructure.SampleCounter queueCounter = new RuinStructure.SampleCounter(BUDGET_NANOS);
            for (ResourceKey<Level> dimension : List.copyOf(QUEUES.keySet())) {
                if (queueCounter.expired()) {
                    break;
                }
                drainQueue(server, dimension, queueCounter);
            }
        }
        drainCompleteness(server);
        if (ticks % SAVE_INTERVAL_TICKS == 0) {
            saveAll(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stopping = true;
        /* 停止时同步落盘, 后台写线程可能来不及跑完 */
        for (Map.Entry<ResourceKey<Level>, RuinSiteIndex> entry : INDEXES.entrySet()) {
            ServerLevel level = event.getServer().getLevel(entry.getKey());
            if (level != null) {
                entry.getValue().saveNow(level);
            }
        }
        clear();
    }

    public static void clear() {
        INDEXES.clear();
        QUEUES.clear();
        QUEUED.clear();
        PENDING.clear();
        PENDING_CELLS.clear();
        PLACEMENTS.clear();
        JOBS.clear();
        ticks = 0;
        stopping = false;
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
        while (!counter.expired() && counter.value() < SAMPLE_CAP_PER_TICK) {
            Long key = queue.poll();
            if (key == null) {
                break;
            }
            if (queued != null) {
                queued.remove(key);
            }
            if (!index.evaluate(level, generator, randomState, (int) (key >> 32), key.intValue(), counter)) {
                /* 预算在格内耗尽: 这一格放回队首, 下一 tick 接着算 */
                queue.addFirst(key);
                if (queued != null) {
                    queued.add(key);
                }
                break;
            }
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
        while (!counter.expired() && counter.value() < SAMPLE_CAP_PER_TICK) {
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
            if (!index.evaluate(level, generator, randomState, (int) (key >> 32), (int) key, counter, true)) {
                /* 预算在格内耗尽: 游标不动, 下一 tick 从同一格继续 */
                return;
            }
            job.cursor++;
            if (++job.cells > MAX_CELLS_PER_QUERY) {
                notice(player, "range");
                JOBS.remove(job.player);
                return;
            }
        }
    }

    /* 补齐检查按 tick 预算推进: 键化去重, 每 tick 只看固定几项, 不再随区块加载全量扫描 */
    private static void drainCompleteness(MinecraftServer server) {
        for (ResourceKey<Level> dimension : List.copyOf(PENDING.keySet())) {
            Deque<Completeness> queue = PENDING.get(dimension);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            ServerLevel level = server.getLevel(dimension);
            RandomSpreadStructurePlacement placement = level == null ? null : placement(level);
            if (level == null || placement == null) {
                queue.clear();
                Set<Long> stale = PENDING_CELLS.get(dimension);
                if (stale != null) {
                    stale.clear();
                }
                continue;
            }
            RuinSiteIndex index = index(level, placement);
            Set<Long> queued = PENDING_CELLS.computeIfAbsent(dimension, key -> new HashSet<>());
            int budget = COMPLETENESS_PER_TICK;
            while (budget > 0 && !queue.isEmpty()) {
                Completeness item = queue.poll();
                budget--;
                long key = index.cellKey(item.cellX, item.cellZ);
                if (!complete(level, item.box)) {
                    item.attempts++;
                    if (item.attempts < COMPLETENESS_ATTEMPTS) {
                        queue.add(item);
                    } else {
                        queued.remove(key);
                    }
                    continue;
                }
                index.markGenerated(item.cellX, item.cellZ);
                queued.remove(key);
            }
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
        String cleaned = displayName(hit.name());
        Component name = cleaned.isEmpty()
                ? Component.translatable(KEY + ".name.unknown")
                : Component.literal(cleaned);
        Component status = Component.translatable(stateKey(hit.state()));
        player.displayClientMessage(Component.translatable(KEY + ".result", distance,
                hit.pos().getX(), hit.pos().getY(), hit.pos().getZ(), name, status)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /*
     * 建筑名只留蓝图文件名: 索引里存的是蓝图 id, 可能带目录与 .schem 后缀,
     * 直接显示会把整条路径摆到提示里。老存档里的名字也走同一套清理。
     */
    private static String displayName(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String name = id;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length());
        } else if (name.endsWith(".schem")) {
            name = name.substring(0, name.length() - ".schem".length());
        }
        return name;
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
        private final long startTick;
        private int ring;
        private int cursor;
        /* 本次查询已经评估过的格数, 用于上限判定 */
        private int cells;

        private Job(UUID player, ResourceKey<Level> dimension, BlockPos origin, ItemStack stack,
                    BiConsumer<ItemStack, BlockPos> onFound, long startTick) {
            this.player = player;
            this.dimension = dimension;
            this.origin = origin;
            this.stack = stack;
            this.onFound = onFound;
            this.startTick = startTick;
        }
    }

    /* 一次补齐检查: 重试次数到上限后放弃, 索引停在已生成但未确认铺齐 */
    private static final class Completeness {
        private final int cellX;
        private final int cellZ;
        private final BoundingBox box;
        private int attempts;

        private Completeness(int cellX, int cellZ, BoundingBox box) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.box = box;
        }
    }
}
