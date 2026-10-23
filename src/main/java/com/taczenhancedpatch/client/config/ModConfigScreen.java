package com.taczenhancedpatch.client.config;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 把配置界面挂到模组列表的配置按钮上, 使用双入口的配置入口页。
 */
public final class ModConfigScreen {
    private ModConfigScreen() {
    }

    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigHubScreen(parent)));
    }
}
