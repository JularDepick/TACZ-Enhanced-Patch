package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端入口。
 * 仅在物理客户端加载, 用于注册依赖客户端环境的补丁内容与原版按键绑定。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TACZEnhancedPatchClient {
    private TACZEnhancedPatchClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TACZEnhancedPatch.LOGGER.debug("{} client setup finished", TACZEnhancedPatch.MOD_NAME);
    }

    /* 把配置入口按键注册到原版按键设置 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ConfigShortcutHandler.OPEN_CONFIG);
    }
}
