package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
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

/**
 * 枪手候选判定。
 * 候选名单由持械生物中间层展开并写入独立的列表配置, 这里只读那份列表;
 * 列表为空说明中间层尚未展开, 此时退回默认规则, 按敌对阵营与非人形开关判定。
 * 首领单位始终排除, 黑名单始终优先。
 */
public final class GunnerEligibility {
    private GunnerEligibility() {
    }

    public static boolean canCarryGun(Mob mob) {
        if (isBlacklisted(mob) || mob instanceof WitherBoss) {
            return false;
        }
        if (GunnerMobRegistry.isListed(mob.getType())) {
            return true;
        }
        if (!GunnerMobRegistry.isEmpty()) {
            return false;
        }
        if (!isHostile(mob)) {
            return false;
        }
        return ServerConfig.gunnerAllowNonHumanoid() || !isHandless(mob);
    }

    private static boolean isHostile(Mob mob) {
        return mob instanceof Monster && mob.getType().getCategory() == MobCategory.MONSTER;
    }

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
