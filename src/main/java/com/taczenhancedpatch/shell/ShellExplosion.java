package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.faction.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/*
 * 手雷爆炸。
 * 地形与两类实体各自按杀伤对象档位决定是否处理。
 * 方块不再随机破坏: 威力从中心到边缘线性衰减到一半, 用衰减后的威力与方块硬度比较, 硬度不超过它的才被破坏。
 * 实体伤害仍按距离线性衰减到零, 中心伤害等于威力乘四。
 */
public final class ShellExplosion {
    /* 边缘处保留的威力比例: 中心为满值, 边缘为一半 */
    private static final double EDGE_POWER_RATIO = 0.5D;

    private ShellExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, ShellSettings settings, @Nullable Entity owner) {
        int power = settings.power();
        double radius = settings.radius();
        if (settings.target().terrain() && radius > 0.0D) {
            breakBlocks(level, center, radius, power);
        }
        damageEntities(level, center, radius, settings, owner);
        /* 震退与杀伤档位无关: 半径内的玩家与生物都会被推开, 震退本身不造成伤害 */
        knockBack(level, center, radius);
        ShellEffects.burst(level, center, power, settings);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Mth.clamp(power * 0.4F, 1.0F, 6.0F), 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /*
     * 由内向外按距离削减威力, 边缘只剩一半;
     * 方块硬度不超过削减后的威力时被破坏, 硬度为负的方块与流体一律跳过。
     */
    private static void breakBlocks(ServerLevel level, Vec3 center, double radius, int power) {
        int range = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            float hardness = state.getBlock().defaultDestroyTime();
            if (hardness < 0.0F) {
                continue;
            }
            double distance = Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(center));
            if (distance > radius) {
                continue;
            }
            double reduced = power * (1.0D - (1.0D - EDGE_POWER_RATIO) * distance / radius);
            if (hardness > reduced) {
                continue;
            }
            level.removeBlock(pos, false);
        }
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
            double distance = Math.sqrt(target.distanceToSqr(center));
            if (distance > radius) {
                continue;
            }
            float damage = (float) (maxDamage * (1.0D - distance / radius));
            if (damage <= 0.0F) {
                continue;
            }
            DamageSource source = level.damageSources().explosion(null, owner);
            target.hurt(source, damage);
        }
    }

    /*
     * 固定震退: 位移距离与杀伤半径一致, 中心最远, 边缘归零;
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
            double distance = Math.sqrt(target.distanceToSqr(center));
            if (distance > radius) {
                continue;
            }
            double factor = 1.0D - distance / radius;
            Vec3 delta = target.position().subtract(center);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double dx = horizontal < 0.01D ? 0.01D : delta.x / horizontal;
            double dz = horizontal < 0.01D ? 0.0D : delta.z / horizontal;
            /* 冲量与地面摩擦换算后的水平位移约为半径, 上限只兜住异常半径 */
            double push = Math.min(6.4D, radius * 0.4D * factor);
            target.push(dx * push, push * 0.35D, dz * push);
            target.hurtMarked = true;
        }
    }
}
