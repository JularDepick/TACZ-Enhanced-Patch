package com.taczenhancedpatch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.client.config.PatchConfigScreen;
import com.taczenhancedpatch.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 服务端配置页快捷键。
 * 默认 Shift + C, 主键可在服务端配置页里改; 只在存档创建者一侧生效,
 * 并且只在没有打开任何界面时触发, 避免与聊天和物品栏冲突。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class ConfigShortcutHandler {
    private static boolean lastDown;

    private ConfigShortcutHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = isComboDown(minecraft);
        boolean pressed = down && !lastDown;
        lastDown = down;
        if (!pressed || minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!ConfigAccess.isWorldOwner()) {
            return;
        }
        minecraft.setScreen(new PatchConfigScreen(null, true));
    }

    private static boolean isComboDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        return shift && InputConstants.isKeyDown(window, ClientConfig.serverConfigKey());
    }
}
