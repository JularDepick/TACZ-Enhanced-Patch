package com.taczenhancedpatch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.client.config.PatchConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 模组配置快捷键。
 * 绑定注册为原版按键项, 默认 C 键, 玩家可以在原版按键设置里改绑或解绑;
 * 触发后在游戏内直接打开配置页, 服务端页签只在存档创建者一侧出现, 因此非创建者只会看到客户端页。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class ConfigShortcutHandler {
    public static final String KEY_CATEGORY = "key.categories." + TACZEnhancedPatch.MOD_ID;

    /* 原版按键设置里的配置入口按键, 默认 C */
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key." + TACZEnhancedPatch.MOD_ID + ".open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            KEY_CATEGORY);

    private ConfigShortcutHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean open = false;
        while (OPEN_CONFIG.consumeClick()) {
            open = true;
        }
        if (!open) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        /* 只在没有打开任何界面时触发, 避免与聊天和物品栏冲突 */
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new PatchConfigScreen(null, false));
    }
}
