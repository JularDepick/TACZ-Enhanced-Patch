package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 通用配置。
 * 配置文件由 Forge 生成于 config/tacz_enhanced_patch-common.toml。
 * 枪手系统全部开关集中在此, 关闭后行为与未安装本模组时一致。
 */
public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DEBUG_EVENTS = BUILDER
            .comment("是否把 TACZ API 事件写入日志, 用于补丁开发期的接入诊断")
            .define("debugEvents", false);

    private static final List<String> DEFAULT_MOB_WHITELIST = List.of(
            "minecraft:zombie",
            "minecraft:husk",
            "minecraft:drowned",
            "minecraft:skeleton",
            "minecraft:stray",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:zombified_piglin",
            "minecraft:piglin_brute"
    );

    private static final ForgeConfigSpec.BooleanValue GUNNER_ENABLED;
    private static final ForgeConfigSpec.DoubleValue GUNNER_SPAWN_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_SPAWNER_CHANCE;
    private static final ForgeConfigSpec.BooleanValue GUNNER_DIFFICULTY_SCALING;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUNNER_MOB_WHITELIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUNNER_DIMENSION_BLACKLIST;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_LOW;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_MID;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_HIGH;
    private static final ForgeConfigSpec.DoubleValue GUNNER_FIRE_RANGE;
    private static final ForgeConfigSpec.IntValue GUNNER_REACTION_TICKS_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_REACTION_TICKS_MAX;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_MAX;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_PAUSE_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_PAUSE_MAX;
    private static final ForgeConfigSpec.DoubleValue GUNNER_AIM_ERROR_DEGREES;
    private static final ForgeConfigSpec.DoubleValue GUNNER_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GUNNER_AMMO_DROP_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_AMMO_DROP_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GUNNER_GUN_DROP_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_ATTACHMENT_DROP_CHANCE;
    private static final ForgeConfigSpec.BooleanValue RUINS_ENABLED;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("持枪敌对生物系统").push("gunner");
        GUNNER_ENABLED = BUILDER
                .comment("是否让原版敌对生物在生成时装备枪械, 关闭后所有枪手相关行为停止")
                .define("enabled", true);
        GUNNER_SPAWN_CHANCE = BUILDER
                .comment("单个敌对生物成为枪手的基础概率, 取值 0 到 1")
                .defineInRange("spawnChance", 0.2D, 0.0D, 1.0D);
        GUNNER_SPAWNER_CHANCE = BUILDER
                .comment("刷怪笼生成敌对生物时成为枪手的最低概率, 用于保证遗迹守卫的武装率")
                .defineInRange("spawnerGunnerChance", 0.6D, 0.0D, 1.0D);
        GUNNER_DIFFICULTY_SCALING = BUILDER
                .comment("是否按游戏难度缩放枪手生成概率")
                .define("difficultyScaling", true);
        GUNNER_MOB_WHITELIST = BUILDER
                .comment("允许成为枪手的生物实体 ID 列表")
                .defineList("mobWhitelist", DEFAULT_MOB_WHITELIST, o -> o instanceof String);
        GUNNER_DIMENSION_BLACKLIST = BUILDER
                .comment("禁止出现枪手的维度 ID 列表")
                .defineListAllowEmpty("dimensionBlacklist", List.<String>of(), o -> o instanceof String);
        GUNNER_TIER_WEIGHT_LOW = BUILDER
                .comment("低阶枪械的抽取权重")
                .defineInRange("tierWeightLow", 60, 0, 10000);
        GUNNER_TIER_WEIGHT_MID = BUILDER
                .comment("中阶枪械的抽取权重")
                .defineInRange("tierWeightMid", 30, 0, 10000);
        GUNNER_TIER_WEIGHT_HIGH = BUILDER
                .comment("高阶枪械的抽取权重")
                .defineInRange("tierWeightHigh", 10, 0, 10000);
        GUNNER_FIRE_RANGE = BUILDER
                .comment("枪手的开火距离, 单位方块")
                .defineInRange("fireRange", 28.0D, 4.0D, 64.0D);
        GUNNER_REACTION_TICKS_MIN = BUILDER
                .comment("枪手发现目标后的最短反应时间, 单位 tick")
                .defineInRange("reactionTicksMin", 15, 0, 400);
        GUNNER_REACTION_TICKS_MAX = BUILDER
                .comment("枪手发现目标后的最长反应时间, 单位 tick")
                .defineInRange("reactionTicksMax", 45, 0, 400);
        GUNNER_BURST_MIN = BUILDER
                .comment("单次点射的最少发数")
                .defineInRange("burstMin", 3, 1, 100);
        GUNNER_BURST_MAX = BUILDER
                .comment("单次点射的最多发数")
                .defineInRange("burstMax", 6, 1, 100);
        GUNNER_BURST_PAUSE_MIN = BUILDER
                .comment("两次点射之间的最短停火时间, 单位 tick")
                .defineInRange("burstPauseMin", 8, 0, 200);
        GUNNER_BURST_PAUSE_MAX = BUILDER
                .comment("两次点射之间的最长停火时间, 单位 tick")
                .defineInRange("burstPauseMax", 24, 0, 200);
        GUNNER_AIM_ERROR_DEGREES = BUILDER
                .comment("枪手瞄准误差的标准差, 单位角度")
                .defineInRange("aimErrorDegrees", 4.0D, 0.0D, 20.0D);
        GUNNER_DAMAGE_MULTIPLIER = BUILDER
                .comment("枪手子弹对目标的伤害倍率, 用于压制 TACZ 原始伤害对玩家的压制力")
                .defineInRange("damageMultiplier", 0.6D, 0.1D, 3.0D);
        GUNNER_AMMO_DROP_CHANCE = BUILDER
                .comment("枪手死亡时掉落弹药的概率")
                .defineInRange("ammoDropChance", 1.0D, 0.0D, 1.0D);
        GUNNER_AMMO_DROP_MULTIPLIER = BUILDER
                .comment("枪手弹药掉落数量的倍率")
                .defineInRange("ammoDropMultiplier", 1.0D, 0.0D, 5.0D);
        GUNNER_GUN_DROP_CHANCE = BUILDER
                .comment("枪手死亡时掉落所用枪械的概率")
                .defineInRange("gunDropChance", 0.08D, 0.0D, 1.0D);
        GUNNER_ATTACHMENT_DROP_CHANCE = BUILDER
                .comment("枪手死亡时掉落随机 TACZ 配件的概率")
                .defineInRange("attachmentDropChance", 0.08D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.comment("世界生成").push("worldgen");
        RUINS_ENABLED = BUILDER
                .comment("是否生成废弃前哨遗迹, 关闭后不再新增遗迹但已生成的保留")
                .define("ruinsEnabled", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CommonConfig() {
    }

    public static boolean debugEvents() {
        return value(DEBUG_EVENTS, false);
    }

    public static boolean gunnerEnabled() {
        return value(GUNNER_ENABLED, true);
    }

    public static double gunnerSpawnChance() {
        return value(GUNNER_SPAWN_CHANCE, 0.2D);
    }

    public static double gunnerSpawnerChance() {
        return value(GUNNER_SPAWNER_CHANCE, 0.6D);
    }

    public static boolean ruinsEnabled() {
        return value(RUINS_ENABLED, true);
    }

    public static boolean gunnerDifficultyScaling() {
        return value(GUNNER_DIFFICULTY_SCALING, true);
    }

    public static List<String> gunnerMobWhitelist() {
        return stringList(GUNNER_MOB_WHITELIST, DEFAULT_MOB_WHITELIST);
    }

    public static List<String> gunnerDimensionBlacklist() {
        return stringList(GUNNER_DIMENSION_BLACKLIST, List.of());
    }

    public static int[] gunnerTierWeights() {
        return new int[]{
                value(GUNNER_TIER_WEIGHT_LOW, 60),
                value(GUNNER_TIER_WEIGHT_MID, 30),
                value(GUNNER_TIER_WEIGHT_HIGH, 10)
        };
    }

    public static double gunnerFireRange() {
        return value(GUNNER_FIRE_RANGE, 28.0D);
    }

    public static int[] gunnerReactionTicks() {
        return range(GUNNER_REACTION_TICKS_MIN, GUNNER_REACTION_TICKS_MAX, 15, 45);
    }

    public static int[] gunnerBurstSize() {
        return range(GUNNER_BURST_MIN, GUNNER_BURST_MAX, 3, 6);
    }

    public static int[] gunnerBurstPause() {
        return range(GUNNER_BURST_PAUSE_MIN, GUNNER_BURST_PAUSE_MAX, 8, 24);
    }

    public static double gunnerAimErrorDegrees() {
        return value(GUNNER_AIM_ERROR_DEGREES, 4.0D);
    }

    public static double gunnerDamageMultiplier() {
        return value(GUNNER_DAMAGE_MULTIPLIER, 0.6D);
    }

    public static double gunnerAmmoDropChance() {
        return value(GUNNER_AMMO_DROP_CHANCE, 1.0D);
    }

    public static double gunnerAmmoDropMultiplier() {
        return value(GUNNER_AMMO_DROP_MULTIPLIER, 1.0D);
    }

    public static double gunnerGunDropChance() {
        return value(GUNNER_GUN_DROP_CHANCE, 0.08D);
    }

    public static double gunnerAttachmentDropChance() {
        return value(GUNNER_ATTACHMENT_DROP_CHANCE, 0.08D);
    }

    private static List<String> stringList(ForgeConfigSpec.ConfigValue<List<? extends String>> configValue, List<String> fallback) {
        try {
            return configValue.get().stream().map(String::valueOf).toList();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    private static int[] range(ForgeConfigSpec.IntValue minValue, ForgeConfigSpec.IntValue maxValue, int minFallback, int maxFallback) {
        int min = value(minValue, minFallback);
        int max = value(maxValue, maxFallback);
        return new int[]{Math.min(min, max), Math.max(min, max)};
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> configValue, T fallback) {
        try {
            return configValue.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }
}
