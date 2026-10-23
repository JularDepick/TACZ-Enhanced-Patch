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
 * 候选不写死生物清单, 而是对游戏实际注册的每个实体类型逐个判定:
 * 属于敌对生物且能正常持握物品即纳入, 因此其他模组新增的敌对生物会自动参与判定。
 * 判定结果按实体类型缓存, 黑名单按实体逐个检查, 便于运行时改配置。
 */
public final class GunnerEligibility {
    private static final Map<EntityType<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    private GunnerEligibility() {
    }

    public static boolean canCarryGun(Mob mob) {
        if (isBlacklisted(mob)) {
            return false;
        }
        return CACHE.computeIfAbsent(mob.getType(), key -> isStructuralCandidate(mob));
    }

    private static boolean isStructuralCandidate(Mob mob) {
        return mob instanceof Monster
                && mob.getType().getCategory() == MobCategory.MONSTER
                && !isHandless(mob);
    }

    /**
     * 没有可挂载物品部位的生物族。
     * 这些类型装备枪械只会得到一把看不见的枪, 因此按类型特征排除, 而不是列出一份允许清单。
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
                || mob instanceof Guardian
                || mob instanceof WitherBoss;
    }

    private static boolean isBlacklisted(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && ServerConfig.gunnerMobBlacklist().contains(id.toString());
    }
}
