package com.taczenhancedpatch.item;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 无限弹夹。
 * 属于扩容弹夹, 复用上游的通用扩容弹夹物品与配件 ID, 只在物品栈上写一个标记;
 * 带标记的弹夹在物品页里单独展示, 显示名与提示行改为无限弹夹与容量正无穷。
 * 装配后由换弹与开火逻辑维持满弹匣, 并把背包弹药消耗切换到虚拟备弹。
 */
public final class InfiniteMag {
    /* 物品栈上的标记, 也是判定是否无限弹夹的唯一依据 */
    public static final String TAG = "InfiniteMag";
    /* 复用的上游通用扩容弹夹配件 ID */
    public static final ResourceLocation BASE_ATTACHMENT = new ResourceLocation("tacz:extended_mag_1");
    public static final String NAME_KEY = "item.tacz_enhanced_patch.infinite_mag";
    public static final String CAPACITY_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.capacity";
    public static final String BEHAVIOR_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.behavior";

    private InfiniteMag() {
    }

    /* 生成一件带标记的无限弹夹 */
    public static ItemStack create() {
        ItemStack stack = TaczApi.createAttachment(BASE_ATTACHMENT);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack.getOrCreateTag().putBoolean(TAG, true);
        stack.setHoverName(Component.translatable(NAME_KEY));
        return stack;
    }

    public static boolean isInfinite(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        return stack.getTag().contains(TAG, Tag.TAG_BYTE) && stack.getTag().getBoolean(TAG);
    }

    public static Component displayName() {
        return Component.translatable(NAME_KEY);
    }
}
