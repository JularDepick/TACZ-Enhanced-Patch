package com.taczenhancedpatch.item;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 无限弹匣的弹药维持。
 * 服务端每 tick 检查手持枪械的扩容弹匣, 带无限标记时把弹匣补满,
 * 因此开火与拉栓都不会真正减少子弹; 使用背包弹药的枪械改为消耗虚拟备弹,
 * 虚拟备弹同样每 tick 补满, 背包里的弹药不会被消耗。弹匣常满时上游也不会再发起换弹。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class InfiniteMagHandler {
    /* 虚拟备弹池大小, 只要大于一局的射击次数即可视为无限 */
    private static final int DUMMY_POOL = 9999;

    private InfiniteMagHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        ItemStack gun = entity.getMainHandItem();
        if (!TaczApi.isGun(gun)) {
            return;
        }
        if (!InfiniteMag.isInfinite(TaczApi.getExtendedMag(gun))) {
            return;
        }
        int capacity = TaczApi.maxMagazineAmmo(gun);
        if (capacity > 0 && TaczApi.magazineAmmo(gun) != capacity) {
            TaczApi.setMagazineAmmo(gun, capacity);
        }
        if (TaczApi.usesInventoryAmmo(gun)) {
            TaczApi.fillDummyAmmo(gun, DUMMY_POOL);
        }
    }
}
