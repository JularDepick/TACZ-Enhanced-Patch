package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * 客户端配置。
 * 由各客户端各自持有, 写入 config/tacz_enhanced_patch-client.toml, 不参与服务端判定。
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue AUTO_RELOAD = BUILDER
            .comment("手中枪械弹匣打空时自动发起换弹",
                    "只影响本客户端的操作习惯, 与上游模组自带的自动换弹互不影响")
            .define("autoReload", false);

    /* 默认值 67 对应 GLFW 的 C 键, 这里用字面量避免在服务端加载客户端输入库 */
    private static final ForgeConfigSpec.IntValue SERVER_CONFIG_KEY = BUILDER
            .comment("打开服务端配置页的快捷键主键, 取值是 GLFW 键码, 默认 67 即 C 键",
                    "实际触发需要同时按住 Shift, 该快捷键只在存档创建者一侧生效")
            .defineInRange("serverConfigKey", 67, 32, 348);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static boolean autoReload() {
        try {
            return AUTO_RELOAD.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static int serverConfigKey() {
        try {
            return SERVER_CONFIG_KEY.get();
        } catch (IllegalStateException ignored) {
            return 67;
        }
    }

    public static void setServerConfigKey(int keyCode) {
        try {
            SERVER_CONFIG_KEY.set(keyCode);
        } catch (IllegalStateException ignored) {
            return;
        }
        save();
    }

    public static List<ConfigOptionGroup> groups() {
        return List.of(new ConfigOptionGroup("本机偏好", List.of(
                new ConfigOption.ToggleOption("自动换弹", AUTO_RELOAD))));
    }

    public static void save() {
        ModConfig config = ConfigEvents.clientConfig();
        if (config != null) {
            config.save();
        }
    }
}
