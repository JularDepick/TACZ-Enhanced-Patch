package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 客户端配置。
 * 由各客户端各自持有, 写入 config/tacz_enhanced_patch-client.toml, 不参与服务端判定。
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /* 方向标尺的默认配色: 不透明纯红 */
    private static final int DEFAULT_HEADING_COLOR = 0xFFFF0000;

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

    private static final ForgeConfigSpec.ConfigValue<String> HEADING_RULER_COLOR = BUILDER
            .comment("方向标尺的强调色, 6 位十六进制 RGB 颜色, 默认纯红",
                    "写法示例: FF0000 为纯红, 00FF88 为青绿",
                    "配置界面会实时预览所选颜色, 非法值回退到默认纯红")
            .define("headingRulerColor", "FF0000");

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

    /* 方向标尺的强调色, 读取失败或写法非法时回退默认纯红 */
    public static int headingRulerColor() {
        try {
            return parseRgb(HEADING_RULER_COLOR.get(), DEFAULT_HEADING_COLOR);
        } catch (IllegalStateException ignored) {
            return DEFAULT_HEADING_COLOR;
        }
    }

    /* 是否为合法的 6 位十六进制 RGB 文本, 配置界面的输入校验共用此判定 */
    public static boolean isValidRgb(@Nullable String raw) {
        if (raw == null) {
            return false;
        }
        String text = normalize(raw);
        if (text.length() != 6) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /* 把 6 位十六进制文本解析为不透明 ARGB, 非法时返回兜底色 */
    public static int parseRgb(@Nullable String raw, int fallback) {
        if (!isValidRgb(raw)) {
            return fallback;
        }
        int rgb = (int) Long.parseLong(normalize(raw), 16);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /* 去掉首尾空白与井号, 便于界面输入与解析共用 */
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        while (text.startsWith("#")) {
            text = text.substring(1);
        }
        return text;
    }

    public static List<ConfigOptionGroup> groups() {
        return List.of(new ConfigOptionGroup("group.client_prefs", List.of(
                new ConfigOption.ToggleOption("opt.auto_reload", AUTO_RELOAD),
                new ConfigOption.ToggleOption("opt.hit_indicator", HIT_INDICATOR),
                new ConfigOption.ToggleOption("opt.heading_ruler", HEADING_RULER),
                new ConfigOption.ColorOption("opt.heading_ruler_color", HEADING_RULER_COLOR))));
    }

    public static void save() {
        ModConfig config = ConfigEvents.clientConfig();
        if (config != null) {
            config.save();
        }
    }
}
