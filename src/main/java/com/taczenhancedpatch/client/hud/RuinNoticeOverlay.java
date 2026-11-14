package com.taczenhancedpatch.client.hud;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 遗迹小提示。
 * 水平居中, 竖直靠上, 字号与原版一致但只有一行, 不遮挡视野;
 * 开启方向标尺时下移到标尺读数之下, 两者不重叠。
 * 由服务端下发的遗迹小提示包驱动, 到期自动消失。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class RuinNoticeOverlay {
    /* 有方向标尺时的纵坐标: 标尺本体 20 像素, 中心三角与读数共约 15 像素 */
    private static final int TOP_WITH_RULER = 40;
    private static final int TOP_WITHOUT_RULER = 8;
    /* 文字框的内边距与配色 */
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int BACKGROUND = 0x66000000;
    private static final int TEXT_COLOR = 0xFFFFFFE0;

    private static String key = "";
    private static int value;
    private static long expireAt;

    private RuinNoticeOverlay() {
    }

    /* 服务端下发时调用, 同一时刻只保留一条, 后来的覆盖先前的 */
    public static void accept(String noticeKey, int noticeValue, int durationMillis) {
        key = noticeKey == null ? "" : noticeKey;
        value = noticeValue;
        expireAt = System.currentTimeMillis() + Math.max(200, durationMillis);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (key.isEmpty() || System.currentTimeMillis() > expireAt) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        String text = UiText.of(key, value).getString();
        if (text.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = minecraft.font.width(text);
        int left = (screenWidth - width) / 2;
        int top = ClientConfig.headingRuler() ? TOP_WITH_RULER : TOP_WITHOUT_RULER;
        graphics.fill(left - PADDING_X, top - PADDING_Y, left + width + PADDING_X,
                top + minecraft.font.lineHeight + PADDING_Y, BACKGROUND);
        graphics.drawString(minecraft.font, text, left, top, TEXT_COLOR, false);
    }
}
