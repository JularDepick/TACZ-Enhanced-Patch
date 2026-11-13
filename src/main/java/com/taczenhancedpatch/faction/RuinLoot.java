package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 夺旗战利品的换算与发放。
 * 弹药按数量折算, 武器按分档价值折算, 两者都受快照总量上限约束;
 * 经验按击杀数与伤害占比分配, 离线玩家由 RuinExperience 暂存。
 */
public final class RuinLoot {
    private static final int CHEST_SLOTS = 27;
    private static final int MAX_AMMO_PER_SLOT = 64;
    /* 武器价值折算成战利品强度的倍数 */
    private static final int WEAPON_STRENGTH = 20;
    /* 附魔: 每件独立判定, 条数与等级随强度提升, 各有上限 */
    private static final float ENCHANT_CHANCE = 0.5F;
    private static final int ENCHANT_COUNT_DIVISOR = 150;
    private static final int ENCHANT_LEVEL_DIVISOR = 400;
    private static final int MAX_ENCHANTS = 4;
    private static final int MAX_ENCHANT_LEVEL = 3;
    /* 末影之眼与面包 */
    private static final float ENDER_EYE_CHANCE = 0.5F;
    private static final int BREAD_BASE = 8;
    private static final int BREAD_DIVISOR = 40;
    /* 护甲与剑的材料池, 顺序与权重一一对应 */
    private static final String[] MATERIAL_NAMES = {"iron", "diamond", "chainmail", "gold", "leather", "netherite"};
    private static final int[] MATERIAL_WEIGHTS = {30, 25, 15, 15, 10, 5};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private RuinLoot() {
    }

    /* 武器价值取分档序号加一, 低阶一, 中阶二, 高阶三 */
    public static int weaponValue(@Nullable GunnerArsenal.Tier tier) {
        return tier == null ? 1 : tier.ordinal() + 1;
    }

    /* 单名玩家的经验, 夺旗发放与旗帜提示共用同一口径 */
    public static int experienceFor(RuinStats stats, UUID player) {
        float total = stats.totalDamage();
        float share = total <= 0.0F ? 0.0F : stats.damage().getOrDefault(player, 0.0F) / total;
        int kills = stats.killCount(player);
        double raw = ServerConfig.ruinXpPerKill() * kills + share * ServerConfig.ruinXpDamagePool();
        return (int) Math.round(raw * ServerConfig.ruinXpScale());
    }

    /* 在核心区生成战利品箱并填充 */
    public static void placeChest(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos pos = findChestSpot(level, site, random);
        if (pos == null) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] no chest spot at {}", site.origin());
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        List<ItemStack> items = buildLoot(site, random);
        int slot = 0;
        for (ItemStack stack : items) {
            if (slot >= CHEST_SLOTS) {
                TACZEnhancedPatch.LOGGER.info("[ruin] loot overflow at {}, {} stacks dropped",
                        site.origin(), items.size() - CHEST_SLOTS);
                break;
            }
            chest.setItem(slot++, stack);
        }
    }

    /* 核心区内找一个无方块的地面位置 */
    @Nullable
    private static BlockPos findChestSpot(ServerLevel level, RuinSites.Site site, RandomSource random) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return null;
        }
        for (int attempt = 0; attempt < 48; attempt++) {
            int radius = 1 + random.nextInt((int) RuinTerritory.CORE_RADIUS);
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos base = banner.offset(dx, 0, dz);
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos candidate = base.offset(0, dy, 0);
                if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()) {
                    continue;
                }
                BlockPos below = candidate.below();
                if (level.getBlockState(below).isSolidRender(level, below)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /* 弹药按口径与武器按价值分别折算, 结果不超过快照上限; 必定项先占格, 食物填满剩余格子 */
    private static List<ItemStack> buildLoot(RuinSites.Site site, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        RuinStats stats = site.stats();
        double scale = ServerConfig.ruinLootScale();
        int strength = stats == null ? 0 : lootStrength(stats, scale);
        items.addAll(guaranteed(strength, random));
        if (stats == null) {
            return items;
        }
        Map<String, Integer> killAmmo = killAmmo(stats);
        for (Map.Entry<String, Integer> entry : killAmmo.entrySet()) {
            int snapshot = stats.snapshotAmmo().getOrDefault(entry.getKey(), 0);
            int amount = (int) Math.floor(scale * entry.getValue());
            amount = Math.min(amount, (int) Math.floor(scale * snapshot));
            if (amount <= 0) {
                continue;
            }
            ResourceLocation ammoId = ResourceLocation.tryParse(entry.getKey());
            if (ammoId == null) {
                continue;
            }
            int remaining = amount;
            while (remaining > 0 && items.size() < CHEST_SLOTS) {
                int stackSize = Math.min(remaining, MAX_AMMO_PER_SLOT);
                items.add(TaczApi.createAmmo(ammoId, stackSize));
                remaining -= stackSize;
            }
        }

        /* 武器按价值预算随机抽取, 单把价值不超过剩余预算 */
        int killValue = 0;
        for (RuinStats.Kill kill : stats.kills()) {
            killValue += kill.weaponValue();
        }
        int budget = (int) Math.floor(scale * Math.min(killValue, stats.snapshotWeaponValue()));
        List<RuinStats.Kill> pool = new ArrayList<>(stats.kills());
        while (budget > 0 && !pool.isEmpty() && items.size() < CHEST_SLOTS) {
            RuinStats.Kill kill = pool.remove(random.nextInt(pool.size()));
            if (kill.weapon().isEmpty() || kill.weaponValue() > budget) {
                continue;
            }
            ItemStack gun = kill.weapon().copy();
            TaczApi.clearDummyAmmo(gun);
            items.add(gun);
            budget -= kill.weaponValue();
        }

        /* 食物最后填, 装不下就到此为止 */
        int extraFood = random.nextInt(4);
        for (int i = 0; i < extraFood && items.size() < CHEST_SLOTS; i++) {
            items.add(randomFood(random));
        }
        return items;
    }

    /* 战利品强度: 折算弹药总发数加折算武器总价值乘二十, 供附魔与食物取值 */
    private static int lootStrength(RuinStats stats, double scale) {
        int ammo = 0;
        for (Map.Entry<String, Integer> entry : killAmmo(stats).entrySet()) {
            int snapshot = stats.snapshotAmmo().getOrDefault(entry.getKey(), 0);
            ammo += Math.min((int) Math.floor(scale * entry.getValue()),
                    (int) Math.floor(scale * snapshot));
        }
        int weapon = (int) Math.floor(scale * Math.min(killValue(stats), stats.snapshotWeaponValue()));
        return ammo + weapon * WEAPON_STRENGTH;
    }

    private static Map<String, Integer> killAmmo(RuinStats stats) {
        Map<String, Integer> ammo = new LinkedHashMap<>();
        for (RuinStats.Kill kill : stats.kills()) {
            for (Map.Entry<String, Integer> entry : kill.ammo().entrySet()) {
                ammo.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return ammo;
    }

    private static int killValue(RuinStats stats) {
        int value = 0;
        for (RuinStats.Kill kill : stats.kills()) {
            value += kill.weaponValue();
        }
        return value;
    }

    /* 必定项: 同材料护甲一套, 独立材料的一把剑, 末影珍珠, 概率末影之眼, 面包 */
    private static List<ItemStack> guaranteed(int strength, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        String armorMaterial = pickMaterial(random);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = new ItemStack(armorItem(armorMaterial, slot));
            maybeEnchant(stack, armorPool(slot), strength, random);
            items.add(stack);
        }
        ItemStack sword = new ItemStack(swordItem(pickMaterial(random)));
        maybeEnchant(sword, swordPool(), strength, random);
        items.add(sword);

        items.add(new ItemStack(Items.ENDER_PEARL, 1 + random.nextInt(16)));
        if (random.nextFloat() < ENDER_EYE_CHANCE) {
            items.add(new ItemStack(Items.ENDER_EYE, 1 + random.nextInt(8)));
        }
        int bread = BREAD_BASE + strength / BREAD_DIVISOR;
        while (bread > 0 && items.size() < CHEST_SLOTS) {
            int count = Math.min(bread, 64);
            items.add(new ItemStack(Items.BREAD, count));
            bread -= count;
        }
        return items;
    }

    /* 每件装备独立判定是否带附魔, 条数与等级由强度决定, 且不超过该附魔自身上限 */
    private static void maybeEnchant(ItemStack stack, List<Enchantment> pool, int strength, RandomSource random) {
        if (random.nextFloat() >= ENCHANT_CHANCE || pool.isEmpty()) {
            return;
        }
        int count = Math.min(MAX_ENCHANTS, 1 + strength / ENCHANT_COUNT_DIVISOR);
        int level = Math.min(MAX_ENCHANT_LEVEL, 1 + strength / ENCHANT_LEVEL_DIVISOR);
        List<Enchantment> candidates = new ArrayList<>(pool);
        Map<Enchantment, Integer> chosen = new LinkedHashMap<>();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            Enchantment enchantment = candidates.remove(random.nextInt(candidates.size()));
            chosen.put(enchantment, Math.min(level, enchantment.getMaxLevel()));
        }
        EnchantmentHelper.setEnchantments(chosen, stack);
    }

    private static List<Enchantment> armorPool(EquipmentSlot slot) {
        List<Enchantment> pool = new ArrayList<>(List.of(Enchantments.ALL_DAMAGE_PROTECTION,
                Enchantments.FIRE_PROTECTION, Enchantments.PROJECTILE_PROTECTION,
                Enchantments.BLAST_PROTECTION, Enchantments.THORNS, Enchantments.UNBREAKING));
        if (slot == EquipmentSlot.HEAD) {
            pool.add(Enchantments.RESPIRATION);
        }
        if (slot == EquipmentSlot.FEET) {
            pool.add(Enchantments.DEPTH_STRIDER);
        }
        return pool;
    }

    private static List<Enchantment> swordPool() {
        return new ArrayList<>(List.of(Enchantments.SHARPNESS, Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
                Enchantments.MOB_LOOTING, Enchantments.UNBREAKING, Enchantments.SWEEPING_EDGE));
    }

    /* 材料按权重抽取, 顺序为铁, 钻石, 锁链, 金, 皮革, 下界合金 */
    private static String pickMaterial(RandomSource random) {
        int total = 0;
        for (int weight : MATERIAL_WEIGHTS) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < MATERIAL_WEIGHTS.length; i++) {
            roll -= MATERIAL_WEIGHTS[i];
            if (roll < 0) {
                return MATERIAL_NAMES[i];
            }
        }
        return MATERIAL_NAMES[0];
    }

    private static Item armorItem(String material, EquipmentSlot slot) {
        return switch (material) {
            case "diamond" -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            };
            case "chainmail" -> switch (slot) {
                case HEAD -> Items.CHAINMAIL_HELMET;
                case CHEST -> Items.CHAINMAIL_CHESTPLATE;
                case LEGS -> Items.CHAINMAIL_LEGGINGS;
                default -> Items.CHAINMAIL_BOOTS;
            };
            case "gold" -> switch (slot) {
                case HEAD -> Items.GOLDEN_HELMET;
                case CHEST -> Items.GOLDEN_CHESTPLATE;
                case LEGS -> Items.GOLDEN_LEGGINGS;
                default -> Items.GOLDEN_BOOTS;
            };
            case "leather" -> switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
            case "netherite" -> switch (slot) {
                case HEAD -> Items.NETHERITE_HELMET;
                case CHEST -> Items.NETHERITE_CHESTPLATE;
                case LEGS -> Items.NETHERITE_LEGGINGS;
                default -> Items.NETHERITE_BOOTS;
            };
            default -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            };
        };
    }

    private static Item swordItem(String material) {
        return switch (material) {
            case "diamond" -> Items.DIAMOND_SWORD;
            case "gold" -> Items.GOLDEN_SWORD;
            case "leather", "chainmail" -> Items.STONE_SWORD;
            case "netherite" -> Items.NETHERITE_SWORD;
            default -> Items.IRON_SWORD;
        };
    }

    private static ItemStack randomFood(RandomSource random) {
        Item[] foods = {Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BAKED_POTATO,
                Items.CARROT, Items.APPLE};
        return new ItemStack(foods[random.nextInt(foods.length)], 4 + random.nextInt(5));
    }

    /* 按战绩发放经验, 在线直接给, 离线暂存 */
    public static void grantExperience(ServerLevel level, RuinSites.Site site) {
        RuinStats stats = site.stats();
        if (stats == null) {
            return;
        }
        for (UUID player : stats.names().keySet()) {
            int xp = experienceFor(stats, player);
            if (xp <= 0) {
                continue;
            }
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
            if (online != null) {
                online.giveExperiencePoints(xp);
                online.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "tacz_enhanced_patch.ruin.xp", xp));
            } else {
                RuinExperience.add(level, player, xp);
            }
        }
    }
}
