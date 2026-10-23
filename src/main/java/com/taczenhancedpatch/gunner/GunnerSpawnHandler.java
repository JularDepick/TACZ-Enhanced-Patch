package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪手生成。
 * 原生生成路径在 FinalizeSpawn 事件里判定身份, 绕过该事件的路径(例如 /summon)在实体进入世界时补判定。
 * 枪械装配统一放在实体进入世界之后, 因为原版 finalizeSpawn 晚于本模组的事件执行,
 * 骨架的弓, 掠夺者的弩等原版武器会覆盖主手, 需要在其之后再覆盖回来。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerSpawnHandler {
    private GunnerSpawnHandler() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (mob.level().isClientSide()) {
            return;
        }
        /* 原生生成路径已在此判定, 实体进入世界时不再重复掷概率 */
        GunnerMarker.markSpawnEvaluated(mob);
        tryArm(mob, event.getDifficulty().getDifficulty(), event.getSpawnType() == MobSpawnType.SPAWNER);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!event.loadedFromDisk() && !GunnerMarker.isSpawnEvaluated(mob)) {
            /* 绕过 finalizeSpawn 的生成路径, 例如 /summon 与部分模组的直接添加 */
            GunnerMarker.markSpawnEvaluated(mob);
            Difficulty difficulty = event.getLevel().getCurrentDifficultyAt(mob.blockPosition()).getDifficulty();
            tryArm(mob, difficulty, false);
        }
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(mob);
        if (loadout == null) {
            return;
        }
        if (!GunnerMarker.isBuffed(mob)) {
            GunnerDurability.apply(mob, loadout.tier());
            GunnerMarker.setBuffed(mob, true);
        }
        if (TaczApi.isGun(mob.getMainHandItem())) {
            return;
        }
        equip(mob, loadout.gunId(), loadout.fireMode(), loadout.ammoPool());
    }

    private static void tryArm(Mob mob, Difficulty difficulty, boolean fromSpawner) {
        if (!ServerConfig.gunnerEnabled() || mob.isBaby()) {
            return;
        }
        if (GunnerMarker.isGunner(mob) || TaczApi.isGun(mob.getMainHandItem())) {
            return;
        }
        if (!GunnerEligibility.canCarryGun(mob) || isBlacklistedDimension(mob)) {
            return;
        }
        double chance = spawnChance(difficulty);
        if (fromSpawner) {
            chance = Math.max(chance, ServerConfig.gunnerSpawnerChance());
        }
        if (mob.getRandom().nextDouble() >= chance) {
            return;
        }

        int[] weights = ServerConfig.gunnerTierWeights();
        if (ServerConfig.gunnerDifficultyScaling()) {
            if (difficulty == Difficulty.HARD) {
                weights[2] *= 2;
            } else if (difficulty == Difficulty.EASY) {
                weights[0] *= 2;
            }
        }
        GunnerArsenal.Tier tier = GunnerArsenal.pickTier(mob.getRandom(), weights[0], weights[1], weights[2]);
        GunnerArsenal.GunProfile profile = GunnerArsenal.pick(tier, mob.getRandom());
        if (profile != null) {
            GunnerMarker.mark(mob, profile);
        }
    }

    private static boolean equip(Mob mob, ResourceLocation gunId, TaczApi.FireMode fireMode, int ammoPool) {
        ItemStack gun = TaczApi.createGun(gunId, fireMode, ammoPool);
        if (gun.isEmpty()) {
            return false;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] armed {} with {}", mob.getType(), gunId);
        }
        return true;
    }

    private static boolean isBlacklistedDimension(Mob mob) {
        String dimension = mob.level().dimension().location().toString();
        return ServerConfig.gunnerDimensionBlacklist().contains(dimension);
    }

    private static double spawnChance(Difficulty difficulty) {
        double chance = ServerConfig.gunnerSpawnChance();
        if (ServerConfig.gunnerDifficultyScaling()) {
            chance *= switch (difficulty) {
                case EASY -> 0.6D;
                case HARD -> 1.5D;
                default -> 1.0D;
            };
        }
        return Mth.clamp(chance, 0.0D, 1.0D);
    }
}
