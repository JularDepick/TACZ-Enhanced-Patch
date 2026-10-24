package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 满弹匣换弹拦截。
 * 上游在弹匣已满时仍会响应换弹按键, 白白播一次换弹动画;
 * 这里在换弹刚开始的瞬间检查弹匣, 只有弹匣已满才取消, 不干扰正常的半仓与空仓换弹。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class ReloadGuard {
    private static final Map<LivingEntity, Long> LAST_START =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ReloadGuard() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        ItemStack gun = entity.getMainHandItem();
        if (!TaczApi.isGun(gun)) {
            LAST_START.remove(entity);
            return;
        }
        long start = TaczApi.reloadStartTimestamp(entity);
        if (start < 0L) {
            LAST_START.remove(entity);
            return;
        }
        Long previous = LAST_START.put(entity, start);
        if (previous != null && previous == start) {
            return;
        }
        int capacity = TaczApi.maxMagazineAmmo(gun);
        if (capacity > 0 && TaczApi.magazineAmmo(gun) >= capacity) {
            TaczApi.cancelReload(entity);
        }
    }
}
