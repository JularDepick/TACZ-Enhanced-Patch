package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 枪手战斗逻辑。
 * 通过上游模组公开的 IGunOperator 驱动真实的射击与换弹, 自身只负责索敌, 瞄准与开火节奏。
 * 持有可用弹药时压制原版追击与近战, 让生物保持距离射击; 弹尽后才交回原版 AI 转入近战。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerCombat {
    private static final Map<LivingEntity, GunnerState> STATES = new WeakHashMap<>();
    private static final int RETARGET_INTERVAL = 10;
    private static final int RELOAD_ATTEMPT_INTERVAL = 8;

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
            GunnerState state = STATES.get(mob);
            if (state != null) {
                restoreTargeting(mob, state);
            }
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
        if (!shouldRetaliate(mob, attacker)) {
            return;
        }
        retaliate(mob, attacker);
    }

    /**
     * 是否应该反击伤害来源。
     * 强制反击开启时一律反击; 关闭时同族与盟友之间不互相攻击, 玩家始终视为敌对。
     */
    static boolean shouldRetaliate(Mob mob, LivingEntity attacker) {
        if (attacker == mob || attacker instanceof Player) {
            return true;
        }
        if (ServerConfig.gunnerForcedRetaliation()) {
            return true;
        }
        /* 关闭强制反击时, 同族与同队伍之间不互相攻击 */
        if (attacker.getType() == mob.getType()) {
            return false;
        }
        if (mob.getTeam() != null && mob.getTeam().isAlliedTo(attacker.getTeam())) {
            return false;
        }
        return true;
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
            GunnerState lost = STATES.get(mob);
            if (lost != null) {
                restoreTargeting(mob, lost);
            }
            /* 弹尽收起枪械后保留枪手标记, 战利品仍按枪手规则结算 */
            if (GunnerMarker.isGunner(mob) && GunnerMarker.isDry(mob)) {
                return;
            }
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
        /* 记录原版 AI 当前锁定的目标, 随后由本模组接管, 例如怪物对铁傀儡与村民的仇恨 */
        LivingEntity vanillaTarget = mob.getTarget();
        boolean canShoot = TaczApi.magazineAmmo(gun) > 0 || TaczApi.dummyAmmo(gun) > 0;
        if (!canShoot) {
            /* 弹尽: 把原版索敌装回去, 让它按原版方式追击与近战 */
            restoreTargeting(mob, state);
            if (!GunnerMarker.isDry(mob)) {
                GunnerMarker.setDry(mob, true);
                if (ServerConfig.debugEvents()) {
                    TACZEnhancedPatch.LOGGER.info("[gunner] {} ran out of ammo", mob.getType());
                }
            }
            /* 弹药耗尽即收起枪械, 交回原版武器与近战行为; 重复调用无副作用 */
            GunnerArming.disarm(mob);
            TaczApi.aim(mob, false);
            return;
        }
        if (GunnerMarker.isDry(mob)) {
            GunnerMarker.setDry(mob, false);
        }
        /* 还有弹药时摘掉原版索敌, 生物只按枪械规则行动, 不会冲向目标近战 */
        suppressTargeting(mob, state);
        mob.setTarget(null);
        updateTarget(mob, state, vanillaTarget);
        LivingEntity target = state.target;
        if (target == null) {
            TaczApi.aim(mob, false);
            return;
        }
        mob.getNavigation().stop();
        if (mob.isSprinting()) {
            mob.setSprinting(false);
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (TaczApi.magazineAmmo(gun) <= 0) {
            /* 弹匣打空且有备弹: 反复尝试换弹, 换弹期间不开火 */
            if (state.reloadTicks <= 0) {
                TaczApi.reload(mob);
                state.reloadTicks = RELOAD_ATTEMPT_INTERVAL;
            }
            state.reloadTicks--;
            TaczApi.aim(mob, true);
            return;
        }
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
        float[] aim = computeAim(mob, target);
        TaczApi.ShotOutcome result = TaczApi.shoot(mob, aim[0], aim[1]);
        if (result == TaczApi.ShotOutcome.SUCCESS) {
            GunnerEffects.shotVisual(mob, aim[0], aim[1]);
        }
        handleResult(mob, gun, state, result);
    }

    /* 摘掉原版的索敌目标选择器, 记录优先级以便弹尽后原样装回 */
    private static void suppressTargeting(Mob mob, GunnerState state) {
        if (state.targetGoalsRemoved) {
            return;
        }
        for (WrappedGoal wrapped : new ArrayList<>(mob.targetSelector.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            state.removedTargetGoals.add(new GunnerState.RemovedGoal(wrapped.getPriority(), goal));
            mob.targetSelector.removeGoal(goal);
        }
        state.targetGoalsRemoved = true;
    }

    private static void restoreTargeting(Mob mob, GunnerState state) {
        if (!state.targetGoalsRemoved) {
            return;
        }
        for (GunnerState.RemovedGoal removed : state.removedTargetGoals) {
            mob.targetSelector.addGoal(removed.priority(), removed.goal());
        }
        state.removedTargetGoals.clear();
        state.targetGoalsRemoved = false;
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

    private static void updateTarget(Mob mob, GunnerState state, @Nullable LivingEntity vanillaTarget) {
        boolean valid = state.target != null && isValidTarget(mob, state.target);
        if (!valid) {
            state.retargetTicks--;
            LivingEntity candidate = null;
            if (vanillaTarget != null && isValidTarget(mob, vanillaTarget)) {
                candidate = vanillaTarget;
            } else if (state.retargetTicks <= 0) {
                candidate = findTarget(mob);
                state.retargetTicks = RETARGET_INTERVAL;
            }
            if (candidate != null) {
                if (candidate != state.target) {
                    state.target = candidate;
                    state.reactionTicks = randomRange(mob, ServerConfig.gunnerReactionTicks());
                    state.pauseTicks = 0;
                    state.shotsInBurst = 0;
                }
            } else if (state.target != null) {
                state.target = null;
                state.shotsInBurst = 0;
            }
            return;
        }
        state.retargetTicks--;
        /* 非玩家目标来自受击反击或原版仇恨, 只要还有效就不主动换成路过玩家 */
        boolean keepNonPlayer = !(state.target instanceof Player);
        if (state.retargetTicks <= 0 && !keepNonPlayer) {
            LivingEntity found = findTarget(mob);
            if (found != null && found != state.target) {
                state.target = found;
                state.reactionTicks = randomRange(mob, ServerConfig.gunnerReactionTicks());
                state.pauseTicks = 0;
                state.shotsInBurst = 0;
            }
            state.retargetTicks = RETARGET_INTERVAL;
        }
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
