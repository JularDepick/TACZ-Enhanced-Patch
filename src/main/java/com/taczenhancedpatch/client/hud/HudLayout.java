package com.taczenhancedpatch.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Collection;

/*
 * 屏幕边缘的纵向排布。
 * 右上角一列有三个占用者: 原版药水效果, 遗迹兵力读数, 手雷面板。
 * 三者各自画各自的, 但纵坐标统一从这里算, 谁出现谁占位, 不再互相遮挡。
 */
public final class HudLayout {
    /* 距屏幕边缘的间距 */
    public static final int MARGIN = 8;
    /* 元素之间的间距 */
    public static final int GAP = 4;
    /* 原版药水效果每行占用的高度与最多占用的行数 */
    private static final int EFFECT_ROW = 26;
    private static final int MAX_EFFECT_ROWS = 6;

    private HudLayout() {
    }

    /* 药水效果区之下第一条可用的纵坐标; 没有效果时就是顶边 */
    public static int rightColumnTop(Minecraft minecraft) {
        return MARGIN + effectOffset(minecraft.player == null
                ? java.util.List.of()
                : minecraft.player.getActiveEffects());
    }

    private static int effectOffset(Collection<MobEffectInstance> effects) {
        if (effects.isEmpty()) {
            return 0;
        }
        return EFFECT_ROW * Math.min(effects.size(), MAX_EFFECT_ROWS);
    }
}
