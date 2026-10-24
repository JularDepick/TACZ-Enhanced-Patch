package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * 快速扩容弹匣。
 * 复用上游扩容弹匣的物品对象与配件 ID, 只在物品栈上加一个等级标记, 不新建任何物品。
 * 显示名改为上游原名称加后缀, 保留原弹匣的档位名称;
 * 换弹时长 = 原换弹时长 × 缺失弹药占弹匣容量的比例 × 容量档位比例。
 */
public final class FastMag {
    /* 物品栈上的等级标记, 同时也是"这是一个快速扩容弹匣"的判定依据 */
    public static final String TAG_LEVEL = "FastExtendedMag";
    /* 追加在原名称后面的后缀翻译键 */
    public static final String SUFFIX_KEY = "item.tacz_enhanced_patch.fast_mag_suffix";
    public static final String TOOLTIP_KEY = "tooltip.tacz_enhanced_patch.fast_extended_mag";

    /* 按等级排列的容量档位比例, 等级 1 到 3 分别对应 30%, 40% 与 50% */
    private static final double[] CAPACITY_RATIO = {0.30D, 0.40D, 0.50D};
    /* 缩放下限, 避免弹匣几乎满时把换弹压缩到不可见的长度 */
    private static final double MIN_SCALE = 0.05D;

    private FastMag() {
    }

    /* 按上游条目生成一件带标记的快速扩容弹匣, 显示名为原名称加后缀 */
    public static ItemStack create(TaczApi.ExtendedMagEntry entry) {
        ItemStack stack = TaczApi.createAttachment(entry.id());
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int level = clampLevel(entry.level());
        stack.getOrCreateTag().putInt(TAG_LEVEL, level);
        stack.setHoverName(Component.empty()
                .append(stack.getHoverName())
                .append(Component.translatable(SUFFIX_KEY)));
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

    /* 容量档位带来的固定比例, 等级越高换弹越慢一点 */
    public static double capacityRatio(int level) {
        return CAPACITY_RATIO[clampLevel(level) - 1];
    }

    /*
     * 当前这把枪的换弹时长比例: 容量档位比例乘缺失弹药占比。
     * 弹匣越空, 需要装填的子弹越多, 比例越大; 弹匣接近满时换弹明显更快。
     */
    public static double reloadScale(ItemStack gunStack, int level) {
        int capacity = TaczApi.maxMagazineAmmo(gunStack);
        double missing = 1.0D;
        if (capacity > 0) {
            int ammo = Mth.clamp(TaczApi.magazineAmmo(gunStack), 0, capacity);
            missing = (capacity - ammo) / (double) capacity;
        }
        return Mth.clamp(capacityRatio(level) * missing, MIN_SCALE, 1.0D);
    }

    /* 提示文本里的整数百分比, 例如 30 */
    public static int reloadPercent(int level) {
        return (int) Math.round(capacityRatio(level) * 100.0D);
    }

    public static Component tooltip(int level) {
        return Component.translatable(TOOLTIP_KEY, reloadPercent(level));
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(3, level));
    }
}
