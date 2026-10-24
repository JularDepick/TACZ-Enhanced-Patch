package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 快速扩容弹夹的换弹加速。
 * 服务端在换弹期间按配件等级把换弹开始时间戳前移, 使换弹进度推进得更快,
 * 因此换弹总时长按比例缩短, 装填与收尾两个阶段一起生效。
 * 时间差按两次 tick 的实际间隔计算, 不依赖固定的 50 毫秒, 掉刻时也不会加速跑偏。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class FastMagReloadHandler {
    /* 两次调整之间的最大时间差, 避免实体长时间未 tick 后一次性跳过整个换弹 */
    private static final long MAX_DELTA_MILLIS = 250L;

    /* 记录每只实体上一次计算加速的时间点 */
    private static final Map<LivingEntity, Long> LAST_TICK = new WeakHashMap<>();

    private FastMagReloadHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
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
        Long last = LAST_TICK.put(entity, now);
        if (last == null) {
            return;
        }
        long delta = Math.min(MAX_DELTA_MILLIS, now - last);
        if (delta <= 0L) {
            return;
        }
        /* 目标倍率 r 下, 每经过 delta 需要额外推进 delta * (1/r - 1) */
        double extra = 1.0D / FastMag.reloadRatio(level) - 1.0D;
        TaczApi.advanceReloadStart(entity, Math.round(delta * extra));
    }
}
