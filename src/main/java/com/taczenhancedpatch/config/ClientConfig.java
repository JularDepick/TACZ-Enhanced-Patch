package com.taczenhancedpatch.config;

import net.minecraft.util.Mth;
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

    private static final ForgeConfigSpec.BooleanValue HEALTH_BARS = BUILDER
            .comment("生物血量显示: 在生物与玩家头顶渲染血条与具体数值",
                    "默认开启, 只影响本客户端显示")
            .define("healthBars", true);

    private static final ForgeConfigSpec.BooleanValue HEADING_RULER = BUILDER
            .comment("方向标尺: 屏幕正上方显示横向方位标尺, 随视角滚动并标注基本方向与角度",
                    "只影响本客户端显示, 不改变任何玩法判定")
            .define("headingRuler", false);

    private static final ForgeConfigSpec.ConfigValue<String> HEADING_RULER_COLOR = BUILDER
            .comment("方向标尺的强调色, 6 位十六进制 RGB 颜色, 默认纯红",
                    "写法示例: FF0000 为纯红, 00FF88 为青绿",
                    "配置界面会实时预览所选颜色, 非法值回退到默认纯红")
            .define("headingRulerColor", "FF0000");

    /* 手雷偏好: 六项属性由游戏内按键切换, 只存本机, 投掷时随包下发 */
    private static final ForgeConfigSpec.IntValue GRENADE_FUSE = BUILDER
            .comment("手雷引信档位, -1 表示触发式, 1 到 10 表示按下右键后多少秒爆炸",
                    "手持手雷时用减号与等号切换")
            .defineInRange("grenadeFuse", 3, -1, 10);

    private static final ForgeConfigSpec.BooleanValue GRENADE_STICKY = BUILDER
            .comment("手雷粘性: 开启后第一次碰撞就停住并跟随被撞到的方块, 生物或玩家",
                    "关闭时按碰撞方块的硬度做自适应反弹, 越硬的方块反弹越弱")
            .define("grenadeSticky", false);

    private static final ForgeConfigSpec.IntValue GRENADE_POWER = BUILDER
            .comment("手雷爆炸威力, 零到十六, 对应杀伤半径零到十六格与中心伤害零到基础生命的百分之三百二十",
                    "手持手雷时左方括号与右方括号切换")
            .defineInRange("grenadePower", 4, 0, 16);

    private static final ForgeConfigSpec.ConfigValue<String> GRENADE_TARGET = BUILDER
            .comment("手雷杀伤对象档位, 取值 terrain_only player_only mob_only no_terrain no_player no_mob all",
                    "默认 no_mob 表示破坏地形并杀伤玩家, 手持手雷时用反斜杠切换")
            .define("grenadeTarget", "no_mob");

    private static final ForgeConfigSpec.ConfigValue<String> GRENADE_COLOR = BUILDER
            .comment("手雷配色, 取原版十六种旗帜颜色名, 只影响物品贴图颜色, 不影响爆炸",
                    "默认 green 即墨绿, 手持手雷时用逗号与句号切换")
            .define("grenadeColor", "green");

    private static final ForgeConfigSpec.BooleanValue GRENADE_EFFECTS = BUILDER
            .comment("手雷爆炸特效: 开启后炸点放出光线射线, 威力满档时额外有小型蘑菇云",
                    "手持手雷时用斜杠切换")
            .define("grenadeEffects", true);

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

    public static boolean healthBars() {
        try {
            return HEALTH_BARS.get();
        } catch (IllegalStateException ignored) {
            return true;
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

    public static int grenadeFuse() {
        try {
            return GRENADE_FUSE.get();
        } catch (IllegalStateException ignored) {
            return 3;
        }
    }

    public static boolean grenadeSticky() {
        try {
            return GRENADE_STICKY.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static int grenadePower() {
        try {
            return GRENADE_POWER.get();
        } catch (IllegalStateException ignored) {
            return 4;
        }
    }

    public static String grenadeTarget() {
        try {
            return GRENADE_TARGET.get();
        } catch (IllegalStateException ignored) {
            return "no_mob";
        }
    }

    public static String grenadeColor() {
        try {
            return GRENADE_COLOR.get();
        } catch (IllegalStateException ignored) {
            return "green";
        }
    }

    public static boolean grenadeEffects() {
        try {
            return GRENADE_EFFECTS.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    /* 按键切换后整份写回, 避免六个键分头保存造成半新半旧 */
    public static void setGrenade(int fuse, boolean sticky, int power, String target, String color, boolean effects) {
        try {
            GRENADE_FUSE.set(Mth.clamp(fuse, -1, 10));
            GRENADE_STICKY.set(sticky);
            GRENADE_POWER.set(Mth.clamp(power, 0, 16));
            GRENADE_TARGET.set(target == null || target.isEmpty() ? "no_mob" : target);
            GRENADE_COLOR.set(color == null || color.isEmpty() ? "green" : color);
            GRENADE_EFFECTS.set(effects);
            save();
        } catch (IllegalStateException ignored) {
            /* 配置尚未加载时忽略, 下次切换会重新写入 */
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
                new ConfigOption.ToggleOption("opt.health_bars", HEALTH_BARS),
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
