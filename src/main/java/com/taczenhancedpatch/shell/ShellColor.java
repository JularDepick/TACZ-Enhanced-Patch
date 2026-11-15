package com.taczenhancedpatch.shell;

/*
 * 炮弹配色。
 * 前十六项沿用原版旗帜颜色, 后四项是自建的迷彩色, 取自小兵的迷彩贴图。
 * 配色只影响物品染色与界面文案, 不参与任何爆炸判定。
 */
public enum ShellColor {
    WHITE("white", 0xF9FFFE),
    ORANGE("orange", 0xF9801D),
    MAGENTA("magenta", 0xC74EBD),
    LIGHT_BLUE("light_blue", 0x3AB3DA),
    YELLOW("yellow", 0xFED83D),
    LIME("lime", 0x80C71F),
    PINK("pink", 0xF38BAA),
    GRAY("gray", 0x474F52),
    LIGHT_GRAY("light_gray", 0x9D9D97),
    CYAN("cyan", 0x169C9C),
    PURPLE("purple", 0x8932B8),
    BLUE("blue", 0x3C44AA),
    BROWN("brown", 0x835432),
    GREEN("green", 0x5E7C16),
    RED("red", 0xB02E26),
    BLACK("black", 0x1D1D21),
    CAMO_GREEN("camo_green", 0x4E5B3C),
    CAMO_DESERT("camo_desert", 0x8A7A52),
    CAMO_URBAN("camo_urban", 0x555B5E),
    CAMO_SNOW("camo_snow", 0xC3C8CC);

    private final String id;
    private final int rgb;

    ShellColor(String id, int rgb) {
        this.id = id;
        this.rgb = rgb;
    }

    public String id() {
        return id;
    }

    public int rgb() {
        return rgb;
    }

    public String labelKey() {
        return "grenade.color." + id;
    }

    public static ShellColor byId(String id, ShellColor fallback) {
        for (ShellColor value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return fallback;
    }

    /* 遗迹旗帜颜色名与配色 id 同名, 直接复用同一套查表 */
    public static ShellColor byDyeName(String name, ShellColor fallback) {
        return byId(name, fallback);
    }
}
