package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 地形破坏的排队执行。
 * 爆炸瞬间只算出要破坏哪些方块, 按扁球归一化距离分圈, 由内向外每游戏刻推进一圈, 每圈带一声击碎音效;
 * 单圈过大时拆成小批, 避免一个 tick 里改上千个方块。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class ShellBreaker {
    /* 单批方块数上限, 超过就拆到下一 tick */
    private static final int BATCH_SIZE = 512;
    /* 每个维度最多排队的批次: 连续爆炸时宁可少炸一些, 也不让队列与内存无限增长 */
    private static final int MAX_BATCHES = 32;
    /* 破坏半径在杀伤半径之外再加的比例上限, 实际取 [0, r/4] 之间的波浪边缘 */
    private static final double EDGE_WAVE_RATIO = 0.25D;
    private static final Map<ResourceKey<Level>, Deque<List<Planned>>> JOBS = new HashMap<>();

    private ShellBreaker() {
    }

    /* 排队时记下当时的方块状态, 执行时方块变了就跳过, 免得删掉玩家后来放下的东西 */
    private record Planned(BlockPos pos, BlockState state) {
    }

    public static void enqueue(ServerLevel level, Vec3 center, double radius, int power) {
        Deque<List<Planned>> queue = JOBS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        if (queue.size() >= MAX_BATCHES) {
            return;
        }
        /* 破坏半径 = 杀伤半径 + [0, r/4] 的波浪边缘, 爆坑边界因此不会是一条整齐的球面 */
        double limit = radius * (1.0D + EDGE_WAVE_RATIO);
        int range = Mth.ceil(limit);
        BlockPos origin = BlockPos.containing(center);
        /* 圈号由扁球归一化距离决定, 圈与圈之间相差一格 */
        List<List<Planned>> rings = new ArrayList<>();
        for (int ring = 0; ring <= range + 1; ring++) {
            rings.add(new ArrayList<>());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            double normalized = ShellExplosion.shape(center, Vec3.atCenterOf(pos),
                    radius * (1.0D + EDGE_WAVE_RATIO * edgeWave(pos)));
            if (normalized > 1.0D) {
                continue;
            }
            /* 未加载区块不读不写: 服务端线程上的强制加载会连带生成整片邻域 */
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            float hardness = state.getBlock().defaultDestroyTime();
            if (hardness < 0.0F) {
                continue;
            }
            /* 与实体伤害同一口径: 边缘只剩一半威力 */
            double reduced = power * (1.0D - 0.5D * normalized);
            if (hardness > reduced) {
                continue;
            }
            int ring = Mth.clamp((int) Math.ceil(normalized * limit), 0, rings.size() - 1);
            rings.get(ring).add(new Planned(pos.immutable(), state));
        }
        for (List<Planned> ring : rings) {
            for (int from = 0; from < ring.size(); from += BATCH_SIZE) {
                if (queue.size() >= MAX_BATCHES) {
                    return;
                }
                queue.add(new ArrayList<>(ring.subList(from, Math.min(ring.size(), from + BATCH_SIZE))));
            }
        }
    }

    /*
     * 边缘扰动: 两路不同频率的正弦叠加, 取值 [0, 1]。
     * 只与方块坐标有关, 因此同一发炮弹每次算出的边界完全一致, 不会出现闪烁的锯齿。
     */
    private static double edgeWave(BlockPos pos) {
        double slow = Math.sin(pos.getX() * 0.61D + pos.getY() * 1.27D) * Math.cos(pos.getZ() * 0.83D - pos.getY() * 0.47D);
        double fast = Math.sin(pos.getX() * 1.73D - pos.getZ() * 1.51D + pos.getY() * 0.29D);
        return Mth.clamp(0.5D + 0.35D * slow + 0.15D * fast, 0.0D, 1.0D);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Deque<List<Planned>>> entry : JOBS.entrySet()) {
            ServerLevel level = event.getServer().getLevel(entry.getKey());
            Deque<List<Planned>> queue = entry.getValue();
            if (level == null) {
                queue.clear();
                continue;
            }
            List<Planned> batch = queue.poll();
            if (batch == null) {
                continue;
            }
            boolean broken = false;
            for (Planned planned : batch) {
                BlockState current = level.getBlockState(planned.pos());
                if (current != planned.state()) {
                    continue;
                }
                level.removeBlock(planned.pos(), false);
                broken = true;
            }
            if (broken) {
                BlockPos at = batch.get(0).pos();
                level.playSound(null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                        SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            }
        }
        JOBS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}
