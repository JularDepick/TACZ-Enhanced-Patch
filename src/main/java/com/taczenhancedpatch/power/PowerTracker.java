package com.taczenhancedpatch.power;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 战斗峰值战力记录。
 * 每次玩家命中生物时记录该玩家当时的战力峰值, 结算掉落时取峰值而不是结算瞬间的战力,
 * 避免玩家在补刀前把装备收进箱子压低自身战力来抬高翻倍系数。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class PowerTracker {
    private static final String TAG_PEAKS = "tacz_enhanced_patch_attacker_power";

    private PowerTracker() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        CompoundTag peaks = victim.getPersistentData().getCompound(TAG_PEAKS);
        String key = attacker.getUUID().toString();
        double power = CombatPower.of(attacker);
        if (power <= peaks.getDouble(key)) {
            return;
        }
        peaks.putDouble(key, power);
        victim.getPersistentData().put(TAG_PEAKS, peaks);
    }

    /* 该玩家对这只生物造成的战力峰值, 没有记录时为零 */
    public static double peakPower(LivingEntity victim, UUID attacker) {
        CompoundTag peaks = victim.getPersistentData().getCompound(TAG_PEAKS);
        return peaks.getDouble(attacker.toString());
    }
}
