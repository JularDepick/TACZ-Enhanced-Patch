package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 枪手候选判定。
 * 候选名单由持械生物中间层扫描全部生物后写入, 每条带一个开关状态, 这里只读那份开关;
 * 列表为空说明中间层尚未扫描, 此时候退为只放行敌对阵营。
 * 首领单位始终排除, 黑名单始终优先。
 */
public final class GunnerEligibility {
    private GunnerEligibility() {
    }

    public static boolean canCarryGun(Mob mob) {
        if (isBlacklisted(mob) || mob instanceof WitherBoss) {
            return false;
        }
        /* 持枪小兵自带枪械, 不进入枪手系统 */
        if (mob instanceof com.taczenhancedpatch.entity.GunnerGrunt) {
            return false;
        }
        if (!GunnerMobRegistry.isEmpty()) {
            return GunnerMobRegistry.isEnabled(mob.getType());
        }
        return mob instanceof Monster && mob.getType().getCategory() == MobCategory.MONSTER;
    }

    private static boolean isBlacklisted(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && ServerConfig.gunnerMobBlacklist().contains(id.toString());
    }
}
