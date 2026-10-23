package com.taczenhancedpatch.config;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;

/**
 * 配置生命周期。
 * 保存当前通用配置对象, 供配置界面在修改后立即写盘。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigEvents {
    @Nullable
    private static ModConfig commonConfig;

    private ConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CommonConfig.SPEC) {
            commonConfig = event.getConfig();
        }
    }

    @Nullable
    public static ModConfig commonConfig() {
        return commonConfig;
    }
}
