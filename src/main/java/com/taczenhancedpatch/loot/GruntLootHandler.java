package com.taczenhancedpatch.loot;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.entity.GunnerGrunt;
import com.taczenhancedpatch.gunner.GunnerArsenal;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 持枪小兵战利品。
 * 击杀后按配置发放枪械, 弹药, 食物与杂物: 弹药数量与剩余备弹挂钩,
 * 食物每次固定抽若干堆, 每堆数量随机, 因此同一次击杀的食物种类与数量都不相同。
 * 枪械掉落时先清空备弹, 剩余弹药只以弹药物品形式出现。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GruntLootHandler {
    private static final int MAX_AMMO_PER_DROP = 64;
    private static final int LOOTING_AMMO_BONUS = 2;

    /* 食物池: 以熟食与常见作物为主, 保证每次击杀都有可用的补给 */
    private static final List<Item> FOODS = List.of(
            Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
            Items.BAKED_POTATO, Items.APPLE, Items.CARROT, Items.COOKIE, Items.SWEET_BERRIES);

    /* 杂物池: 与枪械维护和日常补给相关的材料 */
    private static final List<Item> RESOURCES = List.of(
            Items.GUNPOWDER, Items.IRON_INGOT, Items.COPPER_INGOT, Items.STRING,
            Items.LEATHER, Items.PAPER, Items.FLINT, Items.SUGAR);

    private GruntLootHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof GunnerGrunt grunt)) {
            return;
        }
        RandomSource random = grunt.getRandom();
        ItemStack held = grunt.getMainHandItem();
        if (TaczApi.isGun(held)) {
            dropGunLoot(grunt, held, random, event);
        }
        dropFood(grunt, random, event);
        if (random.nextDouble() < ServerConfig.gruntResourceChance()) {
            Item resource = RESOURCES.get(random.nextInt(RESOURCES.size()));
            addDrop(grunt, new ItemStack(resource, 1 + random.nextInt(3)), event);
        }
    }

    private static void dropGunLoot(GunnerGrunt grunt, ItemStack held, RandomSource random, LivingDropsEvent event) {
        GunnerArsenal.GunProfile profile = GunnerArsenal.byGunId(TaczApi.getGunId(held));
        int reserve = TaczApi.dummyAmmo(held);
        if (profile != null) {
            /* 弹药: 剩余备弹按倍率折算, 再叠加随机数量与抢夺加成 */
            int leftover = (int) Math.round(reserve * ServerConfig.gruntAmmoDropMultiplier());
            int randomPart = Mth.nextInt(random, profile.ammoDropMin(), profile.ammoDropMax())
                    + event.getLootingLevel() * LOOTING_AMMO_BONUS;
            dropAmmo(grunt, profile.ammoId(), Math.max(1, leftover + randomPart), event);
            if (random.nextDouble() < ServerConfig.gruntHighTierAmmoChance()) {
                ResourceLocation highTier = GunnerArsenal.pickHighTierAmmo(random);
                int count = Mth.nextInt(random, GunnerArsenal.HIGH_TIER_AMMO_MIN, GunnerArsenal.HIGH_TIER_AMMO_MAX);
                dropAmmo(grunt, highTier, Math.max(1, count), event);
            }
        }
        if (random.nextDouble() < ServerConfig.gruntGunDropChance()) {
            ItemStack gun = held.copy();
            TaczApi.clearDummyAmmo(gun);
            addDrop(grunt, gun, event);
        }
        if (random.nextDouble() < ServerConfig.gruntAttachmentDropChance()) {
            TaczApi.randomAttachment(random).ifPresent(stack -> addDrop(grunt, stack, event));
        }
    }

    /* 食物: 堆数与每堆数量都按配置区间随机, 逐个抽取种类 */
    private static void dropFood(GunnerGrunt grunt, RandomSource random, LivingDropsEvent event) {
        int[] stacks = ServerConfig.gruntFoodStacks();
        int[] counts = ServerConfig.gruntFoodCount();
        int stackCount = range(random, stacks);
        for (int i = 0; i < stackCount; i++) {
            Item food = FOODS.get(random.nextInt(FOODS.size()));
            addDrop(grunt, new ItemStack(food, range(random, counts)), event);
        }
    }

    private static void dropAmmo(LivingEntity dead, @Nullable ResourceLocation ammoId, int count, LivingDropsEvent event) {
        if (ammoId == null || count <= 0) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, MAX_AMMO_PER_DROP);
            addDrop(dead, TaczApi.createAmmo(ammoId, stackSize), event);
            remaining -= stackSize;
        }
    }

    private static int range(RandomSource random, int[] bounds) {
        int min = Math.max(0, bounds[0]);
        int max = Math.max(min, bounds[1]);
        return min >= max ? min : Mth.nextInt(random, min, max);
    }

    private static void addDrop(LivingEntity dead, ItemStack stack, LivingDropsEvent event) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5D, dead.getZ(), stack);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }
}
