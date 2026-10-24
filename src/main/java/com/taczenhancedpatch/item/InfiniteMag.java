package com.taczenhancedpatch.item;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 无限弹匣。
 * 属于扩容弹匣, 按上游四个弹匣族各提供一件带标记的基底: 常规, 轻量, 霰弹与狙击,
 * 因此任意接受扩容弹匣的枪械都能找到对应的一件装配; 标记存在物品栈上, 装配后随配件保留。
 * 标记物品的图标与容量属性都继承对应族的 III 级弹匣。
 */
public final class InfiniteMag {
    /* 物品栈上的标记, 也是判定是否无限弹匣的唯一依据 */
    public static final String TAG = "InfiniteMag";
    public static final String NAME_KEY = "item.tacz_enhanced_patch.infinite_mag";
    public static final String CAPACITY_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.capacity";
    public static final String BEHAVIOR_KEY = "tooltip.tacz_enhanced_patch.infinite_mag.behavior";

    /* 上游四种扩容弹匣族的 III 级基底, 与各自的显示名键 */
    public static final List<Family> FAMILIES = List.of(
            new Family(new ResourceLocation("tacz:extended_mag_3"), "item.tacz_enhanced_patch.infinite_mag"),
            new Family(new ResourceLocation("tacz:light_extended_mag_3"), "item.tacz_enhanced_patch.infinite_mag.light"),
            new Family(new ResourceLocation("tacz:shotgun_extended_mag_3"), "item.tacz_enhanced_patch.infinite_mag.shotgun"),
            new Family(new ResourceLocation("tacz:sniper_extended_mag_3"), "item.tacz_enhanced_patch.infinite_mag.sniper"));

    public record Family(ResourceLocation attachmentId, String nameKey) {
    }

    private InfiniteMag() {
    }

    /* 生成默认族(常规)的无限弹匣 */
    public static ItemStack create() {
        return create(FAMILIES.get(0));
    }

    /* 按族生成带标记的无限弹匣 */
    public static ItemStack create(Family family) {
        ItemStack stack = TaczApi.createAttachment(family.attachmentId());
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        stack.getOrCreateTag().putBoolean(TAG, true);
        stack.setHoverName(Component.translatable(family.nameKey()));
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
