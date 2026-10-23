package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 通用配置。
 * 配置文件由 Forge 生成于 config/tacz_enhanced_patch-common.toml。
 */
public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DEBUG_EVENTS = BUILDER
            .comment("是否把 TACZ API 事件写入日志, 用于补丁开发期的接入诊断")
            .define("debugEvents", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private CommonConfig() {
    }

    public static boolean debugEvents() {
        return DEBUG_EVENTS.get();
    }
}
