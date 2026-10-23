package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.config.ConfigOption;
import com.taczenhancedpatch.config.ServerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 持械生物列表。
 * 只决定哪些生物可以成为枪手: 可以按分组勾选, 也可以逐个填写实体 ID;
 * 分组与名单都没有内容时沿用默认规则, 按非人形持械开关判定全部敌对生物。
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
        super(Component.literal("持械生物列表"));
        this.parent = parent;
        this.groups = ServerConfig.mobGroupOptions();
        this.mobIds.addAll(ServerConfig.gunnerMobIds());
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
                ServerConfig.save();
            }).bounds(columnLeft + column * (GROUP_WIDTH + GROUP_GAP), groupsTop + row * (GROUP_HEIGHT + 2),
                    GROUP_WIDTH, GROUP_HEIGHT).build();
            addRenderableWidget(button);
        }
        int controlsY = groupsTop + 4 * (GROUP_HEIGHT + 2) + 8;
        listLeft = (width - LIST_WIDTH) / 2;
        listRight = listLeft + LIST_WIDTH;
        listTop = controlsY + 28;
        listBottom = Math.max(listTop + ROW_HEIGHT, height - 44);

        idBox = new EditBox(font, listLeft, controlsY, LIST_WIDTH - 100, 20, Component.literal("实体 ID"));
        idBox.setHint(Component.literal("例如 minecraft:creeper"));
        idBox.setMaxLength(96);
        addRenderableWidget(idBox);
        addRenderableWidget(Button.builder(Component.literal("添加"), b -> addId())
                .bounds(listRight - 94, controlsY, 94, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(width / 2 - 75, height - 34, 150, 20)
                .build());

        for (int i = 0; i < mobIds.size(); i++) {
            String id = mobIds.get(i);
            Button remove = Button.builder(Component.literal("移除"), b -> removeId(id))
                    .bounds(0, 0, REMOVE_WIDTH, 18)
                    .build();
            idRows.add(new IdRow(id, remove));
        }
    }

    private static Component groupText(ConfigOption.ToggleOption toggle) {
        return Component.literal(toggle.label() + ": " + (Boolean.TRUE.equals(toggle.value().get()) ? "开" : "关"));
    }

    private void addId() {
        if (idBox == null) {
            return;
        }
        String raw = idBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            setHint("先填写实体 ID", 0xFF8080);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            setHint("找不到该实体: " + raw, 0xFF8080);
            return;
        }
        if (mobIds.contains(raw)) {
            setHint("列表中已有该实体", 0xFF8080);
            return;
        }
        mobIds.add(raw);
        persist();
        idBox.setValue("");
        setHint("已添加 " + raw, 0x80FF80);
    }

    private void removeId(String id) {
        mobIds.remove(id);
        persist();
        setHint("已移除 " + id, 0x80FF80);
    }

    private void persist() {
        ServerConfig.setGunnerMobIds(mobIds);
        ServerConfig.save();
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
        graphics.drawCenteredString(font,
                Component.literal("分组与名单都没有内容时, 按非人形持械开关判定全部敌对生物"),
                width / 2, 30, 0x9E9E9E);
        graphics.drawCenteredString(font, Component.literal(hint.isEmpty() ? "仅决定哪些生物可以持械" : hint),
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
            graphics.drawString(font, "名单为空", listLeft + 8, listTop + 6, 0x808080, false);
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
