package com.taczenhancedpatch.grenade;

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
 * 手雷爆炸的表现。
 * 全部由服务端生成并广播给周围玩家, 因此旁观者与回放看到的是同一份效果, 不依赖投掷者本机的开关之外的东西。
 * 颜色与手雷配色无关: 手雷颜色只改物品贴图, 爆炸统一用暖色尘与烟。
 */
public final class GrenadeEffects {
    /* 广播半径, 单位方块 */
    private static final double RANGE = 64.0D;
    private static final int RAYS = 12;
    private static final int RAY_POINTS = 6;
    private static final int MUSHROOM_LAYERS = 5;

    private GrenadeEffects() {
    }

    public static void burst(ServerLevel level, Vec3 center, int power, GrenadeSettings settings) {
        if (!settings.effects()) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.72F, 0.32F), 1.6F);
        send(level, center, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        send(level, center, dust, center, 40, 0.6D, 0.6D, 0.6D, 0.1D);
        double rayLength = Mth.clamp(power, 1, GrenadeSettings.POWER_MAX) * 0.8D;
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = Math.PI * 2.0D * ray / RAYS;
            for (int point = 1; point <= RAY_POINTS; point++) {
                double distance = rayLength * point / RAY_POINTS;
                Vec3 at = new Vec3(center.x + Math.cos(angle) * distance,
                        center.y + 0.2D + distance * 0.15D,
                        center.z + Math.sin(angle) * distance);
                send(level, center, ParticleTypes.END_ROD, at, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (power >= GrenadeSettings.POWER_MAX) {
            mushroom(level, center, dust);
        }
    }

    /* 小型蘑菇云: 一根上升的柱, 柱顶再铺开两层伞盖 */
    private static void mushroom(ServerLevel level, Vec3 center, DustParticleOptions dust) {
        for (int layer = 0; layer < MUSHROOM_LAYERS; layer++) {
            Vec3 at = new Vec3(center.x, center.y + layer * 0.9D, center.z);
            send(level, center, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 8, 0.4D, 0.1D, 0.4D, 0.02D);
            send(level, center, dust, at, 4, 0.3D, 0.1D, 0.3D, 0.01D);
        }
        double capY = center.y + MUSHROOM_LAYERS * 0.9D;
        for (int ring = 0; ring < 2; ring++) {
            double radius = 1.6D + ring * 1.4D;
            int count = 10 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                Vec3 at = new Vec3(center.x + Math.cos(angle) * radius, capY + ring * 0.4D,
                        center.z + Math.sin(angle) * radius);
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
