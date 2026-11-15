package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.faction.Factions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/*
 * 炮弹爆炸。
 * 杀伤空间不是球体而是扁球: 水平半径等于威力, 铅直半径压缩到水平半径的三分之二。
 * 地形按扁球归一化距离分圈, 由内向外每游戏刻破坏一圈并播放击碎音效。
 * 实体伤害按归一化距离线性衰减到零, 震退距离等于水平半径的一半, 边缘同样衰减到零。
 */
public final class ShellExplosion {
    /* 边缘处保留的威力比例: 中心为满值, 边缘为一半 */
    private static final double EDGE_POWER_RATIO = 0.5D;
    /* 铅直半径相对水平半径的比例, 二分之三的扁球 */
    public static final double VERTICAL_RATIO = 2.0D / 3.0D;
    /* 震退距离相对水平半径的比例 */
    private static final double KNOCK_RATIO = 0.5D;
    /* 震退位移上限, 兜住异常半径 */
    private static final double KNOCK_LIMIT = 8.0D;

    private ShellExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, ShellSettings settings, @Nullable Entity owner) {
        int power = settings.power();
        double radius = settings.radius();
        if (settings.target().terrain() && radius > 0.0D) {
            ShellBreaker.enqueue(level, center, radius, power);
        }
        damageEntities(level, center, radius, settings, owner);
        /* 震退与杀伤档位无关: 半径内的玩家与生物都会被推开, 震退本身不造成伤害 */
        knockBack(level, center, radius);
        ShellEffects.burst(level, center, power, settings);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Mth.clamp(power * 0.4F, 1.0F, 6.0F), 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /* 扁球归一化距离, 等于一表示正好落在杀伤边界上 */
    public static double shape(Vec3 center, Vec3 point, double radius) {
        double vertical = radius * VERTICAL_RATIO;
        double dx = point.x - center.x;
        double dz = point.z - center.z;
        double dy = point.y - center.y;
        double horizontal = Math.sqrt(dx * dx + dz * dz) / radius;
        double up = dy / vertical;
        return Math.sqrt(horizontal * horizontal + up * up);
    }

    private static void damageEntities(ServerLevel level, Vec3 center, double radius, ShellSettings settings,
                                       @Nullable Entity owner) {
        if (radius <= 0.0D) {
            return;
        }
        boolean fromPlayer = owner instanceof Player;
        float maxDamage = settings.damage();
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive()) {
                continue;
            }
            if (target instanceof Player player) {
                if (!settings.target().players() || player.isCreative() || player.isSpectator()) {
                    continue;
                }
            } else if (!settings.target().mobs()) {
                continue;
            }
            if (!fromPlayer) {
                if (target == owner) {
                    continue;
                }
                if (owner != null && Factions.allied(owner, target)) {
                    continue;
                }
            }
            double distance = shape(center, target.getBoundingBox().getCenter(), radius);
            if (distance > 1.0D) {
                continue;
            }
            float damage = (float) (maxDamage * (1.0D - distance));
            if (damage <= 0.0F) {
                continue;
            }
            DamageSource source = level.damageSources().explosion(null, owner);
            target.hurt(source, damage);
        }
    }

    /*
     * 震退: 位移等于水平半径的一半, 中心最远, 边缘归零;
     * 只推玩家与生物, 不区分杀伤档位, 也不附带任何伤害。
     */
    private static void knockBack(ServerLevel level, Vec3 center, double radius) {
        if (radius <= 0.0D) {
            return;
        }
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive() || target.isSpectator()) {
                continue;
            }
            double distance = shape(center, target.getBoundingBox().getCenter(), radius);
            if (distance > 1.0D) {
                continue;
            }
            double factor = 1.0D - distance;
            Vec3 delta = target.position().subtract(center);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double dx = horizontal < 0.01D ? 0.01D : delta.x / horizontal;
            double dz = horizontal < 0.01D ? 0.0D : delta.z / horizontal;
            double push = Math.min(KNOCK_LIMIT, radius * KNOCK_RATIO * factor);
            target.push(dx * push, push * 0.35D, dz * push);
            target.hurtMarked = true;
        }
    }
}
