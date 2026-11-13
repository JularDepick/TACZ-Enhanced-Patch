package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 中立持械村民的反击判定。
 * 玩家攻击只有把生命值从阈值以上压到阈值以下才反击, 且回看前 10 秒内最近的玩家攻击;
 * 生物攻击只要生命值不高于生物阈值就立即反击, 敌对生物用枪持续还击, 温和生物只做一次近战反击。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class VillagerRetaliation {
    private static final String TAG_PLAYER = "tacz_enhanced_patch_villager_player";
    private static final String TAG_TICK = "tacz_enhanced_patch_villager_tick";
    /* 回看窗口, 单位 tick */
    private static final long LOOKBACK_TICKS = 200L;
    /* 温和生物反击的最低伤害, 村民自身攻击力为零时兜底 */
    private static final float MIN_MELEE_DAMAGE = 2.0F;

    private VillagerRetaliation() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()) {
            return;
        }
        if (!ArmedVillager.isArmed(villager)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) {
            rememberPlayer(villager, player);
            if (crossedPlayerThreshold(villager, event.getAmount())) {
                Player target = recentPlayer(villager);
                if (target != null) {
                    retaliateWithGun(villager, target);
                }
            }
            return;
        }
        if (source instanceof Mob attacker) {
            retaliateMob(villager, attacker, event.getAmount());
        }
    }

    /* 生命值从阈值以上跨到阈值以下才算越线 */
    private static boolean crossedPlayerThreshold(Villager villager, float amount) {
        float before = villager.getHealth();
        float after = before - amount;
        if (after <= 0.0F) {
            return false;
        }
        float threshold = villager.getMaxHealth() * (float) ServerConfig.villagerPlayerThreshold();
        return before > threshold && after <= threshold;
    }

    private static void rememberPlayer(Villager villager, Player player) {
        CompoundTag tag = villager.getPersistentData();
        tag.putUUID(TAG_PLAYER, player.getUUID());
        tag.putLong(TAG_TICK, villager.level().getGameTime());
    }

    /* 前 10 秒内最近的玩家攻击, 超出窗口视为没有 */
    @Nullable
    private static Player recentPlayer(Villager villager) {
        CompoundTag tag = villager.getPersistentData();
        if (!tag.contains(TAG_PLAYER) || !(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        if (level.getGameTime() - tag.getLong(TAG_TICK) > LOOKBACK_TICKS) {
            return null;
        }
        UUID id = tag.getUUID(TAG_PLAYER);
        Player player = level.getServer().getPlayerList().getPlayer(id);
        return player != null && player.isAlive() ? player : null;
    }

    /* 敌对生物用枪持续还击, 温和生物只做一次近战反击 */
    private static void retaliateMob(Villager villager, Mob attacker, float amount) {
        float after = villager.getHealth() - amount;
        if (after <= 0.0F) {
            return;
        }
        if (after > villager.getMaxHealth() * (float) ServerConfig.villagerMobThreshold()) {
            return;
        }
        if (attacker instanceof Enemy) {
            retaliateWithGun(villager, attacker);
            return;
        }
        /* 村民没有攻击力属性, 直接取会抛异常, 这里按缺失处理 */
        float damage = MIN_MELEE_DAMAGE;
        net.minecraft.world.entity.ai.attributes.AttributeInstance attack =
                villager.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            damage = Math.max(MIN_MELEE_DAMAGE, (float) attack.getValue());
        }
        attacker.hurt(villager.damageSources().mobAttack(villager), damage);
    }

    private static void retaliateWithGun(Villager villager, net.minecraft.world.entity.LivingEntity target) {
        villager.setTarget(target);
        villager.setLastHurtByMob(target);
    }
}
