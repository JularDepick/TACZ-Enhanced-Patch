package com.taczenhancedpatch.shell;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/*
 * 炮弹爆炸的表现。
 * 全部由服务端生成并广播给周围玩家, 旁观者看到的是同一份效果。
 * 特效随威力缩放: 外壳高度等于水平半径, 边缘叠加四分之一半径以内的波形抖动, 不再是一个整齐的球。
 */
public final class ShellEffects {
    /* 广播半径, 单位方块 */
    private static final double RANGE = 64.0D;
    /* 外壳抖动相对水平半径的比例 */
    private static final double JITTER_RATIO = 0.25D;

    private ShellEffects() {
    }

    public static void burst(ServerLevel level, Vec3 center, int power, ShellSettings settings) {
        if (!settings.effects()) {
            return;
        }
        double radius = settings.radius();
        if (radius <= 0.0D) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.72F, 0.32F), 1.6F);
        send(level, center, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        shell(level, center, radius, dust);
        rays(level, center, radius);
        if (power >= 8) {
            mushroom(level, center, radius, dust);
        }
    }

    /* 半球外壳: 高度等于水平半径, 每点再往外抖 0 到 四分之一半径 */
    private static void shell(ServerLevel level, Vec3 center, double radius, DustParticleOptions dust) {
        int points = 24 + Mth.ceil(radius * 8.0D);
        double jitter = radius * JITTER_RATIO;
        for (int i = 0; i < points; i++) {
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double up = level.getRandom().nextDouble();
            double shell = radius + level.getRandom().nextDouble() * jitter;
            double horizontal = shell * Math.sqrt(Math.max(0.0D, 1.0D - up * up));
            Vec3 at = new Vec3(center.x + Math.cos(theta) * horizontal, center.y + shell * up,
                    center.z + Math.sin(theta) * horizontal);
            send(level, center, dust, at, 2, 0.12D, 0.12D, 0.12D, 0.01D);
            if (i % 3 == 0) {
                send(level, center, ParticleTypes.LARGE_SMOKE, at, 1, 0.08D, 0.04D, 0.08D, 0.01D);
            }
        }
    }

    /* 由中心向外的光线, 长度随半径 */
    private static void rays(ServerLevel level, Vec3 center, double radius) {
        int count = 10 + Mth.ceil(radius);
        for (int i = 0; i < count; i++) {
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double up = level.getRandom().nextDouble() * 0.8D;
            double length = radius * (0.6D + level.getRandom().nextDouble() * 0.4D);
            for (int step = 1; step <= 4; step++) {
                double distance = length * step / 4.0D;
                Vec3 at = new Vec3(center.x + Math.cos(theta) * distance * (1.0D - up * 0.3D),
                        center.y + distance * up * 0.6D,
                        center.z + Math.sin(theta) * distance * (1.0D - up * 0.3D));
                send(level, center, ParticleTypes.END_ROD, at, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /* 蘑菇云随半径缩放: 柱高约半径, 伞盖半径约半径的一半 */
    private static void mushroom(ServerLevel level, Vec3 center, double radius, DustParticleOptions dust) {
        int layers = 3 + Mth.ceil(radius * 0.4D);
        for (int layer = 0; layer < layers; layer++) {
            Vec3 at = new Vec3(center.x, center.y + radius * layer / layers, center.z);
            send(level, center, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 4, 0.3D, 0.1D, 0.3D, 0.02D);
            send(level, center, dust, at, 2, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        double capY = center.y + radius;
        for (int ring = 0; ring < 2; ring++) {
            double capRadius = radius * (0.3D + ring * 0.2D);
            int count = 10 + Mth.ceil(capRadius * 3.0D);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                Vec3 at = new Vec3(center.x + Math.cos(angle) * capRadius, capY + ring * 0.4D,
                        center.z + Math.sin(angle) * capRadius);
                send(level, center, ParticleTypes.LARGE_SMOKE, at, 1, 0.0D, 0.02D, 0.0D, 0.01D);
            }
        }
    }

    /* 只发给爆炸附近能看到效果的玩家, 不依赖原版 32 格的默认广播距离 */
    private static void send(ServerLevel level, Vec3 origin, ParticleOptions type, Vec3 at, int count,
                             double dx, double dy, double dz, double speed) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(type, false,
                at.x, at.y, at.z, (float) dx, (float) dy, (float) dz, (float) speed, count);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) <= RANGE * RANGE) {
                player.connection.send(packet);
            }
        }
    }
}
