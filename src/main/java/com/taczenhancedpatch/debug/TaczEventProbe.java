package com.taczenhancedpatch.debug;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.CommonConfig;
import com.taczenhancedpatch.integration.TaczApi;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * TACZ API 事件探针。
 * 仅在通用配置 debugEvents 打开时输出日志, 默认关闭, 不改变任何游戏行为。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class TaczEventProbe {
    private TaczEventProbe() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[probe] GunFire side={} gun={} shooter={}",
                    event.getLogicalSide(), gunId(event.getGunItemStack()), name(event.getShooter()));
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[probe] GunShoot side={} gun={} shooter={}",
                    event.getLogicalSide(), gunId(event.getGunItemStack()), name(event.getShooter()));
        }
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[probe] GunReload side={} gun={} entity={}",
                    event.getLogicalSide(), gunId(event.getGunItemStack()), name(event.getEntity()));
        }
    }

    @SubscribeEvent
    public static void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[probe] GunHurtPre side={} gun={} attacker={} hurt={} amount={} headshot={}",
                    event.getLogicalSide(), event.getGunId(), name(event.getAttacker()), name(event.getHurtEntity()),
                    event.getBaseAmount(), event.isHeadShot());
        }
    }

    @SubscribeEvent
    public static void onGunKill(EntityKillByGunEvent event) {
        if (CommonConfig.debugEvents()) {
            TACZEnhancedPatch.LOGGER.info("[probe] GunKill side={} gun={} attacker={} killed={} damage={}",
                    event.getLogicalSide(), event.getGunId(), name(event.getAttacker()), name(event.getKilledEntity()),
                    event.getBaseDamage());
        }
    }

    private static String gunId(ItemStack stack) {
        ResourceLocation id = TaczApi.getGunId(stack);
        return id == null ? "unknown" : id.toString();
    }

    private static String name(@Nullable Entity entity) {
        return entity == null ? "null" : entity.getName().getString();
    }
}
