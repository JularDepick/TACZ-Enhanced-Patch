package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.MobListConfig;
import com.taczenhancedpatch.config.MobMiddleConfig;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 持械生物中间层。
 * 按中间层配置里的分组与额外指定, 扫描游戏实际注册的实体类型, 展开成具体的生物 ID 列表,
 * 写入独立的列表配置文件。候选判定只读列表, 不直接读分组规则, 因此列表可以被当作唯一事实来源。
 */
public final class GunnerMobRegistry {
    private GunnerMobRegistry() {
    }

    /* 按分组规则重新展开并写回列表配置 */
    public static void refresh() {
        boolean hasRules = MobMiddleConfig.hasRules();
        Set<String> resolved = new LinkedHashSet<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null) {
                continue;
            }
            if (hasRules) {
                if (groupMatches(type) || MobMiddleConfig.extraIds().contains(id.toString())) {
                    resolved.add(id.toString());
                }
            } else if (defaultMatches(type)) {
                resolved.add(id.toString());
            }
        }
        MobListConfig.setMobs(resolved.stream().sorted().toList());
        MobListConfig.save();
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] mob list refreshed: {} entries (rules {})",
                    resolved.size(), hasRules);
        }
    }

    /* 默认规则: 全部敌对生物, 是否包含没有手部挂载部位的类型由服务端开关决定 */
    private static boolean defaultMatches(EntityType<?> type) {
        Class<? extends Entity> cls = type.getBaseClass();
        if (!Monster.class.isAssignableFrom(cls) || type.getCategory() != MobCategory.MONSTER) {
            return false;
        }
        if (WitherBoss.class.isAssignableFrom(cls)) {
            return false;
        }
        return ServerConfig.gunnerAllowNonHumanoid() || !isHandless(cls);
    }

    private static boolean groupMatches(EntityType<?> type) {
        Class<? extends Entity> cls = type.getBaseClass();
        if (MobMiddleConfig.groupUndead() && isUndead(cls)) {
            return true;
        }
        if (MobMiddleConfig.groupIllager() && Raider.class.isAssignableFrom(cls)) {
            return true;
        }
        if (MobMiddleConfig.groupArthropod() && isArthropod(cls)) {
            return true;
        }
        if (MobMiddleConfig.groupCreeper() && Creeper.class.isAssignableFrom(cls)) {
            return true;
        }
        if (MobMiddleConfig.groupNether() && isNether(cls)) {
            return true;
        }
        if (MobMiddleConfig.groupHumanoid() && !isHandless(cls)) {
            return true;
        }
        return MobMiddleConfig.groupNonHumanoid() && isHandless(cls);
    }

    private static boolean isUndead(Class<? extends Entity> cls) {
        return Zombie.class.isAssignableFrom(cls)
                || AbstractSkeleton.class.isAssignableFrom(cls)
                || Phantom.class.isAssignableFrom(cls)
                || Zoglin.class.isAssignableFrom(cls);
    }

    private static boolean isArthropod(Class<? extends Entity> cls) {
        return Spider.class.isAssignableFrom(cls)
                || Silverfish.class.isAssignableFrom(cls)
                || Endermite.class.isAssignableFrom(cls);
    }

    private static boolean isNether(Class<? extends Entity> cls) {
        return Blaze.class.isAssignableFrom(cls)
                || Ghast.class.isAssignableFrom(cls)
                || net.minecraft.world.entity.monster.MagmaCube.class.isAssignableFrom(cls)
                || AbstractPiglin.class.isAssignableFrom(cls)
                || Hoglin.class.isAssignableFrom(cls)
                || Zoglin.class.isAssignableFrom(cls);
    }

    /* 没有手部挂载部位的生物族 */
    private static boolean isHandless(Class<? extends Entity> cls) {
        return Slime.class.isAssignableFrom(cls)
                || Ghast.class.isAssignableFrom(cls)
                || Phantom.class.isAssignableFrom(cls)
                || Creeper.class.isAssignableFrom(cls)
                || Spider.class.isAssignableFrom(cls)
                || Silverfish.class.isAssignableFrom(cls)
                || Endermite.class.isAssignableFrom(cls)
                || Blaze.class.isAssignableFrom(cls)
                || Shulker.class.isAssignableFrom(cls)
                || Guardian.class.isAssignableFrom(cls);
    }

    /* 列表是否为空, 空表示中间层尚未展开 */
    public static boolean isEmpty() {
        return MobListConfig.mobs().isEmpty();
    }

    public static boolean isListed(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id != null && MobListConfig.mobs().contains(id.toString());
    }

    @Nullable
    public static String idOf(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? null : id.toString();
    }
}
