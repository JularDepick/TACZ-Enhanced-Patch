package com.taczenhancedpatch.loot;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.CommonConfig;
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

import java.util.Optional;

/**
 * 枪手战利品。
 * 击杀枪手获得对应口径弹药, 少量枪械与配件, 形成以战养战的补给闭环。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class BattleLootHandler {
    private static final int MAX_AMMO_PER_DROP = 64;
    private static final int LOOTING_AMMO_BONUS = 2;

    private BattleLootHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!CommonConfig.gunnerEnabled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(dead);
        if (loadout == null) {
            return;
        }
        RandomSource random = dead.getRandom();

        /* 枪械掉落统一由本模组接管, 移除原版概率掉落, 避免重复 */
        event.getDrops().removeIf(drop -> {
            ResourceLocation gunId = TaczApi.getGunId(drop.getItem());
            return gunId != null && gunId.equals(loadout.gunId());
        });

        if (random.nextDouble() < CommonConfig.gunnerAmmoDropChance()) {
            int base = Mth.nextInt(random, loadout.ammoDropMin(), loadout.ammoDropMax());
            int bonus = event.getLootingLevel() * LOOTING_AMMO_BONUS;
            int count = (int) Math.round((base + bonus) * CommonConfig.gunnerAmmoDropMultiplier());
            count = Mth.clamp(count, 1, MAX_AMMO_PER_DROP);
            addDrop(dead, TaczApi.createAmmo(loadout.ammoId(), count), event);
        }

        if (random.nextDouble() < CommonConfig.gunnerGunDropChance()) {
            addDrop(dead, TaczApi.createGun(loadout.gunId(), loadout.fireMode(), 0), event);
        }

        if (random.nextDouble() < CommonConfig.gunnerAttachmentDropChance()) {
            Optional<ItemStack> attachment = TaczApi.randomAttachment(random);
            attachment.ifPresent(stack -> addDrop(dead, stack, event));
        }

        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[gunner] loot from {}: ammo {}", dead.getType(), loadout.ammoId());
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
