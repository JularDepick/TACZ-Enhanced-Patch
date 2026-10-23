package com.taczenhancedpatch.gunner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

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
}
