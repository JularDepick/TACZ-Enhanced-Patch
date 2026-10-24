package com.taczenhancedpatch.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 组合体增益。
 * 坦克与骑乘在它身上的小兵视为一个组合体, 每个成员的属性按
 * "组合体内部生命上限总和 / 坦克生命上限" 的倍率提高生命与护甲;
 * 成员变动时重新计算, 并把已经离开的成员的加成移除。
 */
public final class TankCrew {
    /* 倍率上限, 避免四个人叠满后属性失控 */
    private static final double MAX_RATIO = 3.0D;
    private static final String NAME = "tep_crew";

    /* 记录每辆坦克上一次加成过的成员, 供离开时清理 */
    private static final Map<UUID, Set<UUID>> MEMBERS = new HashMap<>();

    private TankCrew() {
    }

    public static void refresh(TankElite tank) {
        Set<UUID> current = new HashSet<>();
        current.add(tank.getUUID());
        for (var passenger : tank.getPassengers()) {
            if (passenger instanceof LivingEntity living) {
                current.add(living.getUUID());
            }
        }
        double ratio = ratio(tank);
        Set<UUID> previous = MEMBERS.getOrDefault(tank.getUUID(), Set.of());
        /* 先清掉不再属于组合体的成员 */
        for (UUID id : previous) {
            if (current.contains(id)) {
                continue;
            }
            LivingEntity living = findEntity(tank, id);
            if (living != null) {
                clear(living);
            }
        }
        for (var passenger : tank.getPassengers()) {
            if (passenger instanceof LivingEntity living) {
                apply(living, ratio);
            }
        }
        apply(tank, ratio);
        MEMBERS.put(tank.getUUID(), current);
    }

    /* 坦克失去全部成员时把加成整体撤掉 */
    public static void clear(TankElite tank) {
        Set<UUID> previous = MEMBERS.remove(tank.getUUID());
        if (previous != null) {
            for (UUID id : previous) {
                LivingEntity living = findEntity(tank, id);
                if (living != null) {
                    clear(living);
                }
            }
        }
        clear(tank);
    }

    /* 按 UUID 在服务端存档里找实体, 找不到说明已经离开世界 */
    private static LivingEntity findEntity(TankElite tank, UUID id) {
        if (tank.level() instanceof ServerLevel server) {
            Entity entity = server.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static double ratio(TankElite tank) {
        double tankHealth = baseMaxHealth(tank);
        if (tankHealth <= 0.0D) {
            return 1.0D;
        }
        double total = tankHealth;
        for (var passenger : tank.getPassengers()) {
            if (passenger instanceof LivingEntity living) {
                total += baseMaxHealth(living);
            }
        }
        return Math.min(MAX_RATIO, Math.max(1.0D, total / tankHealth));
    }

    private static double baseMaxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        return attribute == null ? 0.0D : attribute.getBaseValue();
    }

    private static void apply(LivingEntity entity, double ratio) {
        double bonus = ratio - 1.0D;
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            UUID id = modifierId(entity, "health");
            health.removeModifier(id);
            health.addTransientModifier(new AttributeModifier(id, NAME + "_health", bonus,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            UUID id = modifierId(entity, "armor");
            armor.removeModifier(id);
            armor.addTransientModifier(new AttributeModifier(id, NAME + "_armor", bonus,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        entity.setHealth(entity.getMaxHealth());
    }

    private static void clear(LivingEntity entity) {
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(modifierId(entity, "health"));
        }
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(modifierId(entity, "armor"));
        }
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static UUID modifierId(LivingEntity entity, String kind) {
        return UUID.nameUUIDFromBytes((NAME + ":" + kind + ":" + entity.getUUID()).getBytes(StandardCharsets.UTF_8));
    }
}