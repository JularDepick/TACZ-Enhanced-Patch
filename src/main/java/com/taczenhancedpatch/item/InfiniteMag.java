package com.taczenhancedpatch.item;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 无限弹匣。
 * 属于扩容弹匣, 直接复用上游 III 级扩容弹匣的配件, 因此图标与容量属性都继承 III 级弹匣,
 * 再在物品栈上写一个标记并改名为无限弹匣。装配后由换弹与开火逻辑维持满弹匣,
 * 并把背包弹药消耗切换到虚拟备弹。
 */
public final class InfiniteMag {
    /* 物品栈上的标记, 也是判定是否无限弹匣的唯一依据 */
    public static final String TAG = "InfiniteMag";
    /* 复用的上游扩容弹匣配件 ID: III 级, 图标与属性随之继承 */
    public static final ResourceLocation BASE_ATTACHMENT = new ResourceLocation("tacz:extended_mag_3");
    public static final String NAME_KEY = "item.tacz_enhanced_patch.infinite_mag";
    public static final String CAPACITY_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.capacity";
    public static final String BEHAVIOR_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.behavior";

    private InfiniteMag() {
    }

    /* 生成一件带标记的无限弹匣 */
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
