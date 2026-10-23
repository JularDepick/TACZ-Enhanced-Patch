package com.taczenhancedpatch.config;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;

/**
 * 配置生命周期。
 * 分别保存客户端与服务端配置对象, 供配置界面在修改后立即写盘。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigEvents {
    @Nullable
    private static ModConfig clientConfig;
    @Nullable
    private static ModConfig serverConfig;
    @Nullable
    private static ModConfig mobMiddleConfig;
    @Nullable
    private static ModConfig mobListConfig;

    private ConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == ClientConfig.SPEC) {
            clientConfig = config;
        } else if (config.getSpec() == ServerConfig.SPEC) {
            serverConfig = config;
        } else if (config.getSpec() == MobMiddleConfig.SPEC) {
            mobMiddleConfig = config;
        } else if (config.getSpec() == MobListConfig.SPEC) {
            mobListConfig = config;
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

    @Nullable
    public static ModConfig mobMiddleConfig() {
        return mobMiddleConfig;
    }

    @Nullable
    public static ModConfig mobListConfig() {
        return mobListConfig;
    }
}
