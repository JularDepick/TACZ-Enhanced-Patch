package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 配置项描述。
 * 只描述配置界面需要渲染的项, 不承担配置值的读盘与保存。
 * 客户端配置与服务端配置各自提供选项列表, 界面按当前权限决定展示哪些。
 */
public sealed interface ConfigOption {
    String label();

    record ToggleOption(String label, ForgeConfigSpec.BooleanValue value) implements ConfigOption {
    }

    record DoubleOption(String label, ForgeConfigSpec.DoubleValue value, double min, double max, double step) implements ConfigOption {
    }

    record IntOption(String label, ForgeConfigSpec.IntValue value, int min, int max) implements ConfigOption {
    }

    record TextOption(String label, ForgeConfigSpec.ConfigValue<String> value) implements ConfigOption {
    }

    /* 六位十六进制颜色项: 界面按颜色格式校验并实时预览 */
    record ColorOption(String label, ForgeConfigSpec.ConfigValue<String> value) implements ConfigOption {
    }

    /* 枚举项: 按钮按枚举声明顺序循环切换, 取值文本由枚举自身提供 */
    record EnumOption<E extends Enum<E> & Labeled>(String label, ForgeConfigSpec.EnumValue<E> value) implements ConfigOption {
    }

    /* 枚举取值的界面文本键, 不含模组前缀 */
    interface Labeled {
        String labelKey();
    }
}
