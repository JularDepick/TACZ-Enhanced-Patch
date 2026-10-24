package com.taczenhancedpatch.config;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;

/**
 * 配置生命周期。
 * 保存客户端与服务端配置对象, 供配置界面在修改后立即写盘;
 * 生物名单与分组改用独立 JSON 存储后不再需要在这里登记。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigEvents {
    @Nullable
    private static ModConfig clientConfig;
    @Nullable
    private static ModConfig serverConfig;

    private ConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == ClientConfig.SPEC) {
            clientConfig = config;
        } else if (config.getSpec() == ServerConfig.SPEC) {
            serverConfig = config;
        }
    }

    @Nullable
    public static ModConfig clientConfig() {
        return clientConfig;
    }

    @Nullable
    public static ModConfig serverConfig() {
        return serverConfig;
    }
}
