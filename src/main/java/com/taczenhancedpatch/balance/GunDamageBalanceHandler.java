package com.taczenhancedpatch.balance;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.CommonConfig;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪手伤害平衡。
 * TACZ 的原始伤害面向玩家对怪物设计, 直接交给敌对生物会过强,
 * 这里只对枪手发射的子弹做倍率修正, 不改变玩家自身的枪械手感。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunDamageBalanceHandler {
    private static final float MAX_HEADSHOT_MULTIPLIER = 1.35F;

    private GunDamageBalanceHandler() {
    }

    @SubscribeEvent
    public static void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient() || !CommonConfig.gunnerEnabled()) {
            return;
        }
        LivingEntity attacker = event.getAttacker();
        if (attacker == null || attacker instanceof Player || !GunnerMarker.isGunner(attacker)) {
            return;
        }
        float multiplier = (float) CommonConfig.gunnerDamageMultiplier();
        if (multiplier > 0.0F) {
            event.setBaseAmount(event.getBaseAmount() * multiplier);
        }
        if (event.isHeadShot()) {
            event.setHeadshotMultiplier(Math.min(event.getHeadshotMultiplier(), MAX_HEADSHOT_MULTIPLIER));
        }
    }
}
