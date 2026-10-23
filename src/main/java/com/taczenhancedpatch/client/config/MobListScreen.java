package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.config.ConfigOption;
import com.taczenhancedpatch.config.MobListConfig;
import com.taczenhancedpatch.config.MobMiddleConfig;
import com.taczenhancedpatch.gunner.GunnerMobRegistry;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 持械生物列表。
 * 只决定哪些生物可以成为枪手: 可以按分组勾选, 也可以逐个填写实体 ID;
 * 界面只修改中间层规则, 具体名单由中间层展开后写入独立列表文件。
 * 本页不提供任何按生物调整的数值, 数值参数统一在服务端配置页里改。
 */
public final class MobListScreen extends Screen {
    private static final int GROUP_WIDTH = 150;
    private static final int GROUP_HEIGHT = 20;
    private static final int GROUP_GAP = 6;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 320;
    private static final int REMOVE_WIDTH = 52;

    private final Screen parent;
    private final List<ConfigOption> groups;
    private final List<String> mobIds = new ArrayList<>();
    private final List<IdRow> idRows = new ArrayList<>();
    @Nullable
    private EditBox idBox;
    private String hint = "";
    private int hintColor = 0x9E9E9E;
    private double scroll;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;

    public MobListScreen(@Nullable Screen parent) {
        super(UiText.of("moblist.title"));
        this.parent = parent;
        this.groups = MobMiddleConfig.groupOptions();
        this.mobIds.addAll(MobMiddleConfig.extraIds());
    }

    @Override
    protected void init() {
        idRows.clear();
        int columnLeft = (width - GROUP_WIDTH * 2 - GROUP_GAP) / 2;
        int groupsTop = 44;
        for (int i = 0; i < groups.size(); i++) {
            ConfigOption option = groups.get(i);
            if (!(option instanceof ConfigOption.ToggleOption toggle)) {
                continue;
            }
            int column = i % 2;
            int row = i / 2;
            Button button = Button.builder(groupText(toggle), b -> {
                toggle.value().set(!Boolean.TRUE.equals(toggle.value().get()));
                b.setMessage(groupText(toggle));
                persist();
            }).bounds(columnLeft + column * (GROUP_WIDTH + GROUP_GAP), groupsTop + row * (GROUP_HEIGHT + 2),
                    GROUP_WIDTH, GROUP_HEIGHT).build();
            addRenderableWidget(button);
        }
        int controlsY = groupsTop + 4 * (GROUP_HEIGHT + 2) + 8;
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        listTop = controlsY + 28;
        listBottom = Math.max(listTop + ROW_HEIGHT, height - 44);

        idBox = new EditBox(font, listLeft, controlsY, LIST_WIDTH - 100, 20, UiText.of("moblist.title"));
        idBox.setHint(UiText.of("moblist.input_hint"));
        idBox.setMaxLength(96);
        addRenderableWidget(idBox);
        addRenderableWidget(Button.builder(UiText.of("moblist.add"), b -> addId())
                .bounds(listRight - 94, controlsY, 94, 20)
                .build());
        addRenderableWidget(Button.builder(UiText.of("moblist.done"), b -> onClose())
                .bounds(width / 2 - 155, height - 34, 150, 20)
                .build());
        addRenderableWidget(Button.builder(UiText.of("moblist.rescan"), b -> rescan())
                .bounds(width / 2 + 5, height - 34, 150, 20)
                .build());

        for (int i = 0; i < mobIds.size(); i++) {
            String id = mobIds.get(i);
            Button remove = Button.builder(UiText.of("moblist.remove"), b -> removeId(id))
                    .bounds(0, 0, REMOVE_WIDTH, 18)
                    .build();
            idRows.add(new IdRow(id, remove));
        }
    }

    private static Component groupText(ConfigOption.ToggleOption toggle) {
        String state = UiText.plain(Boolean.TRUE.equals(toggle.value().get()) ? "screen.on" : "screen.off");
        return Component.literal(UiText.plain(toggle.label()) + ": " + state);
    }

    private void addId() {
        if (idBox == null) {
            return;
        }
        String raw = idBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            setHint(UiText.plain("moblist.need_id"), 0xFF8080);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            setHint(UiText.of("moblist.unknown_id", raw).getString(), 0xFF8080);
            return;
        }
        if (mobIds.contains(raw)) {
            setHint(UiText.plain("moblist.duplicate"), 0xFF8080);
            return;
        }
        mobIds.add(raw);
        persist();
        idBox.setValue("");
        setHint(UiText.of("moblist.added", raw).getString(), 0x80FF80);
    }

    private void removeId(String id) {
        mobIds.remove(id);
        persist();
        setHint(UiText.of("moblist.removed", id).getString(), 0x80FF80);
    }

    /* 手动重扫: 按当前规则重新展开注册表并写回列表文件 */
    private void rescan() {
        GunnerMobRegistry.refresh();
        setHint(UiText.of("moblist.rescanned", MobListConfig.mobs().size()).getString(), 0x80FF80);
    }

    /* 中间层负责把规则展开成具体列表, 界面只改规则 */
    private void persist() {
        MobMiddleConfig.setExtraIds(mobIds);
        MobMiddleConfig.save();
        GunnerMobRegistry.refresh();
        clearWidgets();
        init();
    }

    private void setHint(String text, int color) {
        hint = text;
        hintColor = color;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, UiText.of("moblist.hint_default"), width / 2, 30, 0x9E9E9E);
        graphics.drawCenteredString(font, Component.literal(hint.isEmpty() ? UiText.plain("moblist.hint_idle") : hint),
                width / 2, listTop - 12, hint.isEmpty() ? 0x9E9E9E : hintColor);
        graphics.fill(listLeft - 4, listTop - 4, listRight + 4, listBottom + 4, 0x66000000);
        scroll = Mth.clamp(scroll, 0.0D, maxScroll());
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = 0; i < idRows.size(); i++) {
            IdRow row = idRows.get(i);
            int rowY = listTop + i * ROW_HEIGHT - (int) scroll;
            boolean visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            row.button().visible = visible;
            if (!visible) {
                continue;
            }
            row.button().setX(listRight - REMOVE_WIDTH - 4);
            row.button().setY(rowY + 2);
            graphics.drawString(font, row.id(), listLeft + 8, rowY + 6, 0xE0E0E0, false);
            row.button().render(graphics, mouseX, mouseY, partialTick);
        }
        if (idRows.isEmpty()) {
            graphics.drawString(font, UiText.of("moblist.empty"), listLeft + 8, listTop + 6, 0x808080, false);
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private double maxScroll() {
        int contentHeight = idRows.size() * ROW_HEIGHT;
        int viewHeight = Math.max(1, listBottom - listTop);
        return Math.max(0.0D, contentHeight - viewHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
        if (inside) {
            for (IdRow row : idRows) {
                if (row.button().visible && row.button().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private record IdRow(String id, Button button) {
    }
}
