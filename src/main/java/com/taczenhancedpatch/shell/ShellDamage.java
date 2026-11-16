package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 爆炸杀伤的推进队列。
 * 爆炸瞬间只记录每个目标离爆心多远以及已经算好的伤害与震退, 之后由杀伤面每游戏刻向外推进四格,
 * 杀伤面扫到的目标才真正受伤, 因此杀伤与地形破坏一样是一圈圈铺开的, 不会一瞬间结算完。
 * 推进面只与距离有关, 与地形破坏的圈号同速, 两者观感一致。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class ShellDamage {
    /* 杀伤面每游戏刻向外推进的格数 */
    private static final double FRONT_PER_TICK = 4.0D;
    private static final Map<ResourceKey<Level>, Deque<Job>> JOBS = new HashMap<>();

    private ShellDamage() {
    }

    /* 一个目标: 爆心到它的距离, 已经算好的破甲与常规两段伤害, 以及震退位移 */
    public record Hit(UUID id, double distance, float pierced, float normal,
                      double pushX, double pushY, double pushZ) {
    }

    /* 一次爆炸的杀伤任务: 从爆心开始向外推进, 处理完的目标当场移出待办表 */
    private static final class Job {
        private final ServerLevel level;
        private final Vec3 center;
        @Nullable
        private final Entity owner;
        private final DamageSource pierce;
        private final List<Hit> pending;
        private double front;

        private Job(ServerLevel level, Vec3 center, @Nullable Entity owner, DamageSource pierce, List<Hit> hits) {
            this.level = level;
            this.center = center;
            this.owner = owner;
            this.pierce = pierce;
            this.pending = new ArrayList<>(hits);
        }
    }

    public static void enqueue(ServerLevel level, Vec3 center, @Nullable Entity owner, DamageSource pierce,
                               List<Hit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        JOBS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>()).add(new Job(level, center, owner, pierce, hits));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, Deque<Job>> entry : JOBS.entrySet()) {
            Deque<Job> queue = entry.getValue();
            Job job = queue.peek();
            if (job == null) {
                continue;
            }
            /* 这一 tick 把杀伤面往外推四格, 扫到的目标才结算 */
            job.front += FRONT_PER_TICK;
            for (int index = job.pending.size() - 1; index >= 0; index--) {
                Hit hit = job.pending.get(index);
                if (hit.distance() > job.front) {
                    continue;
                }
                job.pending.remove(index);
                apply(job, hit);
            }
            if (job.pending.isEmpty()) {
                queue.poll();
            }
        }
        JOBS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static void apply(Job job, Hit hit) {
        if (!(job.level.getEntity(hit.id()) instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        /* 破甲部分跳过护甲点数, 两次结算之间清掉无敌帧, 否则第二段会被吞掉 */
        if (hit.pierced() > 0.0F) {
            target.hurt(job.pierce, hit.pierced());
            target.invulnerableTime = 0;
        }
        if (hit.normal() > 0.0F) {
            target.hurt(job.level.damageSources().explosion(null, job.owner), hit.normal());
        }
        if (hit.pushX() != 0.0D || hit.pushY() != 0.0D || hit.pushZ() != 0.0D) {
            target.push(hit.pushX(), hit.pushY(), hit.pushZ());
            target.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}
