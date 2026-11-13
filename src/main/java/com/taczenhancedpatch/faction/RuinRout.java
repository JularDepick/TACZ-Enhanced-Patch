package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * 溃军。
 * 夺旗成功后遗迹势力瓦解, 领地内剩余的守军转为溃军: 阵营与归属都不变,
 * 但单体战力按配置的系数下降, 生命上限与攻击力同时缩放, 坦克炮弹威力一并缩减。
 */
public final class RuinRout {
    private static final String TAG_ROUTED = "tacz_enhanced_patch_routed";
    private static final String TAG_FACTOR = "tacz_enhanced_patch_routed_factor";
    /* 固定修饰符标识, 重复标记时先移除再加, 保证只生效一份 */
    private static final UUID HEALTH_ID = UUID.fromString("3f2a1c4e-9b7d-4c21-8f60-2a5d7e9c1b34");
    private static final UUID DAMAGE_ID = UUID.fromString("8c1d5b6a-4e2f-4a90-b3d7-6f8e2c4a9d51");
    /* 溃军搜索范围: 领地半径之外再放宽一段, 覆盖正在追击玩家的守军 */
    private static final int SEARCH_MARGIN = 64;

    private RuinRout() {
    }

    /* 夺旗成功时把该遗迹的剩余守军全部转为溃军 */
    public static void rout(ServerLevel level, RuinSites.Site site) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return;
        }
        float factor = factorFromConfig();
        int radius = site.radius() + SEARCH_MARGIN;
        AABB area = new AABB(banner.getX() - radius, level.getMinBuildHeight(), banner.getZ() - radius,
                banner.getX() + radius + 1, level.getMaxBuildHeight(), banner.getZ() + radius + 1);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                mob -> RuinTerritory.isRuinMob(mob) && site.origin().equals(FactionData.homeOf(mob)))) {
            mark(mob, factor);
        }
    }

    public static void mark(Mob mob, float factor) {
        if (isRouted(mob)) {
            return;
        }
        mob.getPersistentData().putBoolean(TAG_ROUTED, true);
        mob.getPersistentData().putFloat(TAG_FACTOR, factor);
        apply(mob, factor);
    }

    public static boolean isRouted(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG_ROUTED);
    }

    /* 溃军战力系数, 非溃军恒为一 */
    public static float factor(Mob mob) {
        if (!isRouted(mob)) {
            return 1.0F;
        }
        float value = mob.getPersistentData().getFloat(TAG_FACTOR);
        return value <= 0.0F || value > 1.0F ? 1.0F : value;
    }

    private static float factorFromConfig() {
        int percent = ServerConfig.ruinRoutedFactor();
        return Math.max(1, Math.min(100, percent)) / 100.0F;
    }

    /* 生命上限与攻击力按同一系数缩放, 缩放后把当前生命夹到新上限 */
    private static void apply(Mob mob, float factor) {
        double delta = factor - 1.0D;
        applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, "ruin_routed_health", delta);
        applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "ruin_routed_damage", delta);
        if (mob.getHealth() > mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void applyModifier(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                      UUID id, String name, double delta) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        instance.addPermanentModifier(new AttributeModifier(id, name, delta, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
