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

    private static final ForgeConfigSpec.BooleanValue HIT_INDICATOR = BUILDER
            .comment("枪击来源提示: 被枪械子弹命中时在屏幕上用弧形指向伤害来源",
                    "只影响本客户端显示, 停止受击一秒后自动淡出")
            .define("hitIndicator", false);

    private static final ForgeConfigSpec.BooleanValue HEADING_RULER = BUILDER
            .comment("方向标尺: 屏幕正上方显示横向方位标尺, 随视角滚动并标注基本方向与角度",
                    "只影响本客户端显示, 不改变任何玩法判定")
            .define("headingRuler", false);

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

    public static boolean hitIndicator() {
        try {
            return HIT_INDICATOR.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static boolean headingRuler() {
        try {
            return HEADING_RULER.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static List<ConfigOptionGroup> groups() {
        return List.of(new ConfigOptionGroup("group.client_prefs", List.of(
                new ConfigOption.ToggleOption("opt.auto_reload", AUTO_RELOAD),
                new ConfigOption.ToggleOption("opt.hit_indicator", HIT_INDICATOR),
                new ConfigOption.ToggleOption("opt.heading_ruler", HEADING_RULER))));
    }

    public static void save() {
        ModConfig config = ConfigEvents.clientConfig();
        if (config != null) {
            config.save();
        }
    }
}
