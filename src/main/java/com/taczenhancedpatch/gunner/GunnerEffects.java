package com.taczenhancedpatch.gunner;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * 枪手开火的表现层。
 * 上游模组的枪口火光只随第一人称本地玩家的枪械模型渲染, 曳光弹也只对带曳光标记的弹药生效,
 * 生物开火时看不到枪口与弹道; 因此由本模组在服务端补一层通用粒子,
 * 让玩家能看清枪口位置与射击方向。粒子只发给附近玩家, 不参与命中判定。
 */
public final class GunnerEffects {
    /* 枪口相对眼睛沿射向前移的距离, 避免粒子落在生物体内 */
    private static final double MUZZLE_OFFSET = 0.6D;
    /* 枪口火焰与枪口烟雾的粒子数量 */
    private static final int FLASH_COUNT = 2;
    private static final int MUZZLE_SMOKE_COUNT = 3;
    /* 弹道烟雾的采样点数量与步长, 覆盖长度约为两者相乘 */
    private static final int TRACER_POINTS = 8;
    private static final double TRACER_STEP = 2.0D;

    private GunnerEffects() {
    }

    public static void shotVisual(Mob mob, float pitch, float yaw) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 muzzle = mob.getEyePosition().add(direction.scale(MUZZLE_OFFSET));
        send(level, ParticleTypes.FLAME, muzzle, FLASH_COUNT, 0.02D, 0.01D);
        send(level, ParticleTypes.SMOKE, muzzle, MUZZLE_SMOKE_COUNT, 0.08D, 0.01D);
        for (int i = 1; i <= TRACER_POINTS; i++) {
            Vec3 point = muzzle.add(direction.scale(i * TRACER_STEP));
            send(level, ParticleTypes.SMOKE, point, 1, 0.0D, 0.0D);
        }
    }

    private static void send(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }
}
