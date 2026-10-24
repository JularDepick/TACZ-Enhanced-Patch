package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.client.ConfigAccess;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.config.ConfigOption;
import com.taczenhancedpatch.config.ConfigOptionGroup;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * 模组配置页。
 * 顶部是标题与客户端/服务端页签, 只有存档创建者能在两页之间切换;
 * 列表按分组渲染开关, 数值与颜色项, 分组条带左侧强调线, 行底色交替并在悬停时高亮;
 * 服务端页第一行固定是生物持械配置入口, 底部是保存与取消。
 */
public final class PatchConfigScreen extends Screen {
    private static final int LIST_WIDTH = 400;
    private static final int ROW_HEIGHT = 26;
    private static final int WIDGET_WIDTH = 190;
    private static final int BUTTON_ROW_WIDTH = 96;
    private static final int LIST_BOTTOM_MARGIN = 54;
    private static final int TAB_WIDTH = 100;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 4;
    private static final int HEADER_HEIGHT = 46;
    private static final int ACCENT_COLOR = 0xFFFFB300;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xE6E6E6;
    private static final int MUTED_COLOR = 0x9AA0A6;
    private static final int GROUP_COLOR = 0xFFD080;
    private static final int ROW_DARK = 0x26000000;
    private static final int ROW_LIGHT = 0x12000000;
    private static final int HOVER_COLOR = 0x24FFFFFF;
    private static final int BORDER_COLOR = 0xFF5A5F66;
    private static final int ERROR_COLOR = 0xFFFF5555;

    private final Screen parent;
    private final boolean owner;
    private final List<ConfigOptionGroup> clientGroups;
    private final List<ConfigOptionGroup> serverGroups;
    private final Map<ConfigOption, Object> originals = new IdentityHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<ColorField> colorFields = new ArrayList<>();
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
        listTop = HEADER_HEIGHT + 6;
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

    private void switchSection(boolean server) {
        if (server == serverSection) {
            return;
        }
        serverSection = server;
        scroll = 0.0D;
        rebuildRows();
        updateTabs();
    }

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
        colorFields.clear();
        textFields.clear();
        if (serverSection) {
            Button mobListButton = Button.builder(UiText.of("screen.open"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new MobListScreen(this));
                }
            }).bounds(0, 0, BUTTON_ROW_WIDTH, 20).build();
            rows.add(new Row("screen.mob_list", mobListButton, RowKind.BUTTON));
        }
        for (ConfigOptionGroup group : currentGroups()) {
            rows.add(new Row(group.label(), null, RowKind.GROUP));
            for (ConfigOption option : group.options()) {
                if (option instanceof ConfigOption.ToggleOption) {
                    rows.add(new Row(option.label(), createWidget(option), RowKind.OPTION));
                }
            }
            for (ConfigOption option : group.options()) {
                if (!(option instanceof ConfigOption.ToggleOption)) {
                    rows.add(new Row(option.label(), createWidget(option), RowKind.OPTION));
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
        drawHeader(graphics);
        graphics.fill(listLeft - 6, listTop - 6, listRight + 22, listBottom + 6, 0x99000000);
        scroll = Mth.clamp(scroll, 0.0D, maxScroll());
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        int index = 0;
        for (Row row : rows) {
            int rowY = listTop + index * ROW_HEIGHT - (int) scroll;
            index++;
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            AbstractWidget widget = row.widget();
            if (widget != null) {
                widget.visible = visible;
                if (visible) {
                    int widgetWidth = row.kind() == RowKind.BUTTON ? BUTTON_ROW_WIDTH : WIDGET_WIDTH;
                    widget.setX(listRight - widgetWidth - 8);
                    widget.setY(rowY + 3);
                }
            }
            if (!visible) {
                continue;
            }
            boolean hovered = mouseX >= listLeft && mouseX <= listRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (row.kind() == RowKind.GROUP) {
                graphics.fill(listLeft, rowY + 2, listRight, rowY + ROW_HEIGHT - 2, 0x33000000);
                graphics.fill(listLeft, rowY + 2, listLeft + 3, rowY + ROW_HEIGHT - 2, ACCENT_COLOR);
                graphics.drawString(font, UiText.plain(row.label()), listLeft + 10, rowY + 9, GROUP_COLOR, false);
                continue;
            }
            graphics.fill(listLeft, rowY + 1, listRight, rowY + ROW_HEIGHT - 1,
                    index % 2 == 0 ? ROW_DARK : ROW_LIGHT);
            if (hovered) {
                graphics.fill(listLeft, rowY + 1, listRight, rowY + ROW_HEIGHT - 1, HOVER_COLOR);
            }
            graphics.drawString(font, UiText.plain(row.label()), listLeft + 10, rowY + 9, LABEL_COLOR, false);
            if (widget != null) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();
        if (hasInvalidColor()) {
            graphics.drawString(font, UiText.plain("screen.color_hint"), listLeft, listBottom + 4, ERROR_COLOR, false);
        }
        syncScrollbar();
        scrollbar.render(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xE6000000);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0x40FFFFFF);
        if (owner) {
            graphics.drawCenteredString(font, UiText.of(serverSection ? "screen.server.title" : "screen.client.title"),
                    width / 2, 6, TITLE_COLOR);
        } else {
            graphics.drawCenteredString(font, title, width / 2, 8, TITLE_COLOR);
            graphics.drawCenteredString(font, UiText.of("screen.hint.client_only"), width / 2, 22, MUTED_COLOR);
        }
    }

    private boolean hasInvalidColor() {
        for (ColorField field : colorFields) {
            if (!field.isValid()) {
                return true;
            }
        }
        return false;
    }

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
            for (ColorField field : colorFields) {
                field.commitValidValue();
            }
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
        if (option instanceof ConfigOption.ColorOption colorOption) {
            ColorField field = new ColorField(font, colorOption.value(), readText(colorOption.value()));
            colorFields.add(field);
            textFields.add(field);
            return field;
        }
        if (option instanceof ConfigOption.TextOption textOption) {
            EditBox box = new EditBox(font, 0, 0, WIDGET_WIDTH, 20, UiText.of(textOption.label()));
            box.setMaxLength(32);
            box.setValue(readText(textOption.value()));
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

    private static String readText(ForgeConfigSpec.ConfigValue<String> value) {
        try {
            return value.get();
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
            return readText(textOption.value());
        }
        if (option instanceof ConfigOption.ColorOption colorOption) {
            return readText(colorOption.value());
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
        } else if (option instanceof ConfigOption.ColorOption colorOption) {
            colorOption.value().set(String.valueOf(value));
        }
    }

    /* 颜色项: 六位十六进制输入框, 右侧实时预览方块, 非法输入时边框与提示变红 */
    private final class ColorField extends EditBox {
        private static final int BOX_WIDTH = 150;
        private static final int SWATCH_SIZE = 16;
        private static final String FALLBACK = "FF0000";
        private final ForgeConfigSpec.ConfigValue<String> holder;
        private String lastValid;

        private ColorField(Font font, ForgeConfigSpec.ConfigValue<String> holder, String initial) {
            super(font, 0, 0, BOX_WIDTH, 20, UiText.of("opt.heading_ruler_color"));
            this.holder = holder;
            this.lastValid = ClientConfig.isValidRgb(initial) ? ClientConfig.normalize(initial) : FALLBACK;
            setMaxLength(7);
            setValue(initial == null ? "" : initial);
            setResponder(value -> {
                if (ClientConfig.isValidRgb(value)) {
                    this.lastValid = ClientConfig.normalize(value);
                }
                try {
                    holder.set(value);
                } catch (IllegalStateException ignored) {
                }
            });
        }

        private boolean isValid() {
            return ClientConfig.isValidRgb(getValue());
        }

        /* 保存时把非法输入回退成最近一次合法值, 保证配置里不留脏数据 */
        private void commitValidValue() {
            if (isValid()) {
                try {
                    holder.set(ClientConfig.normalize(getValue()));
                } catch (IllegalStateException ignored) {
                }
                return;
            }
            setValue(lastValid);
            try {
                holder.set(lastValid);
            } catch (IllegalStateException ignored) {
            }
        }

        private int previewColor() {
            return ClientConfig.parseRgb(isValid() ? getValue() : lastValid, 0xFFFF0000);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isValid() ? BORDER_COLOR : ERROR_COLOR;
            graphics.fill(getX() - 1, getY() - 1, getX() + BOX_WIDTH + 1, getY() + 21, border);
            graphics.fill(getX(), getY(), getX() + BOX_WIDTH, getY() + 20, 0xFF000000);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int swatchX = getX() + BOX_WIDTH + 6;
            graphics.fill(swatchX - 1, getY(), swatchX + SWATCH_SIZE + 1, getY() + SWATCH_SIZE + 2, BORDER_COLOR);
            graphics.fill(swatchX, getY() + 1, swatchX + SWATCH_SIZE, getY() + SWATCH_SIZE + 1, previewColor());
        }
    }

    private record Row(String label, @Nullable AbstractWidget widget, RowKind kind) {
    }

    private enum RowKind {
        GROUP,
        OPTION,
        BUTTON
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
