package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 持械生物中间层配置。
 * 这里只记录选择规则: 勾选了哪些分组, 以及额外指定了哪些具体实体。
 * 规则展开出的具体生物 ID 由中间层写入独立的列表配置文件, 列表文件不供直接编辑。
 */
public final class MobMiddleConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue GROUP_UNDEAD = BUILDER
            .comment("分组: 亡灵, 包含僵尸与骷髅的各类变种")
            .define("groupUndead", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_ILLAGER = BUILDER
            .comment("分组: 灾厄村民阵营, 包含掠夺者, 卫道士, 唤魔者, 劫掠兽与女巫")
            .define("groupIllager", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_ARTHROPOD = BUILDER
            .comment("分组: 节肢, 包含蜘蛛, 洞穴蜘蛛, 蠹虫与末影螨")
            .define("groupArthropod", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_CREEPER = BUILDER
            .comment("分组: 苦力怕")
            .define("groupCreeper", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_NETHER = BUILDER
            .comment("分组: 下界生物, 包含烈焰人, 恶魂, 猪灵, 疣猪兽与岩浆怪")
            .define("groupNether", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_HUMANOID = BUILDER
            .comment("分组: 能正常持握物品的类型")
            .define("groupHumanoid", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_NON_HUMANOID = BUILDER
            .comment("分组: 没有手部挂载部位的类型")
            .define("groupNonHumanoid", false);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_IDS = BUILDER
            .comment("额外指定的持械生物实体 ID, 可以是非敌对生物",
                    "是否主动攻击仍由生物自身的敌意规则决定")
            .defineListAllowEmpty("extraIds", List.of(), element -> element instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MobMiddleConfig() {
    }

    public static List<ConfigOption> groupOptions() {
        return List.of(
                new ConfigOption.ToggleOption("group.undead", GROUP_UNDEAD),
                new ConfigOption.ToggleOption("group.illager", GROUP_ILLAGER),
                new ConfigOption.ToggleOption("group.arthropod", GROUP_ARTHROPOD),
                new ConfigOption.ToggleOption("group.creeper", GROUP_CREEPER),
                new ConfigOption.ToggleOption("group.nether", GROUP_NETHER),
                new ConfigOption.ToggleOption("group.humanoid", GROUP_HUMANOID),
                new ConfigOption.ToggleOption("group.non_humanoid", GROUP_NON_HUMANOID));
    }

    public static boolean groupUndead() {
        return value(GROUP_UNDEAD);
    }

    public static boolean groupIllager() {
        return value(GROUP_ILLAGER);
    }

    public static boolean groupArthropod() {
        return value(GROUP_ARTHROPOD);
    }

    public static boolean groupCreeper() {
        return value(GROUP_CREEPER);
    }

    public static boolean groupNether() {
        return value(GROUP_NETHER);
    }

    public static boolean groupHumanoid() {
        return value(GROUP_HUMANOID);
    }

    public static boolean groupNonHumanoid() {
        return value(GROUP_NON_HUMANOID);
    }

    public static List<String> extraIds() {
        try {
            return List.copyOf(EXTRA_IDS.get());
        } catch (IllegalStateException ignored) {
            return List.of();
        }
    }

    public static void setExtraIds(List<String> ids) {
        try {
            EXTRA_IDS.set(List.copyOf(ids));
        } catch (IllegalStateException ignored) {
        }
    }

    /* 是否已经设置了任何规则, 未设置时中间层按默认规则展开 */
    public static boolean hasRules() {
        return groupUndead() || groupIllager() || groupArthropod() || groupCreeper()
                || groupNether() || groupHumanoid() || groupNonHumanoid() || !extraIds().isEmpty();
    }

    private static boolean value(ForgeConfigSpec.BooleanValue holder) {
        try {
            return holder.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public static void save() {
        var config = ConfigEvents.mobMiddleConfig();
        if (config != null) {
            config.save();
        }
    }
}
