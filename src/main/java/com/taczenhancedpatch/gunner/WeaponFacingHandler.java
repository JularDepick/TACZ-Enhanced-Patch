package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 持械生物面向目标。
 * 手持枪械且有有效目标的生物每隔几 tick 把视线对准目标,
 * 头部与身体随视线转动, 避免出现背对目标开枪的观感问题。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class WeaponFacingHandler {
    private static final int FACE_INTERVAL = 5;
    private static final float TURN_SPEED = 30.0F;
    private static final double MAX_DISTANCE = 64.0D;

    private WeaponFacingHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % FACE_INTERVAL != 0) {
            return;
        }
        if (!TaczApi.isGun(mob.getMainHandItem())) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (mob.distanceToSqr(target) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        mob.getLookControl().setLookAt(target, TURN_SPEED, TURN_SPEED);
    }
}
