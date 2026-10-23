package com.taczenhancedpatch.client.config;

import com.taczenhancedpatch.client.ConfigAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 模组配置入口。
 * 进入后只展示客户端与服务端两个入口按钮, 服务端入口仅在存档创建者一侧出现,
 * 点击任一按钮才进入对应参数的列表页。
 */
public final class ConfigHubScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;

    public ConfigHubScreen(@Nullable Screen parent) {
        super(Component.literal("TACZ 增强补丁配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean owner = ConfigAccess.isWorldOwner();
        int buttonY = height / 2 - BUTTON_HEIGHT / 2;
        if (owner) {
            int left = (width - BUTTON_WIDTH * 2 - BUTTON_GAP) / 2;
            addRenderableWidget(Button.builder(Component.literal("客户端配置"), button ->
                            openSection(false))
                    .bounds(left, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("服务端配置"), button ->
                            openSection(true))
                    .bounds(left + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("客户端配置"), button ->
                            openSection(false))
                    .bounds((width - BUTTON_WIDTH) / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void openSection(boolean serverSection) {
        if (minecraft != null) {
            minecraft.setScreen(new PatchConfigScreen(this, serverSection));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 46, 0xFFFFFF);
        if (!ConfigAccess.isWorldOwner()) {
            graphics.drawCenteredString(font, Component.literal("服务端设置由存档创建者或服务器管理员修改"),
                    width / 2, height / 2 + 26, 0x9E9E9E);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
}
