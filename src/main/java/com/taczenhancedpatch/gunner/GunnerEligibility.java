package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractPiglin;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Hoglin;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枪手候选判定。
 * 候选不写死生物清单, 而是对游戏实际注册的每个敌对实体类型逐个判定。
 * 配置界面可以按分组或具体实体 ID 限定候选范围; 两者都没有内容时沿用默认规则。
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
        if (matchesMobList(mob) || matchesGroup(mob)) {
            return true;
        }
        /* 分组与名单都没有内容时, 按非人形持械开关判定全部敌对生物 */
        if (hasIncludeRules()) {
            return false;
        }
        return ServerConfig.gunnerAllowNonHumanoid() || !isHandless(mob);
    }

    private static boolean hasIncludeRules() {
        return ServerConfig.mobGroupUndead()
                || ServerConfig.mobGroupIllager()
                || ServerConfig.mobGroupArthropod()
                || ServerConfig.mobGroupCreeper()
                || ServerConfig.mobGroupNether()
                || ServerConfig.mobGroupHumanoid()
                || ServerConfig.mobGroupNonHumanoid()
                || !ServerConfig.gunnerMobIds().isEmpty();
    }

    private static boolean matchesMobList(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && ServerConfig.gunnerMobIds().contains(id.toString());
    }

    private static boolean matchesGroup(Mob mob) {
        if (ServerConfig.mobGroupUndead() && isUndead(mob)) {
            return true;
        }
        if (ServerConfig.mobGroupIllager() && mob instanceof Raider) {
            return true;
        }
        if (ServerConfig.mobGroupArthropod() && isArthropod(mob)) {
            return true;
        }
        if (ServerConfig.mobGroupCreeper() && mob instanceof Creeper) {
            return true;
        }
        if (ServerConfig.mobGroupNether() && isNether(mob)) {
            return true;
        }
        boolean handless = isHandless(mob);
        if (ServerConfig.mobGroupHumanoid() && !handless) {
            return true;
        }
        return ServerConfig.mobGroupNonHumanoid() && handless;
    }

    private static boolean isUndead(Mob mob) {
        return mob instanceof Zombie
                || mob instanceof AbstractSkeleton
                || mob instanceof Phantom
                || mob instanceof Zoglin;
    }

    private static boolean isArthropod(Mob mob) {
        return mob instanceof Spider || mob instanceof Silverfish || mob instanceof Endermite;
    }

    private static boolean isNether(Mob mob) {
        return mob instanceof Blaze
                || mob instanceof Ghast
                || mob instanceof MagmaCube
                || mob instanceof AbstractPiglin
                || mob instanceof Hoglin
                || mob instanceof Zoglin;
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
