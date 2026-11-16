package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.Factions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/*
 * 炮弹爆炸。
 * 杀伤空间不是球体而是扁球: 水平半径等于威力, 铅直半径压缩到水平半径的三分之二。
 * 地形按扁球归一化距离分圈, 由内向外每游戏刻破坏四圈并播放击碎音效;
 * 杀伤与震退同样按圈推进: 爆炸瞬间只算出每个目标在爆心多远与应受的伤害, 之后由杀伤面每游戏刻向外推进四格。
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
    /* 破甲伤害类型, 只跳过护甲点数, 仍受附魔保护影响 */
    private static final ResourceKey<DamageType> PIERCE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(TACZEnhancedPatch.MOD_ID, "shell_pierce"));

    private ShellExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, ShellSettings settings, @Nullable Entity owner,
                                double penetration) {
        int power = settings.power();
        double radius = settings.radius();
        if (settings.target().terrain() && radius > 0.0D) {
            ShellBreaker.enqueue(level, center, radius, power);
        }
        /* 杀伤与震退交给推进队列: 与地形一样一圈圈向外, 不再一瞬间结算 */
        ShellDamage.enqueue(level, center, owner, pierce(level, owner),
                collectHits(level, center, radius, settings, owner, penetration));
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

    /*
     * 结算清单。
     * 只做判定与算术: 哪些目标按档位可以被杀伤, 各受多少破甲与常规伤害, 震退多远。
     * 真正的伤害与推动由推进队列在杀伤面扫到时执行, 因此这里的结果必须与时刻无关。
     */
    private static List<ShellDamage.Hit> collectHits(ServerLevel level, Vec3 center, double radius,
                                                     ShellSettings settings, @Nullable Entity owner,
                                                     double penetration) {
        List<ShellDamage.Hit> hits = new ArrayList<>();
        if (radius <= 0.0D) {
            return hits;
        }
        boolean fromPlayer = owner instanceof Player;
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!target.isAlive() || target.isSpectator()) {
                continue;
            }
            double distance = shape(center, target.getBoundingBox().getCenter(), radius);
            if (distance > 1.0D) {
                continue;
            }
            float pierced = 0.0F;
            float normal = 0.0F;
            boolean allowed = true;
            if (target instanceof Player player) {
                if (!settings.target().players() || player.isCreative() || player.isSpectator()) {
                    allowed = false;
                }
            } else if (!settings.target().mobs()) {
                allowed = false;
            }
            if (allowed && !fromPlayer) {
                if (target == owner || (owner != null && Factions.allied(owner, target))) {
                    allowed = false;
                }
            }
            if (allowed) {
                float damage = (float) (settings.damage() * (1.0D - distance));
                if (damage > 0.0F) {
                    pierced = (float) (damage * penetration);
                    normal = damage - pierced;
                }
            }
            /* 震退与杀伤档位无关: 半径内的玩家与生物都会被推开, 位移与杀伤半径一致, 中心最远边缘归零 */
            double factor = 1.0D - distance;
            Vec3 delta = target.position().subtract(center);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double dx = horizontal < 0.01D ? 0.01D : delta.x / horizontal;
            double dz = horizontal < 0.01D ? 0.0D : delta.z / horizontal;
            double push = Math.min(KNOCK_LIMIT, radius * KNOCK_RATIO * factor);
            if (pierced <= 0.0F && normal <= 0.0F && push <= 0.0D) {
                continue;
            }
            hits.add(new ShellDamage.Hit(target.getUUID(), distance * radius, pierced, normal,
                    dx * push, push * 0.35D, dz * push));
        }
        return hits;
    }

    private static DamageSource pierce(ServerLevel level, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PIERCE),
                null, owner);
    }
}
