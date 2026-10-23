package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枪手候选判定。
 * 候选不写死生物清单, 而是对游戏实际注册的每个敌对实体类型逐个判定。
 * 默认连没有手部挂载部位的生物也纳入, 只是枪械不会显示在模型上;
 * 首领单位始终不参与, 避免改变首领战的形态。
 */
public final class GunnerEligibility {
    private static final Map<EntityType<?>, Boolean> HANDLESS_CACHE = new ConcurrentHashMap<>();

    private GunnerEligibility() {
    }

    public static boolean canCarryGun(Mob mob) {
        if (isBlacklisted(mob) || !isHostile(mob)) {
            return false;
        }
        if (mob instanceof WitherBoss) {
            return false;
        }
        if (ServerConfig.gunnerAllowNonHumanoid()) {
            return true;
        }
        return !HANDLESS_CACHE.computeIfAbsent(mob.getType(), key -> isHandless(mob));
    }

    private static boolean isHostile(Mob mob) {
        return mob instanceof Monster && mob.getType().getCategory() == MobCategory.MONSTER;
    }

    /**
     * 没有可挂载物品部位的生物族。
     * 这些类型装备枪械后枪械不可见, 只在关闭非人形持械时用于排除。
     */
    private static boolean isHandless(Mob mob) {
        return mob instanceof Slime
                || mob instanceof Ghast
                || mob instanceof Phantom
                || mob instanceof Creeper
                || mob instanceof Spider
                || mob instanceof Silverfish
                || mob instanceof Endermite
                || mob instanceof Blaze
                || mob instanceof Shulker
                || mob instanceof Guardian;
    }

    private static boolean isBlacklisted(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && ServerConfig.gunnerMobBlacklist().contains(id.toString());
    }
}
