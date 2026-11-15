package com.taczenhancedpatch.grenade;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/*
 * 手雷爆炸的表现。
 * 开启特效时炸点向外放出一圈光线射线, 威力拉满时额外给一朵小型蘑菇云;
 * 颜色取自手雷当前配色, 只影响观感。
 */
public final class GrenadeEffects {
    private static final int RAYS = 12;
    private static final int RAY_POINTS = 6;
    private static final int MUSHROOM_LAYERS = 5;

    private GrenadeEffects() {
    }

    public static void burst(ServerLevel level, Vec3 center, int power, GrenadeSettings settings) {
        if (!settings.effects()) {
            return;
        }
        int rgb = settings.color().getFireworkColor();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), 1.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(dust, center.x, center.y, center.z, 24, 0.4D, 0.4D, 0.4D, 0.08D);
        /* 光线射线: 从炸点向外按方位铺开, 距离随威力增长 */
        double rayLength = Mth.clamp(power, 1, GrenadeSettings.POWER_MAX) * 0.6D;
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = Math.PI * 2.0D * ray / RAYS;
            for (int point = 1; point <= RAY_POINTS; point++) {
                double distance = rayLength * point / RAY_POINTS;
                level.sendParticles(ParticleTypes.END_ROD,
                        center.x + Math.cos(angle) * distance,
                        center.y + 0.2D + distance * 0.15D,
                        center.z + Math.sin(angle) * distance,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (power >= GrenadeSettings.POWER_MAX) {
            mushroom(level, center, dust);
        }
    }

    /* 小型蘑菇云: 先是一根上升的柱, 柱顶再铺开两层伞盖 */
    private static void mushroom(ServerLevel level, Vec3 center, DustParticleOptions dust) {
        for (int layer = 0; layer < MUSHROOM_LAYERS; layer++) {
            double y = center.y + layer * 0.9D;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, y, center.z,
                    6, 0.35D, 0.1D, 0.35D, 0.02D);
            level.sendParticles(dust, center.x, y, center.z, 4, 0.3D, 0.1D, 0.3D, 0.01D);
        }
        double capY = center.y + MUSHROOM_LAYERS * 0.9D;
        for (int ring = 0; ring < 2; ring++) {
            double radius = 1.6D + ring * 1.4D;
            int count = 10 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        center.x + Math.cos(angle) * radius,
                        capY + ring * 0.4D,
                        center.z + Math.sin(angle) * radius,
                        1, 0.0D, 0.02D, 0.0D, 0.01D);
            }
        }
    }
}
