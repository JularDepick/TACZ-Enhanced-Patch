package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.client.ConfigAccess;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.config.ConfigOption;
import com.taczenhancedpatch.config.ConfigOptionGroup;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * 模组配置页。
 * 存档创建者打开时顶部出现客户端与服务端两个页签, 一步就能切到目标参数;
 * 其余玩家只看到客户端页, 并在标题下方提示服务端设置由创建者维护。
 * 服务端页把纯按钮与调试类条目排在参数组之前, 生物持械配置的入口固定在第一行。
 */
public final class PatchConfigScreen extends Screen {
    private static final int LIST_WIDTH = 380;
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 190;
    private static final int LIST_BOTTOM_MARGIN = 46;
    private static final int TAB_WIDTH = 96;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 6;

    private final Screen parent;
    private final boolean owner;
    private final List<ConfigOptionGroup> clientGroups;
    private final List<ConfigOptionGroup> serverGroups;
    private final Map<ConfigOption, Object> originals = new IdentityHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<EditBox> textFields = new ArrayList<>();
    private final ScrollBar scrollbar = new ScrollBar(value -> scroll = value);
    private boolean serverSection;
    private double scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private boolean saved;
    @Nullable
    private AbstractWidget dragging;
    @Nullable
    private Button clientTab;
    @Nullable
    private Button serverTab;

    public PatchConfigScreen(@Nullable Screen parent, boolean serverSection) {
        super(UiText.of("screen.config.title"));
        this.parent = parent;
        /* 只有存档创建者可以编辑服务端参数, 其余玩家一律落在客户端页 */
        this.owner = ConfigAccess.isWorldOwner();
        this.serverSection = serverSection && this.owner;
        this.clientGroups = ClientConfig.groups();
        this.serverGroups = owner ? ServerConfig.groups() : List.of();
        for (ConfigOptionGroup group : allGroups()) {
            for (ConfigOption option : group.options()) {
                originals.put(option, read(option));
            }
        }
    }

    private List<ConfigOptionGroup> allGroups() {
        if (!owner) {
            return clientGroups;
        }
        List<ConfigOptionGroup> all = new ArrayList<>(clientGroups);
        all.addAll(serverGroups);
        return all;
    }

    private List<ConfigOptionGroup> currentGroups() {
        return serverSection ? serverGroups : clientGroups;
    }

    @Override
    protected void init() {
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        listTop = owner ? 48 : 42;
        listBottom = height - LIST_BOTTOM_MARGIN;
        if (owner) {
            int left = width / 2 - TAB_WIDTH - TAB_GAP / 2;
            clientTab = addRenderableWidget(Button.builder(UiText.of("screen.tab.client"), b -> switchSection(false))
                    .bounds(left, 20, TAB_WIDTH, TAB_HEIGHT)
                    .build());
            serverTab = addRenderableWidget(Button.builder(UiText.of("screen.tab.server"), b -> switchSection(true))
                    .bounds(left + TAB_WIDTH + TAB_GAP, 20, TAB_WIDTH, TAB_HEIGHT)
                    .build());
            updateTabs();
        }
        addRenderableWidget(Button.builder(UiText.of("screen.save"), button -> finish(true))
                .bounds(width / 2 - 155, height - 34, 150, 20)
                .build());
        addRenderableWidget(Button.builder(UiText.of("screen.cancel"), button -> finish(false))
                .bounds(width / 2 + 5, height - 34, 150, 20)
                .build());
        rebuildRows();
    }

    /* 切换页签时重建条目, 已改动但未保存的数值留在配置对象里, 取消时统一回滚 */
    private void switchSection(boolean server) {
        if (server == serverSection) {
            return;
        }
        serverSection = server;
        scroll = 0.0D;
        rebuildRows();
        updateTabs();
    }

    /* 当前页签置灰, 一眼看出停在哪一页 */
    private void updateTabs() {
        if (clientTab != null) {
            clientTab.active = serverSection;
        }
        if (serverTab != null) {
            serverTab.active = !serverSection;
        }
    }

    private void rebuildRows() {
        rows.clear();
        textFields.clear();
        if (serverSection) {
            /* 纯按钮入口排在最前, 不必滚到界面与调试组 */
            Button mobListButton = Button.builder(UiText.of("screen.open"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new MobListScreen(this));
                }
            }).bounds(0, 0, WIDGET_WIDTH, 20).build();
            rows.add(new Row("screen.mob_list", mobListButton, false));
        }
        for (ConfigOptionGroup group : currentGroups()) {
            rows.add(new Row(group.label(), null, true));
            /* 组内先排开关项, 再排数值与文本项, 开关最显眼 */
            for (ConfigOption option : group.options()) {
                if (option instanceof ConfigOption.ToggleOption) {
                    rows.add(new Row(option.label(), createWidget(option), false));
                }
            }
            for (ConfigOption option : group.options()) {
                if (!(option instanceof ConfigOption.ToggleOption)) {
                    rows.add(new Row(option.label(), createWidget(option), false));
                }
            }
        }
        setFocused(null);
    }

    @Override
    public void tick() {
        for (EditBox box : textFields) {
            box.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (owner) {
            graphics.drawCenteredString(font, UiText.of(serverSection ? "screen.server.title" : "screen.client.title"),
                    width / 2, 6, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
            graphics.drawCenteredString(font, UiText.of("screen.hint.client_only"), width / 2, 28, 0x9E9E9E);
        }
        graphics.fill(listLeft - 4, listTop - 4, listRight + 20, listBottom + 4, 0x66000000);
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
                graphics.drawString(font, UiText.plain(row.label()), listLeft + 6, rowY + 7, 0xFFD080, false);
                continue;
            }
            if (widget == null) {
                continue;
            }
            graphics.drawString(font, UiText.plain(row.label()), listLeft + 14, rowY + 7, 0xE0E0E0, false);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        syncScrollbar();
        scrollbar.render(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /* 让滚动条的轨道位置与当前滚动量保持同步, 点击判定前也要刷新一次 */
    private void syncScrollbar() {
        scrollbar.update(listRight + 8, listTop, listBottom, scroll, maxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncScrollbar();
        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (insideList(mouseX, mouseY)) {
            for (Row row : rows) {
                AbstractWidget widget = row.widget();
                if (widget != null && widget.visible && widget.mouseClicked(mouseX, mouseY, button)) {
                    dragging = widget;
                    /* 文本框需要由本页转交键盘事件 */
                    setFocused(widget instanceof EditBox ? widget : null);
                    return true;
                }
            }
        }
        setFocused(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbar.mouseReleased();
        if (dragging != null) {
            dragging.mouseReleased(mouseX, mouseY, button);
            dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        if (getFocused() instanceof EditBox previous && previous != listener) {
            previous.setFocused(false);
        }
        if (listener instanceof AbstractWidget widget) {
            widget.setFocused(true);
        }
        super.setFocused(listener);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbar.isDragging()) {
            scrollbar.mouseDragged(mouseY);
            return true;
        }
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
            if (owner) {
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
        if (option instanceof ConfigOption.TextOption textOption) {
            EditBox box = new EditBox(font, 0, 0, WIDGET_WIDTH, 20, UiText.of(textOption.label()));
            box.setMaxLength(16);
            box.setValue(readText(textOption));
            box.setResponder(value -> {
                try {
                    textOption.value().set(value);
                } catch (IllegalStateException ignored) {
                }
            });
            textFields.add(box);
            return box;
        }
        throw new IllegalStateException("unsupported option " + option.label());
    }

    private static String readText(ConfigOption.TextOption option) {
        try {
            return option.value().get();
        } catch (IllegalStateException ignored) {
            return "";
        }
    }

    private static Component toggleText(ConfigOption.ToggleOption option) {
        return UiText.of(Boolean.TRUE.equals(option.value().get()) ? "screen.on" : "screen.off");
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
        if (option instanceof ConfigOption.TextOption textOption) {
            return readText(textOption);
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
        } else if (option instanceof ConfigOption.TextOption textOption) {
            textOption.value().set(String.valueOf(value));
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
            setMessage(Component.literal(UiText.plain(label) + ": " + format(currentValue())));
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
