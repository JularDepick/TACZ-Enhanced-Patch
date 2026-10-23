package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 持械生物列表。
 * 内容由中间层根据分组规则与额外指定展开后写入, 界面不直接编辑本文件。
 */
public final class MobListConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOBS = BUILDER
            .comment("持械生物列表, 由中间层按分组规则自动展开生成",
                    "请勿直接编辑; 需要调整范围时修改中间层配置或在持械生物列表页操作")
            .defineListAllowEmpty("mobs", List.of(), element -> element instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MobListConfig() {
    }

    public static List<String> mobs() {
        try {
            return List.copyOf(MOBS.get());
        } catch (IllegalStateException ignored) {
            return List.of();
        }
    }

    public static void setMobs(List<String> ids) {
        try {
            MOBS.set(List.copyOf(ids));
        } catch (IllegalStateException ignored) {
        }
    }

    public static void save() {
        var config = ConfigEvents.mobListConfig();
        if (config != null) {
            config.save();
        }
    }
}
