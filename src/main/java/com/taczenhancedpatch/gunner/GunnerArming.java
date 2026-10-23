package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * 枪械装配。
 * 生成路径与临场武装共用同一套规则: 分档抽枪, 备弹量按百分比随机, 配件按概率随机安装,
 * 因此所有持枪生物的枪械配置都不完全相同。
 */
public final class GunnerArming {
    public record Arming(GunnerArsenal.GunProfile profile, int ammoPool) {
    }

    private GunnerArming() {
    }

    public static Arming roll(RandomSource random, Difficulty difficulty) {
        int[] weights = ServerConfig.gunnerTierWeights();
        if (ServerConfig.gunnerDifficultyScaling()) {
            if (difficulty == Difficulty.HARD) {
                weights[2] *= 2;
            } else if (difficulty == Difficulty.EASY) {
                weights[0] *= 2;
            }
        }
        GunnerArsenal.Tier tier = GunnerArsenal.pickTier(random, weights[0], weights[1], weights[2]);
        GunnerArsenal.GunProfile profile = GunnerArsenal.pick(tier, random);
        if (profile == null) {
            return null;
        }
        int ammo = GunnerArsenal.randomAmmo(profile.ammoPool(), random, ServerConfig.gunnerAmmoVariation());
        return new Arming(profile, ammo);
    }

    public static boolean equip(Mob mob, ResourceLocation gunId, TaczApi.FireMode fireMode, int ammoPool, RandomSource random) {
        ItemStack gun = createGun(gunId, fireMode, ammoPool, random);
        if (gun.isEmpty()) {
            return false;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] armed {} with {} ({} rounds)", mob.getType(), gunId, ammoPool);
        }
        return true;
    }

    private static ItemStack createGun(ResourceLocation gunId, TaczApi.FireMode fireMode, int ammoPool, RandomSource random) {
        ItemStack gun = TaczApi.createGun(gunId, fireMode, ammoPool);
        if (gun.isEmpty()) {
            return gun;
        }
        int slots = ServerConfig.gunnerRandomAttachmentSlots();
        for (int i = 0; i < slots; i++) {
            if (random.nextFloat() < GunnerArsenal.ATTACHMENT_SLOT_CHANCE) {
                TaczApi.installRandomAttachment(gun, random);
            }
        }
        return gun;
    }
}
