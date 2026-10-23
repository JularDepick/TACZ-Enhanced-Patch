package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 枪手耐久。
 * 生命上限, 护甲与护甲韧性按分档叠加, 并穿上对应材质的四件护甲,
 * 让枪手在 TACZ 的高单发伤害下需要数次命中才会倒下。
 * 加成只在判定成为枪手后施加一次, 由身份标记中的标志位防止重复叠加。
 */
public final class GunnerDurability {
    private static final UUID HEALTH_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_health".getBytes(StandardCharsets.UTF_8));
    private static final UUID ARMOR_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_armor".getBytes(StandardCharsets.UTF_8));
    private static final UUID TOUGHNESS_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_toughness".getBytes(StandardCharsets.UTF_8));

    private GunnerDurability() {
    }

    public static void apply(Mob mob, GunnerArsenal.Tier tier) {
        GunnerArsenal.Durability durability = GunnerArsenal.durability(tier);
        applyHealth(mob, durability.healthFactor());
        applyArmor(mob, durability.extraToughness());
        equipArmor(mob, durability.armorTier());
    }

    private static void applyHealth(Mob mob, double healthFactor) {
        AttributeInstance attribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double factor = Math.max(1.0D, ServerConfig.gunnerHealthMultiplier() * healthFactor);
        attribute.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_ID,
                "tacz_enhanced_patch_gunner_health", factor - 1.0D, AttributeModifier.Operation.MULTIPLY_BASE));
        mob.setHealth(mob.getMaxHealth());
    }

    private static void applyArmor(Mob mob, int extraToughness) {
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        int armorBonus = ServerConfig.gunnerArmorBonus();
        if (armor != null && armorBonus > 0) {
            armor.addPermanentModifier(new AttributeModifier(ARMOR_MODIFIER_ID,
                    "tacz_enhanced_patch_gunner_armor", armorBonus, AttributeModifier.Operation.ADDITION));
        }
        AttributeInstance toughness = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null && extraToughness > 0) {
            toughness.addPermanentModifier(new AttributeModifier(TOUGHNESS_MODIFIER_ID,
                    "tacz_enhanced_patch_gunner_toughness", extraToughness, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void equipArmor(Mob mob, GunnerArsenal.ArmorTier armorTier) {
        float dropChance = (float) ServerConfig.gunnerArmorDropChance();
        equip(mob, EquipmentSlot.HEAD, helmet(armorTier), dropChance);
        equip(mob, EquipmentSlot.CHEST, chestplate(armorTier), dropChance);
        equip(mob, EquipmentSlot.LEGS, leggings(armorTier), dropChance);
        equip(mob, EquipmentSlot.FEET, boots(armorTier), dropChance);
    }

    private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack, float dropChance) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, dropChance);
    }

    private static ItemStack helmet(GunnerArsenal.ArmorTier armorTier) {
        return new ItemStack(switch (armorTier) {
            case LEATHER -> Items.LEATHER_HELMET;
            case CHAIN -> Items.CHAINMAIL_HELMET;
            case IRON -> Items.IRON_HELMET;
        });
    }

    private static ItemStack chestplate(GunnerArsenal.ArmorTier armorTier) {
        return new ItemStack(switch (armorTier) {
            case LEATHER -> Items.LEATHER_CHESTPLATE;
            case CHAIN -> Items.CHAINMAIL_CHESTPLATE;
            case IRON -> Items.IRON_CHESTPLATE;
        });
    }

    private static ItemStack leggings(GunnerArsenal.ArmorTier armorTier) {
        return new ItemStack(switch (armorTier) {
            case LEATHER -> Items.LEATHER_LEGGINGS;
            case CHAIN -> Items.CHAINMAIL_LEGGINGS;
            case IRON -> Items.IRON_LEGGINGS;
        });
    }

    private static ItemStack boots(GunnerArsenal.ArmorTier armorTier) {
        return new ItemStack(switch (armorTier) {
            case LEATHER -> Items.LEATHER_BOOTS;
            case CHAIN -> Items.CHAINMAIL_BOOTS;
            case IRON -> Items.IRON_BOOTS;
        });
    }
}
