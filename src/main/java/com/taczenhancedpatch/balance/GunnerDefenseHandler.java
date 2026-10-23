package com.taczenhancedpatch.balance;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪手受伤平衡。
 * 只下调玩家枪械打向枪手的伤害, 让枪手在 TACZ 的高单发伤害下需要数次命中才倒下,
 * 枪手之间的误伤, 近战伤害与爆炸伤害不受影响。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerDefenseHandler {
    private GunnerDefenseHandler() {
    }

    @SubscribeEvent
    public static void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient() || !ServerConfig.gunnerEnabled()) {
            return;
        }
        Entity hurt = event.getHurtEntity();
        if (!(hurt instanceof LivingEntity living) || !GunnerMarker.isGunner(living)) {
            return;
        }
        if (!(event.getAttacker() instanceof Player)) {
            return;
        }
        float multiplier = (float) ServerConfig.gunnerDamageTakenMultiplier();
        event.setBaseAmount(Math.max(0.0F, event.getBaseAmount() * multiplier));
    }
}
