package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.MobListConfig;
import com.taczenhancedpatch.config.MobMiddleConfig;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 持械生物中间层。
 * 扫描游戏实际注册的全部生物, 逐条写入生物持械配置, 每条带一个开关状态;
 * 新条目默认关闭, 分组开关只负责批量改写列表里的开关状态。
 * 候选判定只读列表, 因此列表就是唯一事实来源。
 */
public final class GunnerMobRegistry {
    /* 列表为空表示中间层尚未扫描, 此时候选判定退回默认规则 */
    private static Set<String> enabledCache;

    private GunnerMobRegistry() {
    }

    /* 扫描全部生物并写入列表: 已有条目保持原开关, 新条目按当前分组开关判定 */
    public static void refresh() {
        Map<String, Boolean> existing = MobListConfig.states();
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (!isMob(type)) {
                continue;
            }
            String id = idOf(type);
            if (id == null) {
                continue;
            }
            Boolean known = existing.get(id);
            resolved.put(id, known != null ? known : groupEnabled(type));
        }
        MobListConfig.setStates(resolved);
        MobListConfig.save();
        invalidate();
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] mob list refreshed: {} entries, {} enabled",
                    resolved.size(), enabledIds().size());
        }
    }

    /* 分组开关: 先把开关状态写回中间层, 再把该组生物在列表里的状态改成批量结果 */
    public static int applyGroup(MobMiddleConfig.GroupSwitch group, boolean enabled) {
        group.setter().accept(enabled);
        MobMiddleConfig.save();
        Map<String, Boolean> states = MobListConfig.states();
        int changed = 0;
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if (!isMob(type) || !matchesGroup(group.key(), type)) {
                continue;
            }
            String id = idOf(type);
            if (id == null) {
                continue;
            }
            /* 关闭某一组时, 仍被其它已开启分组命中的生物保持开启 */
            boolean target = groupEnabled(type);
            if (!Boolean.valueOf(target).equals(states.get(id))) {
                states.put(id, target);
                changed++;
            }
        }
        MobListConfig.setStates(states);
        MobListConfig.save();
        invalidate();
        return changed;
    }

    /* 单条状态改写, 供列表页逐项开关使用 */
    public static void setEnabled(String id, boolean enabled) {
        MobListConfig.setState(id, enabled);
        MobListConfig.save();
        invalidate();
    }

    /* 某个分组是否命中该类生物, 同一生物可以同时属于多个分组 */
    public static boolean matchesGroup(String key, EntityType<?> type) {
        Class<? extends Entity> cls = type.getBaseClass();
        return switch (key) {
            case "undead" -> isUndead(cls);
            case "illager" -> Raider.class.isAssignableFrom(cls);
            case "arthropod" -> isArthropod(cls);
            case "creeper" -> Creeper.class.isAssignableFrom(cls);
            case "nether" -> isNether(cls);
            case "humanoid" -> !isHandless(cls);
            default -> isHandless(cls);
        };
    }

    /* 当前分组开关命中的生物是否在列表里开启 */
    private static boolean groupEnabled(EntityType<?> type) {
        for (MobMiddleConfig.GroupSwitch group : MobMiddleConfig.groups()) {
            if (group.getter().getAsBoolean() && matchesGroup(group.key(), type)) {
                return true;
            }
        }
        return false;
    }

    /* 只扫描能成为枪手的生物, 即继承自 Mob 的实体类型 */
    private static boolean isMob(EntityType<?> type) {
        return Mob.class.isAssignableFrom(type.getBaseClass());
    }

    private static Set<String> enabledIds() {
        if (enabledCache == null) {
            enabledCache = new TreeSet<>();
            for (Map.Entry<String, Boolean> entry : MobListConfig.states().entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    enabledCache.add(entry.getKey());
                }
            }
        }
        return enabledCache;
    }

    private static void invalidate() {
        enabledCache = null;
    }

    /* 列表是否为空, 空表示中间层尚未展开 */
    public static boolean isEmpty() {
        return MobListConfig.states().isEmpty();
    }

    public static boolean isEnabled(EntityType<?> type) {
        String id = idOf(type);
        return id != null && enabledIds().contains(id);
    }

    @Nullable
    public static String idOf(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? null : id.toString();
    }

    private static boolean isUndead(Class<? extends Entity> cls) {
        return Zombie.class.isAssignableFrom(cls)
                || net.minecraft.world.entity.monster.AbstractSkeleton.class.isAssignableFrom(cls)
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
}
