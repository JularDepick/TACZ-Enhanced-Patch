package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.faction.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
 * 不复用原版爆炸: 地形与两类实体各自按杀伤对象档位决定是否处理, 伤害按距离线性衰减到零,
 * 方块按抗爆性与距离随机破坏。生物投出的手雷跳过自己与同阵营, 避免误伤友军。
 */
public final class GrenadeExplosion {
    /* 抗爆性超过该值的方块不被破坏, 与原版黑曜石一类的观感一致 */
    private static final float RESISTANCE_LIMIT = 100.0F;

    private GrenadeExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, GrenadeSettings settings, @Nullable Entity owner) {
        int power = settings.power();
        double radius = settings.blockRadius();
        if (settings.target().terrain() && radius > 0.0D) {
            breakBlocks(level, center, radius, level.getRandom());
        }
        damageEntities(level, center, radius, settings, owner);
        GrenadeEffects.burst(level, center, power, settings);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                Mth.clamp(power * 0.4F, 1.0F, 6.0F), 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    private static void breakBlocks(ServerLevel level, Vec3 center, double radius, RandomSource random) {
        int range = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            double distance = Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(center));
            if (distance > radius) {
                continue;
            }
            if (state.getBlock().getExplosionResistance() >= RESISTANCE_LIMIT) {
                continue;
            }
            /* 越靠近中心越容易破坏, 边缘只剩少量方块被掀掉 */
            if (random.nextDouble() > 1.0D - distance / radius) {
                continue;
            }
            level.removeBlock(pos, false);
        }
    }

    private static void damageEntities(ServerLevel level, Vec3 center, double radius, GrenadeSettings settings,
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
            double push = 0.6D * (1.0D - distance / radius);
            Vec3 direction = target.position().subtract(center).normalize().scale(push);
            target.push(direction.x, direction.y * 0.5D + push * 0.3D, direction.z);
        }
    }
}
