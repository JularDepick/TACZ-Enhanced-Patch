package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.config.MobListConfig;
import com.taczenhancedpatch.config.MobMiddleConfig;
import com.taczenhancedpatch.gunner.GunnerMobRegistry;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 持械生物列表。
 * 顶部七个分组开关用于批量改写列表里的开关状态, 下方是扫描得到的全部生物与逐项开关。
 * 新的扫描会直接登记全部生物并把开关置为关闭, 只有开关为开的生物才能成为枪手。
 * 本页不提供任何按生物调整的数值, 数值参数统一在服务端配置页里改。
 */
public final class MobListScreen extends Screen {
    private static final int TOGGLE_WIDTH = 64;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 420;
    private static final int SEARCH_HEIGHT = 20;

    private final Screen parent;
    private final List<MobMiddleConfig.GroupSwitch> groups;
    private final List<Row> rows = new ArrayList<>();
    private final ScrollBar scrollbar = new ScrollBar(value -> scroll = value);
    @Nullable
    private EditBox searchBox;
    private String hint = "";
    private int hintColor = 0x9E9E9E;
    private double scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int totalMobs;
    private int enabledMobs;

    public MobListScreen(@Nullable Screen parent) {
        super(UiText.of("moblist.title"));
        this.parent = parent;
        this.groups = MobMiddleConfig.groups();
    }

    @Override
    protected void init() {
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        int searchY = 34;
        int searchWidth = LIST_WIDTH - 120;
        searchBox = new EditBox(font, listLeft, searchY, searchWidth, SEARCH_HEIGHT, UiText.of("moblist.title"));
        searchBox.setHint(UiText.of("moblist.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(text -> {
            scroll = 0.0D;
            rebuild();
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(UiText.of("moblist.rescan"), b -> rescan())
                .bounds(listRight - 112, searchY, 112, SEARCH_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(UiText.of("moblist.done"), b -> onClose())
                .bounds(width / 2 - 80, height - 30, 160, 20)
                .build());
        listTop = searchY + SEARCH_HEIGHT + 16;
        listBottom = height - 38;
        rebuild();
    }

    /* 扫全部生物写入列表, 之后按当前搜索条件重建行 */
    private void rescan() {
        GunnerMobRegistry.refresh();
        rebuild();
        setHint(UiText.of("moblist.rescanned", totalMobs).getString(), 0x80FF80);
    }

    private void rebuild() {
        rows.clear();
        for (MobMiddleConfig.GroupSwitch group : groups) {
            Button button = Button.builder(groupText(group), b -> toggleGroup(group, b))
                    .bounds(0, 0, TOGGLE_WIDTH, 18)
                    .build();
            rows.add(new Row(group.label(), button, true));
        }
        Map<String, Boolean> states = MobListConfig.states();
        totalMobs = states.size();
        enabledMobs = 0;
        List<String> ids = new ArrayList<>(states.keySet());
        ids.sort(Comparator.naturalOrder());
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (String id : ids) {
            if (Boolean.TRUE.equals(states.get(id))) {
                enabledMobs++;
            }
            if (!query.isEmpty() && !id.contains(query)) {
                continue;
            }
            Button button = Button.builder(stateText(states.get(id)), b -> toggleMob(id, b))
                    .bounds(0, 0, TOGGLE_WIDTH, 18)
                    .build();
            rows.add(new Row(id, button, false));
        }
        scroll = Mth.clamp(scroll, 0.0D, maxScroll());
    }

    private void toggleGroup(MobMiddleConfig.GroupSwitch group, Button button) {
        boolean next = !group.getter().getAsBoolean();
        int changed = GunnerMobRegistry.applyGroup(group, next);
        button.setMessage(groupText(group));
        rebuildRowStates();
        rebuild();
        setHint(UiText.of("moblist.group_applied", UiText.plain(group.label()), changed).getString(), 0x80FF80);
    }

    private void toggleMob(String id, Button button) {
        boolean next = !MobListConfig.isEnabled(id);
        GunnerMobRegistry.setEnabled(id, next);
        button.setMessage(stateText(next));
        rebuildRowStates();
        enabledMobs = 0;
        for (Boolean enabled : MobListConfig.states().values()) {
            if (Boolean.TRUE.equals(enabled)) {
                enabledMobs++;
            }
        }
    }

    /* 批量改写后刷新所有开关按钮的显示文字 */
    private void rebuildRowStates() {
        Map<String, Boolean> states = MobListConfig.states();
        for (Row row : rows) {
            if (row.group() || row.label().isEmpty()) {
                continue;
            }
            row.button().setMessage(stateText(states.get(row.label())));
        }
    }

    private static Component stateText(@Nullable Boolean enabled) {
        return Component.literal(UiText.plain(Boolean.TRUE.equals(enabled) ? "screen.on" : "screen.off"));
    }

    private static Component groupText(MobMiddleConfig.GroupSwitch group) {
        return stateText(group.getter().getAsBoolean());
    }

    private void setHint(String text, int color) {
        hint = text;
        hintColor = color;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(hint.isEmpty() ? UiText.plain("moblist.hint_default") : hint),
                width / 2, 24, hint.isEmpty() ? 0x9E9E9E : hintColor);
        graphics.drawString(font, UiText.of("moblist.count", totalMobs, enabledMobs).getString(),
                listLeft, listTop - 12, 0x9E9E9E, false);
        graphics.fill(listLeft - 4, listTop - 4, listRight + 20, listBottom + 4, 0x66000000);
        scroll = Mth.clamp(scroll, 0.0D, maxScroll());
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + i * ROW_HEIGHT - (int) scroll;
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            Button button = row.button();
            button.visible = visible;
            if (!visible) {
                continue;
            }
            button.setX(listRight - TOGGLE_WIDTH - 6);
            button.setY(rowY + 1);
            graphics.drawString(font, UiText.plain(row.label()), listLeft + 8, rowY + 6,
                    row.group() ? 0xFFD080 : 0xE0E0E0, false);
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        if (rows.size() == groups.size()) {
            graphics.drawString(font, UiText.of("moblist.empty").getString(), listLeft + 8, listTop + 6, 0x808080, false);
        }
        graphics.disableScissor();
        scrollbar.update(listRight + 8, listTop, listBottom, scroll, maxScroll());
        scrollbar.render(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private double maxScroll() {
        int contentHeight = rows.size() * ROW_HEIGHT;
        int viewHeight = Math.max(1, listBottom - listTop);
        return Math.max(0.0D, contentHeight - viewHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        boolean inside = mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
        if (inside) {
            for (Row row : rows) {
                if (row.button().visible && row.button().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollbar.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbar.isDragging()) {
            scrollbar.mouseDragged(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom && maxScroll() > 0.0D) {
            scroll = Mth.clamp(scroll - delta * ROW_HEIGHT, 0.0D, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(String label, Button button, boolean group) {
    }
}
