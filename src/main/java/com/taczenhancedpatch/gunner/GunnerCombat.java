package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.CommonConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 枪手战斗逻辑。
 * 通过 TACZ 公开的 IGunOperator 驱动真实的射击与换弹, 自身只负责索敌, 瞄准与开火节奏。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerCombat {
    private static final Map<LivingEntity, GunnerState> STATES = new WeakHashMap<>();
    private static final int RETARGET_INTERVAL = 10;

    private GunnerCombat() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob.level().isClientSide() || !GunnerMarker.isGunner(mob)) {
            return;
        }
        if (!CommonConfig.gunnerEnabled()) {
            return;
        }
        tick(mob);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        STATES.remove(event.getEntity());
    }

    private static void tick(Mob mob) {
        ItemStack gun = mob.getMainHandItem();
        if (!TaczApi.isGun(gun)) {
            GunnerMarker.clear(mob);
            STATES.remove(mob);
            return;
        }
        GunnerState state = STATES.computeIfAbsent(mob, key -> new GunnerState());
        ResourceLocation gunId = TaczApi.getGunId(gun);
        if (!state.initialized || gunId == null || !gunId.equals(state.gunId)) {
            TaczApi.initOperator(mob);
            state.initialized = true;
            state.gunId = gunId;
            state.burstSize = randomRange(mob, CommonConfig.gunnerBurstSize());
            GunnerMarker.setDry(mob, false);
        }
        if (GunnerMarker.isDry(mob)) {
            if (TaczApi.dummyAmmo(gun) > 0 || TaczApi.magazineAmmo(gun) > 0) {
                GunnerMarker.setDry(mob, false);
            } else {
                return;
            }
        }
        updateTarget(mob, state);
        LivingEntity target = state.target;
        if (target == null) {
            TaczApi.aim(mob, false);
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (state.pauseTicks > 0) {
            state.pauseTicks--;
            return;
        }
        if (state.reactionTicks > 0) {
            state.reactionTicks--;
            TaczApi.aim(mob, true);
            return;
        }
        TaczApi.aim(mob, true);
        if (mob.isSprinting()) {
            mob.setSprinting(false);
        }
        float[] aim = computeAim(mob, target);
        TaczApi.ShotOutcome result = TaczApi.shoot(mob, aim[0], aim[1]);
        handleResult(mob, gun, state, result);
    }

    private static void handleResult(Mob mob, ItemStack gun, GunnerState state, TaczApi.ShotOutcome result) {
        switch (result) {
            case SUCCESS -> {
                state.shotsInBurst++;
                if (state.shotsInBurst >= state.burstSize) {
                    state.shotsInBurst = 0;
                    state.burstSize = randomRange(mob, CommonConfig.gunnerBurstSize());
                    state.pauseTicks = randomRange(mob, CommonConfig.gunnerBurstPause());
                }
            }
            case NO_AMMO -> {
                if (TaczApi.dummyAmmo(gun) > 0) {
                    TaczApi.reload(mob);
                    state.pauseTicks = 4;
                } else {
                    GunnerMarker.setDry(mob, true);
                    TaczApi.aim(mob, false);
                    if (CommonConfig.debugEvents()) {
                        TACZEnhancedPatch.LOGGER.info("[gunner] {} ran out of ammo", mob.getType());
                    }
                }
            }
            case INVALID -> {
                GunnerMarker.clear(mob);
                STATES.remove(mob);
            }
            default -> {
            }
        }
    }

    private static void updateTarget(Mob mob, GunnerState state) {
        state.retargetTicks--;
        boolean needRetarget = state.target == null || !isValidTarget(mob, state.target) || state.retargetTicks <= 0;
        if (!needRetarget) {
            return;
        }
        LivingEntity found = findTarget(mob);
        if (found != state.target) {
            state.target = found;
            state.reactionTicks = randomRange(mob, CommonConfig.gunnerReactionTicks());
            state.pauseTicks = 0;
            state.shotsInBurst = 0;
        }
        state.retargetTicks = RETARGET_INTERVAL;
    }

    @Nullable
    private static LivingEntity findTarget(Mob mob) {
        double range = CommonConfig.gunnerFireRange();
        double rangeSqr = range * range;
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mob.level().players()) {
            if (!isValidPlayer(player)) {
                continue;
            }
            double distance = mob.distanceToSqr(player);
            if (distance > rangeSqr || distance >= bestDistance) {
                continue;
            }
            if (!mob.hasLineOfSight(player)) {
                continue;
            }
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean isValidTarget(Mob mob, LivingEntity target) {
        if (!(target instanceof Player player) || !isValidPlayer(player)) {
            return false;
        }
        if (target.level() != mob.level()) {
            return false;
        }
        double range = CommonConfig.gunnerFireRange();
        return mob.distanceToSqr(target) <= range * range && mob.hasLineOfSight(target);
    }

    private static boolean isValidPlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private static float[] computeAim(Mob mob, LivingEntity target) {
        Vec3 eye = mob.getEyePosition();
        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        Vec3 direction = aimPoint.subtract(eye);
        double length = direction.length();
        if (length < 1.0E-4D) {
            direction = mob.getLookAngle();
        } else {
            direction = direction.scale(1.0D / length);
        }
        double errorDegrees = CommonConfig.gunnerAimErrorDegrees();
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z))
                + mob.getRandom().nextGaussian() * errorDegrees;
        double pitch = Math.toDegrees(-Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)))
                + mob.getRandom().nextGaussian() * errorDegrees * 0.6D;
        return new float[]{(float) pitch, (float) yaw};
    }

    private static int randomRange(Mob mob, int[] range) {
        return range[0] >= range[1] ? range[0] : Mth.nextInt(mob.getRandom(), range[0], range[1]);
    }
}
