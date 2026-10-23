package com.taczenhancedpatch.gunner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个枪手的瞬时战斗状态。
 * 只保存在服务端内存中, 不写入 NBT, 区块卸载后重新计算即可。
 */
final class GunnerState {
    boolean initialized;
    @Nullable
    ResourceLocation gunId;
    @Nullable
    LivingEntity target;
    int retargetTicks;
    int reactionTicks;
    int pauseTicks;
    int burstSize;
    int shotsInBurst;
    int reloadTicks;
    /* 持枪期间被摘下的原版索敌目标, 弹尽时按原优先级装回 */
    boolean targetGoalsRemoved;
    final List<RemovedGoal> removedTargetGoals = new ArrayList<>();

    record RemovedGoal(int priority, Goal goal) {
    }
}
