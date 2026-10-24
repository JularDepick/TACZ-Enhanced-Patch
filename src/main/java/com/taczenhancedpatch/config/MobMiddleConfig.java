package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 持械生物中间层配置。
 * 只记录七个分组开关的开关状态, 作为批量改写持械生物列表的依据;
 * 具体哪些生物可以持械由 {@link MobListConfig} 记录, 分组开关只是它的批量编辑器。
 */
public final class MobMiddleConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue GROUP_UNDEAD = BUILDER
            .comment("分组开关: 亡灵, 包含僵尸与骷髅的各类变种")
            .define("groupUndead", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_ILLAGER = BUILDER
            .comment("分组开关: 灾厄村民阵营, 包含掠夺者, 卫道士, 唤魔者, 劫掠兽与女巫")
            .define("groupIllager", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_ARTHROPOD = BUILDER
            .comment("分组开关: 节肢, 包含蜘蛛, 洞穴蜘蛛, 蠹虫与末影螨")
            .define("groupArthropod", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_CREEPER = BUILDER
            .comment("分组开关: 苦力怕")
            .define("groupCreeper", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_NETHER = BUILDER
            .comment("分组开关: 下界生物, 包含烈焰人, 恶魂, 猪灵, 疣猪兽与岩浆怪")
            .define("groupNether", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_HUMANOID = BUILDER
            .comment("分组开关: 能正常持握物品的类型")
            .define("groupHumanoid", false);
    private static final ForgeConfigSpec.BooleanValue GROUP_NON_HUMANOID = BUILDER
            .comment("分组开关: 没有手部挂载部位的类型")
            .define("groupNonHumanoid", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /* 分组开关: key 供中间层匹配生物, label 是界面文本键 */
    public record GroupSwitch(String key, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
    }

    private MobMiddleConfig() {
    }

    public static List<GroupSwitch> groups() {
        return List.of(
                new GroupSwitch("undead", "group.undead", () -> value(GROUP_UNDEAD), enabled -> set(GROUP_UNDEAD, enabled)),
                new GroupSwitch("illager", "group.illager", () -> value(GROUP_ILLAGER), enabled -> set(GROUP_ILLAGER, enabled)),
                new GroupSwitch("arthropod", "group.arthropod", () -> value(GROUP_ARTHROPOD), enabled -> set(GROUP_ARTHROPOD, enabled)),
                new GroupSwitch("creeper", "group.creeper", () -> value(GROUP_CREEPER), enabled -> set(GROUP_CREEPER, enabled)),
                new GroupSwitch("nether", "group.nether", () -> value(GROUP_NETHER), enabled -> set(GROUP_NETHER, enabled)),
                new GroupSwitch("humanoid", "group.humanoid", () -> value(GROUP_HUMANOID), enabled -> set(GROUP_HUMANOID, enabled)),
                new GroupSwitch("non_humanoid", "group.non_humanoid", () -> value(GROUP_NON_HUMANOID),
                        enabled -> set(GROUP_NON_HUMANOID, enabled)));
    }

    /* 是否至少开启了一个分组, 全部关闭时中间层扫描得到的条目全部为关闭 */
    public static boolean anyGroupEnabled() {
        for (GroupSwitch group : groups()) {
            if (group.getter().getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static boolean value(ForgeConfigSpec.BooleanValue holder) {
        try {
            return holder.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void set(ForgeConfigSpec.BooleanValue holder, boolean enabled) {
        try {
            holder.set(enabled);
        } catch (IllegalStateException ignored) {
        }
    }

    public static void save() {
        var config = ConfigEvents.mobMiddleConfig();
        if (config != null) {
            config.save();
        }
    }
}
