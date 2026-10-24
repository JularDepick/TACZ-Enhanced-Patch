package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * 服务端配置。
 * 由服务端持有: 单人存档写入该存档的 serverconfig 目录, 专用服务端写入 config 目录,
 * 客户端只接收同步值, 不能自行修改。
 * 枪手系统全部开关集中在此, 关闭后行为与未安装本模组时一致。
 */
public final class ServerConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DEBUG_EVENTS = BUILDER
            .comment("是否把 TACZ API 事件写入日志, 用于补丁开发期的接入诊断")
            .define("debugEvents", false);

    private static final ForgeConfigSpec.BooleanValue GUNNER_ENABLED;
    private static final ForgeConfigSpec.DoubleValue GUNNER_SPAWN_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_SPAWNER_CHANCE;
    private static final ForgeConfigSpec.BooleanValue GUNNER_DIFFICULTY_SCALING;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUNNER_MOB_BLACKLIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUNNER_DIMENSION_BLACKLIST;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_LOW;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_MID;
    private static final ForgeConfigSpec.IntValue GUNNER_TIER_WEIGHT_HIGH;
    private static final ForgeConfigSpec.DoubleValue GUNNER_TARGET_RANGE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_FIRE_RANGE;
    private static final ForgeConfigSpec.IntValue GUNNER_REACTION_TICKS_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_REACTION_TICKS_MAX;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_MAX;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_PAUSE_MIN;
    private static final ForgeConfigSpec.IntValue GUNNER_BURST_PAUSE_MAX;
    private static final ForgeConfigSpec.DoubleValue GUNNER_ACCURACY;
    private static final ForgeConfigSpec.DoubleValue GUNNER_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GUNNER_DAMAGE_TAKEN_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GUNNER_HEALTH_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue GUNNER_ARMOR_BONUS;
    private static final ForgeConfigSpec.DoubleValue GUNNER_ARMOR_DROP_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_RETALIATION_CHANCE;
    private static final ForgeConfigSpec.IntValue GUNNER_BASE_AMMO;
    private static final ForgeConfigSpec.IntValue GUNNER_RANDOM_ATTACHMENT_SLOTS;
    private static final ForgeConfigSpec.BooleanValue GUNNER_FORCED_RETALIATION;
    private static final ForgeConfigSpec.DoubleValue GUNNER_HIGH_TIER_AMMO_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_AMMO_DROP_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GUNNER_GUN_DROP_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GUNNER_ATTACHMENT_DROP_CHANCE;
    private static final ForgeConfigSpec.BooleanValue RUINS_ENABLED;
    private static final ForgeConfigSpec.DoubleValue GRUNT_AMMO_DROP_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue GRUNT_HIGH_TIER_AMMO_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GRUNT_GUN_DROP_CHANCE;
    private static final ForgeConfigSpec.DoubleValue GRUNT_ATTACHMENT_DROP_CHANCE;
    private static final ForgeConfigSpec.IntValue GRUNT_FOOD_STACKS_MIN;
    private static final ForgeConfigSpec.IntValue GRUNT_FOOD_STACKS_MAX;
    private static final ForgeConfigSpec.IntValue GRUNT_FOOD_COUNT_MIN;
    private static final ForgeConfigSpec.IntValue GRUNT_FOOD_COUNT_MAX;
    private static final ForgeConfigSpec.DoubleValue GRUNT_RESOURCE_CHANCE;
    private static final ForgeConfigSpec.IntValue TANK_CANNON_COOLDOWN;
    private static final ForgeConfigSpec.IntValue TANK_EXPLOSION_POWER;
    private static final ForgeConfigSpec.DoubleValue TANK_CANNON_RANGE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("持枪敌对生物系统").push("gunner");
        GUNNER_ENABLED = BUILDER
                .comment("是否让原版敌对生物在生成时装备枪械, 关闭后所有枪手相关行为停止")
                .define("enabled", true);
        GUNNER_SPAWN_CHANCE = BUILDER
                .comment("单个敌对生物成为枪手的基础概率, 取值 0 到 1",
                        "同一数值也决定护甲每个部位的穿戴概率, 空部位概率为 1 减去该值")
                .defineInRange("spawnChance", 0.2D, 0.0D, 1.0D);
        GUNNER_SPAWNER_CHANCE = BUILDER
                .comment("刷怪笼生成敌对生物时成为枪手的最低概率, 用于保证遗迹守卫的武装率")
                .defineInRange("spawnerGunnerChance", 0.6D, 0.0D, 1.0D);
        GUNNER_DIFFICULTY_SCALING = BUILDER
                .comment("是否按游戏难度缩放枪手生成概率与分档权重")
                .define("difficultyScaling", true);
        GUNNER_MOB_BLACKLIST = BUILDER
                .comment("额外排除的生物实体 ID 列表",
                        "候选生物由游戏实际的实体类型自动判定, 敌方生物默认全部纳入, 这里只做减法")
                .defineListAllowEmpty("mobBlacklist", List.<String>of(), o -> o instanceof String);
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
        GUNNER_TARGET_RANGE = BUILDER
                .comment("枪手的锁定范围, 单位方块, 超过该距离不会索敌")
                .defineInRange("targetRange", 32.0D, 4.0D, 64.0D);
        GUNNER_FIRE_RANGE = BUILDER
                .comment("枪手的开火距离, 单位方块, 目标超出该距离时只跟踪不开火")
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
        GUNNER_ACCURACY = BUILDER
                .comment("枪手命中准度, 0 为最不准, 1 为最准")
                .defineInRange("accuracy", 0.75D, 0.0D, 1.0D);
        GUNNER_DAMAGE_MULTIPLIER = BUILDER
                .comment("枪手子弹对目标的伤害倍率, 用于压制 TACZ 原始伤害对玩家的压制力")
                .defineInRange("damageMultiplier", 0.6D, 0.1D, 3.0D);
        GUNNER_DAMAGE_TAKEN_MULTIPLIER = BUILDER
                .comment("玩家枪械打向枪手的伤害倍率, 用于让枪手经得起数次命中; 枪手误伤, 近战与爆炸伤害不受影响")
                .defineInRange("damageTakenMultiplier", 0.6D, 0.1D, 1.0D);
        GUNNER_HEALTH_MULTIPLIER = BUILDER
                .comment("枪手最大生命值倍率, 与分档耐久系数相乘")
                .defineInRange("healthMultiplier", 2.5D, 1.0D, 10.0D);
        GUNNER_ARMOR_BONUS = BUILDER
                .comment("枪手额外护甲点数, 叠加在穿戴的护甲之上")
                .defineInRange("armorBonus", 4, 0, 30);
        GUNNER_ARMOR_DROP_CHANCE = BUILDER
                .comment("枪手穿戴的护甲每个部位的掉落概率")
                .defineInRange("armorDropChance", 0.05D, 0.0D, 1.0D);
        GUNNER_RETALIATION_CHANCE = BUILDER
                .comment("和平生物, 中立生物与友军生物受击后生命降到一半时临场获得枪械的概率",
                        "每只生物只判定一次, 取 0 表示关闭临场武装")
                .defineInRange("retaliationChance", 0.35D, 0.0D, 1.0D);
        GUNNER_BASE_AMMO = BUILDER
                .comment("枪手的基本弹药量, 实际获得的备弹量在基本值的 0.5 到 2 倍之间随机")
                .defineInRange("baseAmmo", 60, 1, 1000);
        GUNNER_RANDOM_ATTACHMENT_SLOTS = BUILDER
                .comment("每把枪最多随机安装的配件数量, 每个槽位还有单独的概率判定")
                .defineInRange("randomAttachmentSlots", 2, 0, 4);
        GUNNER_FORCED_RETALIATION = BUILDER
                .comment("是否强制反击伤害来源",
                        "开启时无论伤害来源是谁都会立刻还击; 关闭时先判断敌对关系, 同类与盟友之间不还击")
                .define("forcedRetaliation", true);
        GUNNER_AMMO_DROP_MULTIPLIER = BUILDER
                .comment("枪手掉落弹药数量的倍率, 同时作用于剩余备弹与随机数量")
                .defineInRange("ammoDropMultiplier", 1.0D, 0.0D, 5.0D);
        GUNNER_HIGH_TIER_AMMO_CHANCE = BUILDER
                .comment("枪手死亡时额外掉落一份高级弹药的概率",
                        "高级弹药指 308, 338 与 50bmg 这类高阶口径")
                .defineInRange("highTierAmmoChance", 0.12D, 0.0D, 1.0D);
        GUNNER_GUN_DROP_CHANCE = BUILDER
                .comment("枪手死亡时掉落所用枪械的概率")
                .defineInRange("gunDropChance", 0.08D, 0.0D, 1.0D);
        GUNNER_ATTACHMENT_DROP_CHANCE = BUILDER
                .comment("枪手死亡时掉落随机 TACZ 配件的概率")
                .defineInRange("attachmentDropChance", 0.08D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.comment("持枪小兵").push("grunt");
        GRUNT_AMMO_DROP_MULTIPLIER = BUILDER
                .comment("击杀持枪小兵时弹药数量相对剩余备弹的倍率")
                .defineInRange("ammoDropMultiplier", 1.0D, 0.0D, 5.0D);
        GRUNT_HIGH_TIER_AMMO_CHANCE = BUILDER
                .comment("击杀持枪小兵时额外掉落一份高级弹药的概率")
                .defineInRange("highTierAmmoChance", 0.1D, 0.0D, 1.0D);
        GRUNT_GUN_DROP_CHANCE = BUILDER
                .comment("击杀持枪小兵时掉落其手中枪械的概率")
                .defineInRange("gunDropChance", 0.12D, 0.0D, 1.0D);
        GRUNT_ATTACHMENT_DROP_CHANCE = BUILDER
                .comment("击杀持枪小兵时掉落随机配件的概率")
                .defineInRange("attachmentDropChance", 0.1D, 0.0D, 1.0D);
        GRUNT_FOOD_STACKS_MIN = BUILDER
                .comment("击杀持枪小兵时掉落食物堆数的最少值")
                .defineInRange("foodStacksMin", 2, 0, 8);
        GRUNT_FOOD_STACKS_MAX = BUILDER
                .comment("击杀持枪小兵时掉落食物堆数的最多值")
                .defineInRange("foodStacksMax", 4, 0, 8);
        GRUNT_FOOD_COUNT_MIN = BUILDER
                .comment("每堆食物的最少数量")
                .defineInRange("foodCountMin", 1, 1, 16);
        GRUNT_FOOD_COUNT_MAX = BUILDER
                .comment("每堆食物的最多数量")
                .defineInRange("foodCountMax", 4, 1, 16);
        GRUNT_RESOURCE_CHANCE = BUILDER
                .comment("击杀持枪小兵时掉落一份随机杂物的概率")
                .defineInRange("resourceChance", 0.6D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.comment("坦克精英怪").push("tank");
        TANK_CANNON_COOLDOWN = BUILDER
                .comment("坦克精英怪两次开炮之间的间隔, 单位 tick, 数值越大节奏越慢")
                .defineInRange("cannonCooldown", 200, 20, 1200);
        TANK_EXPLOSION_POWER = BUILDER
                .comment("炮弹爆炸威力, 数值越大破坏范围与伤害越高, 恶魂火球为 1")
                .defineInRange("explosionPower", 4, 1, 8);
        TANK_CANNON_RANGE = BUILDER
                .comment("坦克精英怪的开炮距离, 单位方块, 目标超出该距离只追击不开炮")
                .defineInRange("cannonRange", 48.0D, 8.0D, 96.0D);
        BUILDER.pop();

        BUILDER.comment("世界生成").push("worldgen");
        RUINS_ENABLED = BUILDER
                .comment("是否生成废弃前哨遗迹, 关闭后不再新增遗迹但已生成的保留")
                .define("ruinsEnabled", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ServerConfig() {
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

    public static double gruntAmmoDropMultiplier() {
        return value(GRUNT_AMMO_DROP_MULTIPLIER, 1.0D);
    }

    public static double gruntHighTierAmmoChance() {
        return value(GRUNT_HIGH_TIER_AMMO_CHANCE, 0.1D);
    }

    public static double gruntGunDropChance() {
        return value(GRUNT_GUN_DROP_CHANCE, 0.12D);
    }

    public static double gruntAttachmentDropChance() {
        return value(GRUNT_ATTACHMENT_DROP_CHANCE, 0.1D);
    }

    public static int[] gruntFoodStacks() {
        return range(GRUNT_FOOD_STACKS_MIN, GRUNT_FOOD_STACKS_MAX, 2, 4);
    }

    public static int[] gruntFoodCount() {
        return range(GRUNT_FOOD_COUNT_MIN, GRUNT_FOOD_COUNT_MAX, 1, 4);
    }

    public static double gruntResourceChance() {
        return value(GRUNT_RESOURCE_CHANCE, 0.6D);
    }

    public static int tankCannonCooldown() {
        return value(TANK_CANNON_COOLDOWN, 200);
    }

    public static int tankExplosionPower() {
        return value(TANK_EXPLOSION_POWER, 4);
    }

    public static double tankCannonRange() {
        return value(TANK_CANNON_RANGE, 48.0D);
    }

    public static boolean ruinsEnabled() {
        return value(RUINS_ENABLED, true);
    }

    public static boolean gunnerDifficultyScaling() {
        return value(GUNNER_DIFFICULTY_SCALING, true);
    }

    public static List<String> gunnerMobBlacklist() {
        return stringList(GUNNER_MOB_BLACKLIST, List.of());
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

    public static double gunnerTargetRange() {
        return value(GUNNER_TARGET_RANGE, 32.0D);
    }

    public static double gunnerFireRange() {
        return value(GUNNER_FIRE_RANGE, 28.0D);
    }

    public static double gunnerAccuracy() {
        return value(GUNNER_ACCURACY, 0.75D);
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

    public static double gunnerDamageMultiplier() {
        return value(GUNNER_DAMAGE_MULTIPLIER, 0.6D);
    }

    public static double gunnerDamageTakenMultiplier() {
        return value(GUNNER_DAMAGE_TAKEN_MULTIPLIER, 0.6D);
    }

    public static double gunnerHealthMultiplier() {
        return value(GUNNER_HEALTH_MULTIPLIER, 2.5D);
    }

    public static int gunnerArmorBonus() {
        return value(GUNNER_ARMOR_BONUS, 4);
    }

    public static double gunnerArmorDropChance() {
        return value(GUNNER_ARMOR_DROP_CHANCE, 0.05D);
    }

    public static boolean gunnerForcedRetaliation() {
        return value(GUNNER_FORCED_RETALIATION, true);
    }

    /* 持械生物分组开关, 供配置界面单独渲染 */
    public static double gunnerRetaliationChance() {
        return value(GUNNER_RETALIATION_CHANCE, 0.35D);
    }

    public static int gunnerBaseAmmo() {
        return value(GUNNER_BASE_AMMO, 60);
    }

    public static int gunnerRandomAttachmentSlots() {
        return value(GUNNER_RANDOM_ATTACHMENT_SLOTS, 2);
    }

    public static double gunnerHighTierAmmoChance() {
        return value(GUNNER_HIGH_TIER_AMMO_CHANCE, 0.12D);
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

    /* 分组标题: 界面与调试, 该组固定排在最前 */
    public static final String GROUP_UI = "group.ui";

    /*
     * 分组顺序即界面顺序。
     * 纯按钮与开关类条目优先: 界面与调试组提供生物持械配置入口与调试开关, 遗迹组是最常用的开关,
     * 两者排在所有数值组之前, 打开页面即可见, 不需要滚动查找。
     */
    public static List<ConfigOptionGroup> groups() {
        return List.of(
                new ConfigOptionGroup(GROUP_UI, List.of(
                        new ConfigOption.ToggleOption("opt.debug", DEBUG_EVENTS))),
                new ConfigOptionGroup("group.ruins", List.of(
                        new ConfigOption.ToggleOption("opt.ruins", RUINS_ENABLED))),
                new ConfigOptionGroup("group.basic", List.of(
                        new ConfigOption.ToggleOption("opt.enabled", GUNNER_ENABLED),
                        new ConfigOption.ToggleOption("opt.difficulty", GUNNER_DIFFICULTY_SCALING))),
                new ConfigOptionGroup("group.retaliation", List.of(
                        new ConfigOption.ToggleOption("opt.forced_retaliation", GUNNER_FORCED_RETALIATION))),
                new ConfigOptionGroup("group.spawn", List.of(
                        new ConfigOption.DoubleOption("opt.spawn_chance", GUNNER_SPAWN_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.spawner_chance", GUNNER_SPAWNER_CHANCE, 0.0D, 1.0D, 0.01D))),
                new ConfigOptionGroup("group.targeting", List.of(
                        new ConfigOption.DoubleOption("opt.target_range", GUNNER_TARGET_RANGE, 4.0D, 64.0D, 1.0D),
                        new ConfigOption.DoubleOption("opt.fire_range", GUNNER_FIRE_RANGE, 4.0D, 64.0D, 1.0D),
                        new ConfigOption.DoubleOption("opt.accuracy", GUNNER_ACCURACY, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.IntOption("opt.reaction_min", GUNNER_REACTION_TICKS_MIN, 0, 400),
                        new ConfigOption.IntOption("opt.reaction_max", GUNNER_REACTION_TICKS_MAX, 0, 400),
                        new ConfigOption.IntOption("opt.burst_min", GUNNER_BURST_MIN, 1, 100),
                        new ConfigOption.IntOption("opt.burst_max", GUNNER_BURST_MAX, 1, 100),
                        new ConfigOption.IntOption("opt.burst_pause_min", GUNNER_BURST_PAUSE_MIN, 0, 200),
                        new ConfigOption.IntOption("opt.burst_pause_max", GUNNER_BURST_PAUSE_MAX, 0, 200))),
                new ConfigOptionGroup("group.ammo", List.of(
                        new ConfigOption.IntOption("opt.base_ammo", GUNNER_BASE_AMMO, 1, 1000),
                        new ConfigOption.IntOption("opt.attachment_slots", GUNNER_RANDOM_ATTACHMENT_SLOTS, 0, 4))),
                new ConfigOptionGroup("group.durability", List.of(
                        new ConfigOption.DoubleOption("opt.damage", GUNNER_DAMAGE_MULTIPLIER, 0.1D, 3.0D, 0.05D),
                        new ConfigOption.DoubleOption("opt.damage_taken", GUNNER_DAMAGE_TAKEN_MULTIPLIER, 0.1D, 1.0D, 0.05D),
                        new ConfigOption.DoubleOption("opt.health", GUNNER_HEALTH_MULTIPLIER, 1.0D, 10.0D, 0.1D),
                        new ConfigOption.IntOption("opt.armor_bonus", GUNNER_ARMOR_BONUS, 0, 30),
                        new ConfigOption.DoubleOption("opt.armor_drop", GUNNER_ARMOR_DROP_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.retaliation_chance", GUNNER_RETALIATION_CHANCE, 0.0D, 1.0D, 0.01D))),
                new ConfigOptionGroup("group.loot", List.of(
                        new ConfigOption.DoubleOption("opt.ammo_drop_mult", GUNNER_AMMO_DROP_MULTIPLIER, 0.0D, 5.0D, 0.1D),
                        new ConfigOption.DoubleOption("opt.high_tier_chance", GUNNER_HIGH_TIER_AMMO_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.gun_drop", GUNNER_GUN_DROP_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.attachment_drop", GUNNER_ATTACHMENT_DROP_CHANCE, 0.0D, 1.0D, 0.01D))),
                new ConfigOptionGroup("group.grunt", List.of(
                        new ConfigOption.DoubleOption("opt.grunt_ammo_mult", GRUNT_AMMO_DROP_MULTIPLIER, 0.0D, 5.0D, 0.1D),
                        new ConfigOption.DoubleOption("opt.grunt_high_tier", GRUNT_HIGH_TIER_AMMO_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.grunt_gun_drop", GRUNT_GUN_DROP_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.DoubleOption("opt.grunt_attachment_drop", GRUNT_ATTACHMENT_DROP_CHANCE, 0.0D, 1.0D, 0.01D),
                        new ConfigOption.IntOption("opt.grunt_food_stacks_min", GRUNT_FOOD_STACKS_MIN, 0, 8),
                        new ConfigOption.IntOption("opt.grunt_food_stacks_max", GRUNT_FOOD_STACKS_MAX, 0, 8),
                        new ConfigOption.IntOption("opt.grunt_food_count_min", GRUNT_FOOD_COUNT_MIN, 1, 16),
                        new ConfigOption.IntOption("opt.grunt_food_count_max", GRUNT_FOOD_COUNT_MAX, 1, 16),
                        new ConfigOption.DoubleOption("opt.grunt_resource_chance", GRUNT_RESOURCE_CHANCE, 0.0D, 1.0D, 0.01D))),
                new ConfigOptionGroup("group.tank", List.of(
                        new ConfigOption.IntOption("opt.tank_cooldown", TANK_CANNON_COOLDOWN, 20, 1200),
                        new ConfigOption.IntOption("opt.tank_explosion", TANK_EXPLOSION_POWER, 1, 8),
                        new ConfigOption.DoubleOption("opt.tank_range", TANK_CANNON_RANGE, 8.0D, 96.0D, 1.0D)))
        );
    }

    public static void save() {
        ModConfig config = ConfigEvents.serverConfig();
        if (config != null) {
            config.save();
        }
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
