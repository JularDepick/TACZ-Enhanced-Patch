package com.taczenhancedpatch.loot;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 枪手战利品。
 * 击杀枪手必定获得与其枪械同口径的弹药, 数量由剩余备弹与随机数量共同决定;
 * 枪械与配件按概率掉落, 另有概率额外掉落一份高阶口径弹药。
 * 掉落枪械时会把剩余备弹清零, 剩余弹药改为以弹药物品掉落, 避免同一份弹药掉两次。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class BattleLootHandler {
    /* 单个掉落物的数量上限, 超出部分拆成多份 */
    private static final int MAX_AMMO_PER_DROP = 64;
    /* 每一级抢夺等级增加的随机弹药数量 */
    private static final int LOOTING_AMMO_BONUS = 2;

    private BattleLootHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!ServerConfig.gunnerEnabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(dead);
        if (loadout == null) {
            return;
        }
        RandomSource random = dead.getRandom();
        ItemStack held = dead.getMainHandItem();
        /* 弹尽后枪械已收起, 结算使用登记过的那把枪 */
        ItemStack gunStack = TaczApi.isGun(held) ? held : GunnerMarker.storedGun(dead);
        int reserve = TaczApi.dummyAmmo(gunStack);

        /* 枪械掉落统一由本模组接管, 移除原版概率掉落, 避免重复 */
        event.getDrops().removeIf(drop -> {
            ResourceLocation gunId = TaczApi.getGunId(drop.getItem());
            return gunId != null && gunId.equals(loadout.gunId());
        });

        /* 弹药必定掉落: 剩余备弹按倍率折算, 再叠加随机数量与抢夺加成 */
        int leftover = (int) Math.round(reserve * ServerConfig.gunnerAmmoDropMultiplier());
        int randomPart = Mth.nextInt(random, loadout.ammoDropMin(), loadout.ammoDropMax())
                + event.getLootingLevel() * LOOTING_AMMO_BONUS;
        dropAmmo(dead, loadout.ammoId(), Math.max(1, leftover + randomPart), event);

        /* 高级弹药: 额外一份高阶口径弹药 */
        if (random.nextDouble() < ServerConfig.gunnerHighTierAmmoChance()) {
            ResourceLocation highTier = GunnerArsenal.pickHighTierAmmo(random);
            if (highTier != null) {
                int count = (int) Math.round(Mth.nextInt(random, GunnerArsenal.HIGH_TIER_AMMO_MIN, GunnerArsenal.HIGH_TIER_AMMO_MAX)
                        * ServerConfig.gunnerAmmoDropMultiplier());
                dropAmmo(dead, highTier, Math.max(1, count), event);
            }
        }

        if (random.nextDouble() < ServerConfig.gunnerGunDropChance()) {
            addDrop(dead, gunForDrop(gunStack, loadout), event);
        }

        if (random.nextDouble() < ServerConfig.gunnerAttachmentDropChance()) {
            Optional<ItemStack> attachment = TaczApi.randomAttachment(random);
            attachment.ifPresent(stack -> addDrop(dead, stack, event));
        }

        if (ServerConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] loot from {}: ammo {} (reserve {})", dead.getType(), loadout.ammoId(), reserve);
        }
    }

    /* 掉落实际使用的那把枪, 随机配件随枪转移, 剩余备弹由弹药掉落接管 */
    private static ItemStack gunForDrop(ItemStack held, GunnerMarker.Loadout loadout) {
        if (TaczApi.isGun(held)) {
            ItemStack gun = held.copy();
            TaczApi.clearDummyAmmo(gun);
            return gun;
        }
        return TaczApi.createGun(loadout.gunId(), loadout.fireMode(), 0);
    }

    /* 数量超过单份上限时拆成多份掉落 */
    private static void dropAmmo(LivingEntity dead, @Nullable ResourceLocation ammoId, int count, LivingDropsEvent event) {
        if (ammoId == null || count <= 0) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, MAX_AMMO_PER_DROP);
            addDrop(dead, TaczApi.createAmmo(ammoId, stackSize), event);
            remaining -= stackSize;
        }
    }

    private static void addDrop(LivingEntity dead, ItemStack stack, LivingDropsEvent event) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5D, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }
}
