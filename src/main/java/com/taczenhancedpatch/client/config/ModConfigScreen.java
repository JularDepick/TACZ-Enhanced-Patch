package com.taczenhancedpatch.client.config;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 把配置页挂到模组列表的配置按钮上。
 * 按钮直接打开配置页本身, 存档创建者可在页内用顶部页签切到服务端参数,
 * 不再经过只放了两个按钮的中间页。
 */
public final class ModConfigScreen {
    private ModConfigScreen() {
    }

    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new PatchConfigScreen(parent, false)));
    }
}
