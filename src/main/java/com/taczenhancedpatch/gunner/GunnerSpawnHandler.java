package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.CommonConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 枪手生成。
 * 在生物生成收尾阶段按配置决定是否装备枪械, 不新增实体类型, 只改变原版敌对生物的武装。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerSpawnHandler {
    private GunnerSpawnHandler() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!CommonConfig.gunnerEnabled()) {
            return;
        }
        Mob mob = event.getEntity();
        if (mob.level().isClientSide() || mob.isBaby()) {
            return;
        }
        if (GunnerMarker.isGunner(mob) || TaczApi.isGun(mob.getMainHandItem())) {
            return;
        }
        if (!isWhitelisted(mob) || isBlacklistedDimension(mob)) {
            return;
        }
        Difficulty difficulty = event.getDifficulty().getDifficulty();
        double chance = spawnChance(difficulty);
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            chance = Math.max(chance, CommonConfig.gunnerSpawnerChance());
        }
        if (mob.getRandom().nextDouble() >= chance) {
            return;
        }

        int[] weights = CommonConfig.gunnerTierWeights();
        if (CommonConfig.gunnerDifficultyScaling()) {
            if (difficulty == Difficulty.HARD) {
                weights[2] *= 2;
            } else if (difficulty == Difficulty.EASY) {
                weights[0] *= 2;
            }
        }
        GunnerArsenal.Tier tier = GunnerArsenal.pickTier(mob.getRandom(), weights[0], weights[1], weights[2]);
        GunnerArsenal.GunProfile profile = GunnerArsenal.pick(tier, mob.getRandom());
        if (profile == null) {
            return;
        }
        ItemStack gun = TaczApi.createGun(profile.gunId(), profile.fireMode(), profile.ammoPool());
        if (gun.isEmpty()) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
        GunnerMarker.mark(mob, profile);
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] armed {} with {} (tier {})", mob.getType(), profile.gunId(), tier);
        }
    }

    private static boolean isWhitelisted(Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id != null && CommonConfig.gunnerMobWhitelist().contains(id.toString());
    }

    private static boolean isBlacklistedDimension(Mob mob) {
        String dimension = mob.level().dimension().location().toString();
        return CommonConfig.gunnerDimensionBlacklist().contains(dimension);
    }

    private static double spawnChance(Difficulty difficulty) {
        double chance = CommonConfig.gunnerSpawnChance();
        if (CommonConfig.gunnerDifficultyScaling()) {
            chance *= switch (difficulty) {
                case EASY -> 0.6D;
                case HARD -> 1.5D;
                default -> 1.0D;
            };
        }
        return Mth.clamp(chance, 0.0D, 1.0D);
    }
}
