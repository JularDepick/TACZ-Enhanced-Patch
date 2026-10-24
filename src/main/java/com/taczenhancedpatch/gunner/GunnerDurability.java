package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 枪手耐久。
 * 生命上限与护甲韧性按分档叠加, 四件护甲则按部位独立随机:
 * 每个部位先按生成时的持械概率决定是否穿戴, 再按皮革到合金的等比递减抽取材质,
 * 因此四个部位可以出现不同材质, 也可以空缺。加成只在判定成为枪手后施加一次,
 * 由身份标记中的标志位防止重复叠加。
 */
public final class GunnerDurability {
    private static final UUID HEALTH_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_health".getBytes(StandardCharsets.UTF_8));
    private static final UUID ARMOR_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_armor".getBytes(StandardCharsets.UTF_8));
    private static final UUID TOUGHNESS_MODIFIER_ID = UUID.nameUUIDFromBytes("tacz_enhanced_patch:gunner_toughness".getBytes(StandardCharsets.UTF_8));

    /* 参与随机抽取的四个护甲部位, 顺序固定便于对照 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private GunnerDurability() {
    }

    public static void apply(Mob mob, GunnerArsenal.Tier tier) {
        GunnerArsenal.Durability durability = GunnerArsenal.durability(tier);
        applyHealth(mob, durability.healthFactor());
        applyArmor(mob, durability.extraToughness());
        equipRandomArmor(mob);
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

    /**
     * 按部位独立抽取护甲。
     * 空部位的概率等于 1 减去生成时的持械概率(含难度缩放), 有护甲时材质从皮革到合金等比递减,
     * 四个部位互不影响, 因此同一只枪手可能混穿多种材质或只穿其中一两件。
     */
    private static void equipRandomArmor(Mob mob) {
        RandomSource random = mob.getRandom();
        Difficulty difficulty = mob.level().getCurrentDifficultyAt(mob.blockPosition()).getDifficulty();
        double chance = GunnerSpawnHandler.spawnChance(difficulty);
        float dropChance = (float) ServerConfig.gunnerArmorDropChance();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (random.nextDouble() >= chance) {
                continue;
            }
            GunnerArsenal.ArmorMaterial material = GunnerArsenal.rollArmorMaterial(random);
            mob.setItemSlot(slot, new ItemStack(itemFor(slot, material)));
            mob.setDropChance(slot, dropChance);
        }
    }

    /* 部位与材质的组合, 覆盖皮革, 锁链, 铁, 金, 钻石与下界合金六种 */
    private static Item itemFor(EquipmentSlot slot, GunnerArsenal.ArmorMaterial material) {
        return switch (material) {
            case LEATHER -> switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
            case CHAINMAIL -> switch (slot) {
                case HEAD -> Items.CHAINMAIL_HELMET;
                case CHEST -> Items.CHAINMAIL_CHESTPLATE;
                case LEGS -> Items.CHAINMAIL_LEGGINGS;
                default -> Items.CHAINMAIL_BOOTS;
            };
            case IRON -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            };
            case GOLD -> switch (slot) {
                case HEAD -> Items.GOLDEN_HELMET;
                case CHEST -> Items.GOLDEN_CHESTPLATE;
                case LEGS -> Items.GOLDEN_LEGGINGS;
                default -> Items.GOLDEN_BOOTS;
            };
            case DIAMOND -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            };
            case NETHERITE -> switch (slot) {
                case HEAD -> Items.NETHERITE_HELMET;
                case CHEST -> Items.NETHERITE_CHESTPLATE;
                case LEGS -> Items.NETHERITE_LEGGINGS;
                default -> Items.NETHERITE_BOOTS;
            };
        };
    }
}
