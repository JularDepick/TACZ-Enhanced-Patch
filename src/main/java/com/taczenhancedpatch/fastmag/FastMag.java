package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 快速扩容弹夹。
 * 复用上游扩容弹夹的物品对象与配件 ID, 只在物品栈上加一个等级标记, 不新建任何物品。
 * 带标记的物品栈会写入自定义名称, 因此显示名改为快速扩容弹夹加上等级;
 * 换弹时长按等级缩短为原来的 30%, 40% 或 50%。
 */
public final class FastMag {
    /* 物品栈上的等级标记, 同时也是"这是一个快速扩容弹夹"的判定依据 */
    public static final String TAG_LEVEL = "FastExtendedMag";
    /* 自定义名称与提示文本的翻译键 */
    public static final String NAME_KEY = "item.tacz_enhanced_patch.fast_extended_mag";
    public static final String TOOLTIP_KEY = "tooltip.tacz_enhanced_patch.fast_extended_mag";

    private static final String[] ROMAN = {"I", "II", "III"};
    /* 按等级排列的换弹时长比例, 等级 1 到 3 分别对应 30%, 40% 与 50% */
    private static final double[] RELOAD_RATIO = {0.30D, 0.40D, 0.50D};

    private FastMag() {
    }

    /* 按上游条目生成一件带标记的快速扩容弹夹 */
    public static ItemStack create(TaczApi.ExtendedMagEntry entry) {
        ItemStack stack = TaczApi.createAttachment(entry.id());
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int level = clampLevel(entry.level());
        stack.getOrCreateTag().putInt(TAG_LEVEL, level);
        stack.setHoverName(displayName(level));
        return stack;
    }

    /* 物品栈上的等级标记, 没有标记时返回 0 */
    public static int level(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        if (!stack.getTag().contains(TAG_LEVEL, net.minecraft.nbt.Tag.TAG_INT)) {
            return 0;
        }
        return clampLevel(stack.getTag().getInt(TAG_LEVEL));
    }

    public static boolean isFastMag(ItemStack stack) {
        return level(stack) > 0;
    }

    public static double reloadRatio(int level) {
        return RELOAD_RATIO[clampLevel(level) - 1];
    }

    /* 提示文本里的整数百分比, 例如 30 */
    public static int reloadPercent(int level) {
        return (int) Math.round(reloadRatio(level) * 100.0D);
    }

    public static Component displayName(int level) {
        return Component.translatable(NAME_KEY, ROMAN[clampLevel(level) - 1]);
    }

    public static Component tooltip(int level) {
        return Component.translatable(TOOLTIP_KEY, reloadPercent(level));
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(3, level));
    }
}
