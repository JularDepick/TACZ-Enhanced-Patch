package com.taczenhancedpatch.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;

/**
 * 可拖动滚动条。
 * 页面每帧调用 update 同步轨道位置与滚动量, 点击轨道会跳到对应位置, 按住滑块可以连续拖动。
 */
public final class ScrollBar {
    private static final int WIDTH = 6;
    /* 滑块最小高度, 内容很多时仍然按得到 */
    private static final int MIN_THUMB_HEIGHT = 20;
    /* 点击判定向外放宽的像素, 便于点中细轨道 */
    private static final int HIT_PADDING = 2;
    private static final int TRACK_COLOR = 0x55000000;
    private static final int THUMB_COLOR = 0xFFB0B0B0;

    private final DoubleConsumer setter;
    private int x;
    private int top;
    private int bottom;
    private double scroll;
    private double maxScroll;
    private boolean dragging;
    private double grabOffset;

    public ScrollBar(DoubleConsumer setter) {
        this.setter = setter;
    }

    public void update(int x, int top, int bottom, double scroll, double maxScroll) {
        this.x = x;
        this.top = top;
        this.bottom = bottom;
        this.scroll = Mth.clamp(scroll, 0.0D, Math.max(0.0D, maxScroll));
        this.maxScroll = Math.max(0.0D, maxScroll);
    }

    public boolean isVisible() {
        return maxScroll > 0.0D;
    }

    public boolean isDragging() {
        return dragging;
    }

    private int trackHeight() {
        return Math.max(1, bottom - top);
    }

    private int thumbHeight() {
        int track = trackHeight();
        int height = (int) (track * track / (track + maxScroll));
        return Mth.clamp(height, MIN_THUMB_HEIGHT, track);
    }

    private int thumbTop() {
        int travel = trackHeight() - thumbHeight();
        if (travel <= 0 || maxScroll <= 0.0D) {
            return top;
        }
        return top + (int) (travel * (scroll / maxScroll));
    }

    public void render(GuiGraphics graphics) {
        if (!isVisible()) {
            return;
        }
        graphics.fill(x, top, x + WIDTH, bottom, TRACK_COLOR);
        graphics.fill(x, thumbTop(), x + WIDTH, thumbTop() + thumbHeight(), THUMB_COLOR);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isVisible()) {
            return false;
        }
        if (mouseX < x - HIT_PADDING || mouseX > x + WIDTH + HIT_PADDING || mouseY < top || mouseY > bottom) {
            return false;
        }
        int thumbTop = thumbTop();
        int thumbHeight = thumbHeight();
        if (mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight) {
            grabOffset = mouseY - thumbTop;
        } else {
            /* 点到轨道空白处: 滑块中心跳到点击位置, 随后可以接着拖动 */
            grabOffset = thumbHeight / 2.0D;
            applyThumbTop(mouseY - grabOffset);
        }
        dragging = true;
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!dragging) {
            return false;
        }
        applyThumbTop(mouseY - grabOffset);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void applyThumbTop(double thumbTop) {
        int travel = trackHeight() - thumbHeight();
        if (travel <= 0 || maxScroll <= 0.0D) {
            return;
        }
        double ratio = Mth.clamp((thumbTop - top) / (double) travel, 0.0D, 1.0D);
        setter.accept(ratio * maxScroll);
    }
}
