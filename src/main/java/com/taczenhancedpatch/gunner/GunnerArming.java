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
        int ammo = GunnerArsenal.randomAmmo(ServerConfig.gunnerBaseAmmo(), random);
        return new Arming(profile, ammo);
    }

    public static boolean equip(Mob mob, ResourceLocation gunId, TaczApi.FireMode fireMode, int ammoPool, RandomSource random) {
        ItemStack gun = createGun(gunId, fireMode, ammoPool, random);
        if (gun.isEmpty()) {
            return false;
        }
        /* 记录被覆盖的原版武器, 弹尽后还原; 记下发出去的枪, 弹尽收起后仍可用于战利品结算 */
        ItemStack current = mob.getMainHandItem();
        if (!current.isEmpty() && !TaczApi.isGun(current)) {
            GunnerMarker.setReplacedWeapon(mob, current);
        }
        GunnerMarker.setStoredGun(mob, gun);
        mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] armed {} with {} ({} rounds)", mob.getType(), gunId, ammoPool);
        }
        return true;
    }

    /* 弹尽后收起枪械, 主手还原为被覆盖的原版武器, 没有原版武器则清空主手 */
    public static void disarm(Mob mob) {
        ItemStack held = mob.getMainHandItem();
        if (!TaczApi.isGun(held)) {
            return;
        }
        /* 记下收起时的实际状态, 战利品按这份枪械结算剩余备弹 */
        GunnerMarker.setStoredGun(mob, held);
        ItemStack replaced = GunnerMarker.replacedWeapon(mob);
        mob.setItemSlot(EquipmentSlot.MAINHAND, replaced.isEmpty() ? ItemStack.EMPTY : replaced.copy());
        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] {} put away the gun when out of ammo", mob.getType());
        }
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
