package com.taczenhancedpatch.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.client.ConfigAccess;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.config.ConfigOption;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * 单页配置列表。
 * 客户端页对所有玩家可见并可改; 服务端页只在存档创建者一侧打开, 普通客户端看不到入口。
 * 服务端页顶部额外提供打开本页的快捷键设置。
 */
public final class PatchConfigScreen extends Screen {
    private static final int LIST_WIDTH = 340;
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 160;
    private static final int LIST_TOP = 42;
    private static final int LIST_BOTTOM_MARGIN = 46;

    private final Screen parent;
    private final boolean serverSection;
    private final List<ConfigOption> options;
    private final Map<ConfigOption, Object> originals = new IdentityHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private boolean saved;
    private boolean capturingKey;
    @Nullable
    private Button shortcutButton;
    @Nullable
    private AbstractWidget dragging;

    public PatchConfigScreen(@Nullable Screen parent, boolean serverSection) {
        super(Component.literal(serverSection && ConfigAccess.isWorldOwner() ? "服务端配置" : "客户端配置"));
        this.parent = parent;
        /* 服务端页只在创建者一侧生效, 越权构造时退化为客户端页 */
        this.serverSection = serverSection && ConfigAccess.isWorldOwner();
        this.options = this.serverSection ? ServerConfig.options() : ClientConfig.options();
        for (ConfigOption option : options) {
            originals.put(option, read(option));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        listTop = LIST_TOP;
        listBottom = height - LIST_BOTTOM_MARGIN;
        if (serverSection) {
            rows.add(new Row("服务端设置会同步给所有客户端", null, true));
            shortcutButton = Button.builder(shortcutText(), button -> startCapturingKey())
                    .bounds(0, 0, WIDGET_WIDTH, 20)
                    .build();
            rows.add(new Row("打开本页快捷键", shortcutButton, false));
        } else {
            rows.add(new Row("客户端设置只影响本机", null, true));
        }
        for (ConfigOption option : options) {
            rows.add(new Row(option.label(), createWidget(option), false));
        }
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), button -> finish(true))
                .bounds(width / 2 - 155, height - 34, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("取消"), button -> finish(false))
                .bounds(width / 2 + 5, height - 34, 150, 20)
                .build());
    }

    private void startCapturingKey() {
        capturingKey = true;
        if (shortcutButton != null) {
            shortcutButton.setMessage(Component.literal("按下新的按键"));
        }
    }

    private static Component shortcutText() {
        InputConstants.Key key = InputConstants.getKey(ClientConfig.serverConfigKey(), 0);
        return Component.literal("Shift + " + key.getDisplayName().getString());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturingKey) {
            capturingKey = false;
            /* 只接受主键, 修饰键本身与退出键不作为绑定 */
            if (keyCode != GLFW.GLFW_KEY_ESCAPE
                    && keyCode != GLFW.GLFW_KEY_LEFT_SHIFT
                    && keyCode != GLFW.GLFW_KEY_RIGHT_SHIFT) {
                ClientConfig.setServerConfigKey(keyCode);
            }
            if (shortcutButton != null) {
                shortcutButton.setMessage(shortcutText());
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            AbstractWidget widget = row.widget();
            if (widget != null) {
                widget.visible = visible;
                if (visible) {
                    widget.setX(listRight - WIDGET_WIDTH - 6);
                    widget.setY(rowY + 2);
                }
            }
            if (!visible) {
                continue;
            }
            if (row.header()) {
                graphics.drawString(font, row.label(), listLeft + 6, rowY + 7, 0xFFD080, false);
                continue;
            }
            if (widget == null) {
                continue;
            }
            graphics.drawString(font, row.label(), listLeft + 14, rowY + 7, 0xE0E0E0, false);
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
                if (widget != null && widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
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
            ClientConfig.save();
            if (serverSection) {
                ServerConfig.save();
            }
            saved = true;
        } else if (!saved) {
            restoreOriginals();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void restoreOriginals() {
        for (Map.Entry<ConfigOption, Object> entry : originals.entrySet()) {
            write(entry.getKey(), entry.getValue());
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

    private AbstractWidget createWidget(ConfigOption option) {
        if (option instanceof ConfigOption.ToggleOption toggle) {
            return Button.builder(toggleText(toggle), button -> {
                toggle.value().set(!Boolean.TRUE.equals(toggle.value().get()));
                button.setMessage(toggleText(toggle));
            }).bounds(0, 0, WIDGET_WIDTH, 20).build();
        }
        if (option instanceof ConfigOption.DoubleOption doubleOption) {
            double current = Mth.clamp(doubleOption.value().get(), doubleOption.min(), doubleOption.max());
            double fraction = (current - doubleOption.min()) / (doubleOption.max() - doubleOption.min());
            return new NumberSlider(doubleOption.label(), doubleOption.min(), doubleOption.max(),
                    doubleOption.step(), false, fraction,
                    value -> doubleOption.value().set(Mth.clamp(value, doubleOption.min(), doubleOption.max())));
        }
        if (option instanceof ConfigOption.IntOption intOption) {
            double current = Mth.clamp(intOption.value().get(), intOption.min(), intOption.max());
            double fraction = (current - intOption.min()) / (double) (intOption.max() - intOption.min());
            return new NumberSlider(intOption.label(), intOption.min(), intOption.max(),
                    1.0D, true, fraction,
                    value -> intOption.value().set((int) Math.round(Mth.clamp(value, intOption.min(), intOption.max()))));
        }
        throw new IllegalStateException("unsupported option " + option.label());
    }

    private static Component toggleText(ConfigOption.ToggleOption option) {
        return Component.literal(Boolean.TRUE.equals(option.value().get()) ? "开" : "关");
    }

    private static Object read(ConfigOption option) {
        if (option instanceof ConfigOption.ToggleOption toggle) {
            return toggle.value().get();
        }
        if (option instanceof ConfigOption.DoubleOption doubleOption) {
            return doubleOption.value().get();
        }
        if (option instanceof ConfigOption.IntOption intOption) {
            return intOption.value().get();
        }
        return null;
    }

    private static void write(ConfigOption option, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (option instanceof ConfigOption.ToggleOption toggle) {
            toggle.value().set(Boolean.TRUE.equals(value));
        } else if (option instanceof ConfigOption.DoubleOption doubleOption) {
            doubleOption.value().set(((Number) value).doubleValue());
        } else if (option instanceof ConfigOption.IntOption intOption) {
            intOption.value().set(((Number) value).intValue());
        }
    }

    private record Row(String label, @Nullable AbstractWidget widget, boolean header) {
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
