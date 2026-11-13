package com.taczenhancedpatch.power;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 战力量化评估。
 * 战力是一个把装备与状态折算成单一数值的统计量, 玩家与生物共用同一套口径:
 * 背包里的枪械, 弹药, 普通武器, 当前生命值, 以及空手攻击伤害各按固定权重累加。
 * 非持械生物没有背包, 因此战力只取决于空手攻击伤害, 可以为零。
 */
public final class CombatPower {
    /* 枪械: 每把基础分, 再按分档乘系数, 高阶枪械价值明显更高 */
    private static final double GUN_BASE = 100.0D;
    private static final double GUN_TIER_LOW = 0.6D;
    private static final double GUN_TIER_MID = 1.0D;
    private static final double GUN_TIER_HIGH = 1.5D;
    /* 弹药: 每发 */
    private static final double AMMO_UNIT = 0.8D;
    /* 普通武器: 每把 */
    private static final double WEAPON_MELEE = 12.0D;
    private static final double WEAPON_RANGED = 15.0D;
    private static final double WEAPON_TRIDENT = 20.0D;
    /* 当前生命值: 每点 */
    private static final double HEALTH_UNIT = 0.5D;
    /* 空手攻击伤害: 每点伤害 */
    private static final double FIST_UNIT = 0.25D;
    /* 上游弹药物品的注册名 */
    private static final String AMMO_ITEM_ID = "tacz:ammo";

    private CombatPower() {
    }

    /* 实体总战力, 空实体按零处理 */
    public static double of(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0.0D;
        }
        return gunScore(entity) + ammoScore(entity) + weaponScore(entity)
                + healthScore(entity) + fistScore(entity);
    }

    /* 掉落翻倍系数: 一加 持械生物战力 除以 击杀者战力, 并受配置上限约束 */
    public static double dropMultiplier(LivingEntity dead, @Nullable Player killer) {
        if (killer == null) {
            return 1.0D;
        }
        double victim = of(dead);
        /* 击杀者战力取本次战斗的峰值, 防止补刀前收起装备压低自身战力 */
        double attacker = Math.max(1.0D, Math.max(of(killer), PowerTracker.peakPower(dead, killer.getUUID())));
        double multiplier = 1.0D + victim / attacker;
        return Math.min(multiplier, Math.max(1.0D, ServerConfig.gunnerArmedDropCap()));
    }

    /* 枪械: 枪手读身份标记里的那把枪, 玩家扫背包 */
    private static double gunScore(LivingEntity entity) {
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(entity);
        if (loadout != null) {
            return GUN_BASE * tierFactor(loadout.tier());
        }
        double score = 0.0D;
        for (ItemStack stack : containers(entity)) {
            if (stack.isEmpty() || !TaczApi.isGun(stack)) {
                continue;
            }
            ResourceLocation gunId = TaczApi.getGunId(stack);
            GunnerArsenal.GunProfile profile = GunnerArsenal.byGunId(gunId);
            double factor = profile == null ? GUN_TIER_MID : tierFactor(profile.tier());
            score += GUN_BASE * factor * stack.getCount();
        }
        return score;
    }

    /* 弹药: 枪手读剩余备弹, 玩家按背包里的弹药物品计数 */
    private static double ammoScore(LivingEntity entity) {
        GunnerMarker.Loadout loadout = GunnerMarker.loadout(entity);
        if (loadout != null) {
            return Math.max(0, loadout.ammoPool()) * AMMO_UNIT;
        }
        int rounds = 0;
        for (ItemStack stack : containers(entity)) {
            if (isAmmo(stack)) {
                rounds += stack.getCount();
            }
        }
        return rounds * AMMO_UNIT;
    }

    /* 普通武器: 近战, 远程与三叉戟分档计分, 枪械不计入本项 */
    private static double weaponScore(LivingEntity entity) {
        double score = 0.0D;
        for (ItemStack stack : containers(entity)) {
            if (stack.isEmpty() || TaczApi.isGun(stack)) {
                continue;
            }
            double unit = 0.0D;
            if (stack.getItem() instanceof TridentItem) {
                unit = WEAPON_TRIDENT;
            } else if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                unit = WEAPON_MELEE;
            } else if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
                unit = WEAPON_RANGED;
            }
            if (unit > 0.0D) {
                score += unit * stack.getCount();
            }
        }
        return score;
    }

    private static double healthScore(LivingEntity entity) {
        return Math.max(0.0D, entity.getHealth()) * HEALTH_UNIT;
    }

    /* 空手攻击伤害取属性的基础值, 不含手持物品带来的加成 */
    private static double fistScore(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 0.0D : Math.max(0.0D, attack.getBaseValue()) * FIST_UNIT;
    }

    /* 玩家扫背包与副手, 生物扫主手与副手, 其余实体没有可扫描的容器 */
    private static List<ItemStack> containers(LivingEntity entity) {
        if (entity instanceof Player player) {
            java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().items);
            stacks.addAll(player.getInventory().offhand);
            return stacks;
        }
        if (entity instanceof Mob mob) {
            return List.of(mob.getMainHandItem(), mob.getOffhandItem());
        }
        return List.of();
    }

    private static boolean isAmmo(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && AMMO_ITEM_ID.equals(id.toString())) {
            return true;
        }
        return stack.getTag() != null && stack.getTag().contains("AmmoId");
    }

    private static double tierFactor(@Nullable GunnerArsenal.Tier tier) {
        if (tier == null) {
            return GUN_TIER_MID;
        }
        return switch (tier) {
            case LOW -> GUN_TIER_LOW;
            case HIGH -> GUN_TIER_HIGH;
            default -> GUN_TIER_MID;
        };
    }
}
