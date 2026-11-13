package com.taczenhancedpatch.villager;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.gunner.GunnerArming;
import com.taczenhancedpatch.gunner.GunnerMarker;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 中立持械村民的状态与交易数据。
 * 村民没有背包, 这里在实体持久化数据里自建一份枪械与弹药库存, 以及每类商品的额度;
 * 额度在生成时定死, 只随交易减少, 库存清空即退化为原版村民。
 */
public final class ArmedVillager {
    private static final String ROOT = "tacz_enhanced_patch_villager";
    private static final String STOCK = "stock";
    private static final String QUOTA = "quota";
    private static final String TOTAL = "total";
    private static final String SOLD = "sold";
    /* 弹药每次交易的发数, 原价段与折扣段不同 */
    private static final int AMMO_UNIT_FULL = 8;
    private static final int AMMO_UNIT_HALF = 16;
    /* 枪械价格按分档, 折扣段取一半 */
    private static final int PRICE_LOW = 12;
    private static final int PRICE_MID = 20;
    private static final int PRICE_HIGH = 32;
    /* 击杀持械村民的额外经验 */
    public static final int VILLAGER_XP = 20;

    private ArmedVillager() {
    }

    /* 一类商品的交易计划 */
    public record Plan(String key, ItemStack result, int emeralds, int maxUses) {
    }

    public static boolean isArmed(Villager villager) {
        return villager.getPersistentData().contains(ROOT, Tag.TAG_COMPOUND);
    }

    @Nullable
    private static CompoundTag root(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        return data.contains(ROOT, Tag.TAG_COMPOUND) ? data.getCompound(ROOT) : null;
    }

    /* 生成时武装: 给一把枪与一份弹药, 主手装备一把可射击的枪, 并按持有量初始化额度 */
    public static void arm(Villager villager, RandomSource random) {
        /* 村民在区块加载过程中进入世界, 此时不能查群系, 只读世界难度 */
        Difficulty difficulty = villager.level().getDifficulty();
        GunnerArming.Arming arming = GunnerArming.roll(random, difficulty);
        if (arming == null) {
            return;
        }
        GunnerMarker.mark(villager, arming.profile(), arming.ammoPool(), true);
        GunnerArming.equip(villager, arming.profile().gunId(), arming.profile().fireMode(), arming.ammoPool(), random);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        equipLightArmor(villager, random);

        CompoundTag data = new CompoundTag();
        ListTag stock = new ListTag();
        ItemStack gun = TaczApi.createGun(arming.profile().gunId(), arming.profile().fireMode(), 0);
        if (!gun.isEmpty()) {
            stock.add(gun.copy().save(new CompoundTag()));
        }
        ItemStack ammo = TaczApi.createAmmo(arming.profile().ammoId(), Math.max(8, arming.ammoPool()));
        if (!ammo.isEmpty()) {
            stock.add(ammo.copy().save(new CompoundTag()));
        }
        data.put(STOCK, stock);

        CompoundTag quota = new CompoundTag();
        for (ItemStack stack : readStock(stock)) {
            String key = keyOf(stack);
            if (key == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(TOTAL, countOf(stack) * 2);
            entry.putInt(SOLD, 0);
            quota.put(key, entry);
        }
        data.put(QUOTA, quota);
        villager.getPersistentData().put(ROOT, data);
        refreshOffers(villager);
        TACZEnhancedPatch.LOGGER.debug("[villager] armed at {}", villager.blockPosition());
    }

    /* 交易完成后的账目更新, 返回是否已经退化 */
    public static boolean recordSale(Villager villager, String key, int units) {
        CompoundTag data = root(villager);
        if (data == null) {
            return false;
        }
        CompoundTag quota = data.getCompound(QUOTA);
        if (!quota.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag entry = quota.getCompound(key);
        int sold = entry.getInt(SOLD);
        entry.putInt(SOLD, sold + units);
        quota.put(key, entry);
        data.put(QUOTA, quota);

        /* 只有原价段才从库存扣除, 折扣段由系统兜底 */
        ListTag stock = data.getList(STOCK, Tag.TAG_COMPOUND);
        boolean fromStock = countOf(stock, key) > 0;
        if (fromStock) {
            consume(stock, key, units);
            data.put(STOCK, stock);
        }
        villager.getPersistentData().put(ROOT, data);
        if (countAll(stock) <= 0) {
            degrade(villager);
            return true;
        }
        refreshOffers(villager);
        return false;
    }

    /* 只穿轻甲: 四个部位各随机皮革或锁链, 不加额外护甲点 */
    private static void equipLightArmor(Villager villager, RandomSource random) {
        float dropChance = (float) ServerConfig.gunnerArmorDropChance();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            boolean leather = random.nextBoolean();
            villager.setItemSlot(slot, new ItemStack(lightArmor(slot, leather)));
            villager.setDropChance(slot, dropChance);
        }
    }

    private static Item lightArmor(EquipmentSlot slot, boolean leather) {
        if (leather) {
            return switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
        }
        return switch (slot) {
            case HEAD -> Items.CHAINMAIL_HELMET;
            case CHEST -> Items.CHAINMAIL_CHESTPLATE;
            case LEGS -> Items.CHAINMAIL_LEGGINGS;
            default -> Items.CHAINMAIL_BOOTS;
        };
    }

    /* 取出并清空库存, 用于感染转换时把物品交给新生物或掉在地上 */
    public static List<ItemStack> takeStock(Villager villager) {
        CompoundTag data = root(villager);
        List<ItemStack> stacks = new ArrayList<>();
        if (data == null) {
            return stacks;
        }
        stacks.addAll(readStock(data.getList(STOCK, Tag.TAG_COMPOUND)));
        villager.getPersistentData().remove(ROOT);
        GunnerMarker.clear(villager);
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return stacks;
    }

    /* 退化为原版村民 */
    public static void degrade(Villager villager) {
        CompoundTag persistent = villager.getPersistentData();
        persistent.remove(ROOT);
        GunnerMarker.clear(villager);
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        MerchantOffers offers = villager.getOffers();
        offers.removeIf(ArmedVillager::isOurs);
        TACZEnhancedPatch.LOGGER.debug("[villager] degraded at {}", villager.blockPosition());
    }

    /* 按当前账目重建自定义商品, 保留原版商品 */
    public static void refreshOffers(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        offers.removeIf(ArmedVillager::isOurs);
        if (!isArmed(villager)) {
            return;
        }
        for (Plan plan : plans(villager)) {
            ItemStack cost = new ItemStack(Items.EMERALD, Math.max(1, plan.emeralds()));
            offers.add(new MerchantOffer(cost, plan.result(), Math.max(1, plan.maxUses()), 1, 0.0F));
        }
    }

    /* 当前可交易的商品计划 */
    public static List<Plan> plans(Villager villager) {
        List<Plan> plans = new ArrayList<>();
        CompoundTag data = root(villager);
        if (data == null) {
            return plans;
        }
        CompoundTag quota = data.getCompound(QUOTA);
        ListTag stock = data.getList(STOCK, Tag.TAG_COMPOUND);
        /* 按 key 排序保证商品顺序稳定, 职业变更重建商品时不会错位 */
        for (String key : new java.util.TreeSet<>(quota.getAllKeys())) {
            CompoundTag entry = quota.getCompound(key);
            int remaining = entry.getInt(TOTAL) - entry.getInt(SOLD);
            if (remaining <= 0) {
                continue;
            }
            ItemStack sample = sampleOf(stock, key);
            if (sample.isEmpty()) {
                continue;
            }
            boolean discounted = countOf(stock, key) <= 0;
            if (key.startsWith("ammo:")) {
                int unit = discounted ? AMMO_UNIT_HALF : AMMO_UNIT_FULL;
                int rounds = Math.min(unit, remaining);
                ItemStack result = sample.copy();
                result.setCount(Math.max(1, rounds));
                plans.add(new Plan(key, result, 1, Math.max(1, (remaining + unit - 1) / unit)));
            } else {
                ItemStack result = sample.copy();
                result.setCount(1);
                int price = gunPrice(result, discounted);
                plans.add(new Plan(key, result, price, remaining));
            }
        }
        return plans;
    }

    private static int gunPrice(ItemStack gun, boolean discounted) {
        GunnerArsenal.GunProfile profile = GunnerArsenal.byGunId(TaczApi.getGunId(gun));
        int base = PRICE_MID;
        if (profile != null) {
            base = switch (profile.tier()) {
                case LOW -> PRICE_LOW;
                case HIGH -> PRICE_HIGH;
                default -> PRICE_MID;
            };
        }
        return discounted ? Math.max(1, base / 2) : base;
    }

    /* 商品是否由本模组注入 */
    public static boolean isOurs(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        return TaczApi.isGun(result) || isAmmo(result);
    }

    @Nullable
    public static String keyOf(ItemStack stack) {
        if (TaczApi.isGun(stack)) {
            ResourceLocation id = TaczApi.getGunId(stack);
            return id == null ? null : "gun:" + id;
        }
        if (isAmmo(stack)) {
            CompoundTag tag = stack.getTag();
            String id = tag == null ? "" : tag.getString("AmmoId");
            return id.isEmpty() ? "ammo:unknown" : "ammo:" + id;
        }
        return null;
    }

    private static boolean isAmmo(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("AmmoId");
    }

    private static int countOf(ItemStack stack) {
        return isAmmo(stack) ? stack.getCount() : 1;
    }

    private static List<ItemStack> readStock(ListTag stock) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) {
            ItemStack stack = ItemStack.of(stock.getCompound(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static int countOf(ListTag stock, String key) {
        int total = 0;
        for (ItemStack stack : readStock(stock)) {
            if (key.equals(keyOf(stack))) {
                total += countOf(stack);
            }
        }
        return total;
    }

    private static int countAll(ListTag stock) {
        int total = 0;
        for (ItemStack stack : readStock(stock)) {
            total += countOf(stack);
        }
        return total;
    }

    private static ItemStack sampleOf(ListTag stock, String key) {
        for (ItemStack stack : readStock(stock)) {
            if (key.equals(keyOf(stack))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /* 从库存里扣掉指定数量, 扣到零的条目直接移除 */
    private static void consume(ListTag stock, String key, int units) {
        int remaining = units;
        for (int i = 0; i < stock.size() && remaining > 0; i++) {
            ItemStack stack = ItemStack.of(stock.getCompound(i));
            if (!key.equals(keyOf(stack))) {
                continue;
            }
            int take = Math.min(remaining, countOf(stack));
            remaining -= take;
            if (isAmmo(stack)) {
                int left = stack.getCount() - take;
                if (left <= 0) {
                    stock.remove(i);
                    i--;
                } else {
                    stack.setCount(left);
                    stock.set(i, stack.save(new CompoundTag()));
                }
            } else {
                stock.remove(i);
                i--;
            }
        }
    }
}
