package com.taczenhancedpatch.config;

/**
 * 遗迹建筑蓝图的来源。
 * 三个取值按界面按钮的循环顺序声明, 默认取两者;
 * 内置与外部在选择时平权, 本项只决定哪些来源参与随机。
 */
public enum RuinBlueprintSource implements ConfigOption.Labeled {
    BUILTIN("opt.blueprint_source.builtin"),
    BOTH("opt.blueprint_source.both"),
    EXTERNAL("opt.blueprint_source.external");

    private final String labelKey;

    RuinBlueprintSource(String labelKey) {
        this.labelKey = labelKey;
    }

    @Override
    public String labelKey() {
        return labelKey;
    }

    public boolean allowsBuiltin() {
        return this != EXTERNAL;
    }

    public boolean allowsExternal() {
        return this != BUILTIN;
    }
}
