package com.taczenhancedpatch.fastmag;

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
 * 快速扩容弹匣的换弹加速。
 * 换弹期间按目标比例把换弹开始时间戳持续前移, 使上游的换弹状态机按更短的总时长推进,
 * 装填与收尾两个阶段同时生效, 客户端动画与服务端计时一起加速。
 * 目标比例 = 缺失弹药占弹匣容量的比例 × 容量档位比例, 每 tick 按实际经过的时间换算。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class FastMagReloadHandler {
    /* 两次调整之间的最大时间差, 避免实体长时间未 tick 后一次性跳过整个换弹 */
    private static final long MAX_DELTA_MILLIS = 250L;

    /* 记录每只实体上一次计算加速的时间点, 客户端与服务端共用一张线程安全的弱表 */
    private static final Map<LivingEntity, Long> LAST_TICK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FastMagReloadHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        ItemStack gun = entity.getMainHandItem();
        if (!TaczApi.isGun(gun)) {
            LAST_TICK.remove(entity);
            return;
        }
        int level = FastMag.level(TaczApi.getExtendedMag(gun));
        if (level <= 0) {
            LAST_TICK.remove(entity);
            return;
        }
        long start = TaczApi.reloadStartTimestamp(entity);
        if (start < 0L) {
            LAST_TICK.remove(entity);
            return;
        }
        long now = System.currentTimeMillis();
        Long last;
        synchronized (LAST_TICK) {
            last = LAST_TICK.put(entity, now);
        }
        if (last == null) {
            return;
        }
        long delta = Math.min(MAX_DELTA_MILLIS, now - last);
        if (delta <= 0L) {
            return;
        }
        double scale = FastMag.reloadScale(gun, level);
        if (scale >= 1.0D) {
            return;
        }
        /* 目标比例 scale 下, 每经过 delta 需要额外推进 delta * (1/scale - 1) */
        double extra = 1.0D / scale - 1.0D;
        TaczApi.advanceReloadStart(entity, Math.round(delta * extra));
    }
}
