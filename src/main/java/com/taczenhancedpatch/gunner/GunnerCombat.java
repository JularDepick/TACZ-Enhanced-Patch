package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
        if (!ServerConfig.gunnerEnabled()) {
            return;
        }
        tick(mob);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        STATES.remove(event.getEntity());
    }

    /**
     * 受击索敌。
     * 枪手被任何生物或玩家打中时, 若攻击者在锁定范围内就立刻把目标换成攻击者,
     * 之后按正常的反应时间与点射节奏还击。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        if (!ServerConfig.gunnerEnabled() || !GunnerMarker.isGunner(mob)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == mob) {
            return;
        }
        if (attacker.level() != mob.level()) {
            return;
        }
        double range = ServerConfig.gunnerTargetRange();
        if (mob.distanceToSqr(attacker) > range * range) {
            return;
        }
        retaliate(mob, attacker);
    }

    /* 供临场武装复用: 直接把目标设为攻击者, 重置反应与点射节奏 */
    static void retaliate(Mob mob, @Nullable LivingEntity attacker) {
        if (attacker == null) {
            return;
        }
        GunnerState state = STATES.computeIfAbsent(mob, key -> new GunnerState());
        state.target = attacker;
        state.reactionTicks = randomRange(mob, ServerConfig.gunnerReactionTicks());
        state.pauseTicks = 0;
        state.shotsInBurst = 0;
        state.retargetTicks = RETARGET_INTERVAL;
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
            state.burstSize = randomRange(mob, ServerConfig.gunnerBurstSize());
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
                    state.burstSize = randomRange(mob, ServerConfig.gunnerBurstSize());
                    state.pauseTicks = randomRange(mob, ServerConfig.gunnerBurstPause());
                }
            }
            case NO_AMMO -> {
                if (TaczApi.dummyAmmo(gun) > 0) {
                    TaczApi.reload(mob);
                    state.pauseTicks = 4;
                } else {
                    GunnerMarker.setDry(mob, true);
                    TaczApi.aim(mob, false);
                    if (ServerConfig.debugEvents()) {
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
        boolean valid = state.target != null && isValidTarget(mob, state.target);
        /* 非玩家目标来自受击反击, 只要还有效就不主动换成路过玩家 */
        boolean keepRetaliation = valid && !(state.target instanceof Player);
        boolean needRetarget = state.target == null || !valid || (state.retargetTicks <= 0 && !keepRetaliation);
        if (!needRetarget) {
            return;
        }
        LivingEntity found = findTarget(mob);
        if (found != state.target) {
            state.target = found;
            state.reactionTicks = randomRange(mob, ServerConfig.gunnerReactionTicks());
            state.pauseTicks = 0;
            state.shotsInBurst = 0;
        }
        state.retargetTicks = RETARGET_INTERVAL;
    }

    @Nullable
    private static LivingEntity findTarget(Mob mob) {
        double range = ServerConfig.gunnerTargetRange();
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
        if (target == mob || target.level() != mob.level() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && !isValidPlayer(player)) {
            return false;
        }
        double range = ServerConfig.gunnerFireRange();
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
        double errorDegrees = (1.0D - ServerConfig.gunnerAccuracy()) * GunnerArsenal.MAX_AIM_ERROR_DEGREES;
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
