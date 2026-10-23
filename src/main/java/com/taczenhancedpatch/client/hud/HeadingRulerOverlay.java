package com.taczenhancedpatch.client.hud;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 方向标尺。
 * 屏幕正上方的横向航向带: 随镜头滚动, 标注基本方向与角度数字, 便于报点。
 * 只在客户端绘制, 采用原版界面绘制能力, 不依赖上游模组的渲染管线。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HeadingRulerOverlay {
    /* 标尺宽度与高度, 单位像素 */
    private static final int TAPE_WIDTH = 260;
    private static final int TAPE_HEIGHT = 20;
    /* 标尺距屏幕顶部的距离 */
    private static final int TAPE_TOP = 6;
    /* 每度对应的像素数, 决定可视角度范围 */
    private static final float PIXELS_PER_DEGREE = 2.0F;
    /* 刻度与数字标签的角度间隔 */
    private static final int TICK_STEP = 5;
    private static final int LABEL_STEP = 15;
    /* 刻度与文字配色 */
    private static final int BACKGROUND = 0x66000000;
    private static final int TICK_COLOR = 0xCCFFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int ACCENT_COLOR = 0xFFFFB300;
    /* 方位换算: 游戏朝向加半圈得到罗盘航向, 北为 0 度 */
    private static final float COMPASS_OFFSET = 180.0F;

    private HeadingRulerOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientConfig.headingRuler()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;
        int left = centerX - TAPE_WIDTH / 2;
        int right = left + TAPE_WIDTH;
        int top = TAPE_TOP;
        int bottom = top + TAPE_HEIGHT;
        float heading = Mth.positiveModulo(
                minecraft.gameRenderer.getMainCamera().getYRot() + COMPASS_OFFSET, 360.0F);

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(left, top, right, bottom, BACKGROUND);
        graphics.enableScissor(left, top, right, bottom);
        float visibleHalfAngle = TAPE_WIDTH / 2.0F / PIXELS_PER_DEGREE + TICK_STEP;
        for (int degree = 0; degree < 360; degree += TICK_STEP) {
            float offset = Mth.wrapDegrees(degree - heading);
            if (Math.abs(offset) > visibleHalfAngle) {
                continue;
            }
            int x = Math.round(centerX + offset * PIXELS_PER_DEGREE);
            boolean cardinal = degree % 90 == 0;
            boolean labelled = degree % LABEL_STEP == 0;
            int tickHeight = cardinal ? TAPE_HEIGHT - 4 : (labelled ? 7 : 4);
            graphics.fill(x, bottom - tickHeight, x + 1, bottom, TICK_COLOR);
            if (labelled) {
                String text = cardinal ? cardinalName(degree) : String.valueOf(degree);
                int textWidth = minecraft.font.width(text);
                graphics.drawString(minecraft.font, text, x - textWidth / 2, top + 3,
                        cardinal ? ACCENT_COLOR : TEXT_COLOR, false);
            }
        }
        graphics.disableScissor();
        /* 中心指示: 一条竖线加一个向下的三角, 表示当前正对方向 */
        graphics.fill(centerX, top, centerX + 1, bottom, ACCENT_COLOR);
        for (int row = 0; row < 4; row++) {
            int halfWidth = 3 - row;
            if (halfWidth <= 0) {
                continue;
            }
            graphics.fill(centerX - halfWidth, bottom + row, centerX + halfWidth + 1, bottom + row + 1, ACCENT_COLOR);
        }
        String readout = UiText.of("hud.heading.readout", String.valueOf(Math.round(heading) % 360)).getString();
        int readoutWidth = minecraft.font.width(readout);
        graphics.drawString(minecraft.font, readout, centerX - readoutWidth / 2, bottom + 6, ACCENT_COLOR, false);
    }

    /* 航向角与基本方向的对应关系, 北为 0 度, 顺时针递增 */
    private static String cardinalName(int degree) {
        return switch (Mth.positiveCeilDiv(degree, 90) % 4) {
            case 0 -> UiText.plain("hud.heading.north");
            case 1 -> UiText.plain("hud.heading.east");
            case 2 -> UiText.plain("hud.heading.south");
            default -> UiText.plain("hud.heading.west");
        };
    }
}
