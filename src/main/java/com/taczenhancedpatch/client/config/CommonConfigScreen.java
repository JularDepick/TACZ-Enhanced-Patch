package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.config.CommonConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * 通用配置界面。
 * 从模组列表的配置按钮进入, 只编辑标量配置项, 生物黑名单与维度黑名单仍需直接编辑配置文件。
 */
public final class CommonConfigScreen extends Screen {
    private static final int LIST_WIDTH = 340;
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 160;
    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM_MARGIN = 46;

    private final Screen parent;
    private final List<CommonConfig.Option> options;
    private final List<Object> originals = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private boolean saved;
    @Nullable
    private AbstractWidget dragging;

    public CommonConfigScreen(@Nullable Screen parent) {
        super(Component.literal("TACZ 增强补丁配置"));
        this.parent = parent;
        this.options = CommonConfig.options();
        for (CommonConfig.Option option : options) {
            originals.add(read(option));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        listTop = LIST_TOP;
        listBottom = height - LIST_BOTTOM_MARGIN;
        for (CommonConfig.Option option : options) {
            rows.add(new Row(option.label(), createWidget(option)));
        }
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), button -> finish(true))
                .bounds(width / 2 - 155, height - 34, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("取消"), button -> finish(false))
                .bounds(width / 2 + 5, height - 34, 150, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.fill(listLeft - 4, listTop - 4, listRight + 12, listBottom + 4, 0x66000000);
        scroll = Mth.clamp(scroll, 0.0D, maxScroll());
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + i * ROW_HEIGHT - (int) scroll;
            AbstractWidget widget = row.widget();
            widget.setX(listRight - WIDGET_WIDTH - 6);
            widget.setY(rowY + 2);
            widget.visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            if (!widget.visible) {
                continue;
            }
            graphics.drawString(font, row.label(), listLeft + 4, rowY + 7, 0xE0E0E0, false);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        double max = maxScroll();
        if (max <= 0.0D) {
            return;
        }
        int trackHeight = listBottom - listTop;
        int thumbHeight = Math.max(20, (int) (trackHeight * trackHeight / (trackHeight + max)));
        int thumbTop = listTop + (int) ((trackHeight - thumbHeight) * (scroll / max));
        int x = listRight + 4;
        graphics.fill(x, listTop, x + 4, listBottom, 0x44000000);
        graphics.fill(x, thumbTop, x + 4, thumbTop + thumbHeight, 0xFFB0B0B0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insideList(mouseX, mouseY)) {
            for (Row row : rows) {
                AbstractWidget widget = row.widget();
                if (widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
                    dragging = widget;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging.mouseReleased(mouseX, mouseY, button);
            dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (insideList(mouseX, mouseY) && maxScroll() > 0.0D) {
            scroll = Mth.clamp(scroll - delta * ROW_HEIGHT, 0.0D, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        finish(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void finish(boolean saveNow) {
        if (saveNow) {
            CommonConfig.save();
            saved = true;
        } else if (!saved) {
            restoreOriginals();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void restoreOriginals() {
        for (int i = 0; i < options.size(); i++) {
            write(options.get(i), originals.get(i));
        }
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
    }

    private double maxScroll() {
        int contentHeight = rows.size() * ROW_HEIGHT;
        int viewHeight = Math.max(1, listBottom - listTop);
        return Math.max(0.0D, contentHeight - viewHeight);
    }

    private AbstractWidget createWidget(CommonConfig.Option option) {
        if (option instanceof CommonConfig.ToggleOption toggle) {
            return Button.builder(toggleText(toggle), button -> {
                toggle.value().set(!Boolean.TRUE.equals(toggle.value().get()));
                button.setMessage(toggleText(toggle));
            }).bounds(0, 0, WIDGET_WIDTH, 20).build();
        }
        if (option instanceof CommonConfig.DoubleOption doubleOption) {
            double current = Mth.clamp(doubleOption.value().get(), doubleOption.min(), doubleOption.max());
            double fraction = (current - doubleOption.min()) / (doubleOption.max() - doubleOption.min());
            return new NumberSlider(doubleOption.label(), doubleOption.min(), doubleOption.max(),
                    doubleOption.step(), false, fraction,
                    value -> doubleOption.value().set(Mth.clamp(value, doubleOption.min(), doubleOption.max())));
        }
        if (option instanceof CommonConfig.IntOption intOption) {
            double current = Mth.clamp(intOption.value().get(), intOption.min(), intOption.max());
            double fraction = (current - intOption.min()) / (double) (intOption.max() - intOption.min());
            return new NumberSlider(intOption.label(), intOption.min(), intOption.max(),
                    1.0D, true, fraction,
                    value -> intOption.value().set((int) Math.round(Mth.clamp(value, intOption.min(), intOption.max()))));
        }
        throw new IllegalStateException("unsupported option " + option.label());
    }

    private static Component toggleText(CommonConfig.ToggleOption option) {
        return Component.literal(Boolean.TRUE.equals(option.value().get()) ? "开" : "关");
    }

    private static Object read(CommonConfig.Option option) {
        if (option instanceof CommonConfig.ToggleOption toggle) {
            return toggle.value().get();
        }
        if (option instanceof CommonConfig.DoubleOption doubleOption) {
            return doubleOption.value().get();
        }
        if (option instanceof CommonConfig.IntOption intOption) {
            return intOption.value().get();
        }
        return null;
    }

    private static void write(CommonConfig.Option option, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (option instanceof CommonConfig.ToggleOption toggle) {
            toggle.value().set(Boolean.TRUE.equals(value));
        } else if (option instanceof CommonConfig.DoubleOption doubleOption) {
            doubleOption.value().set(((Number) value).doubleValue());
        } else if (option instanceof CommonConfig.IntOption intOption) {
            intOption.value().set(((Number) value).intValue());
        }
    }

    private record Row(String label, AbstractWidget widget) {
    }

    private static final class NumberSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final boolean integer;
        private final DoubleConsumer setter;

        NumberSlider(String label, double min, double max, double step, boolean integer,
                     double initialFraction, DoubleConsumer setter) {
            super(0, 0, WIDGET_WIDTH, 20, Component.empty(), Mth.clamp(initialFraction, 0.0D, 1.0D));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.integer = integer;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + format(currentValue())));
        }

        @Override
        protected void applyValue() {
            setter.accept(currentValue());
        }

        private double currentValue() {
            double raw = min + (max - min) * value;
            double snapped = integer ? Math.round(raw) : Math.round(raw / step) * step;
            return Mth.clamp(snapped, min, max);
        }

        private String format(double current) {
            if (integer) {
                return String.valueOf((int) Math.round(current));
            }
            return String.format(Locale.ROOT, "%.2f", current);
        }
    }
}
