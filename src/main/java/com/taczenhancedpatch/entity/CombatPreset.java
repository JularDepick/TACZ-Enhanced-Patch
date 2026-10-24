package com.taczenhancedpatch.entity;

import net.minecraft.world.Difficulty;

/**
 * 本模组生物的属性预设。
 * 小兵与坦克的属性不看模组配置, 只随游戏难度切换;
 * 坦克以坚守者为基准取八成, 小兵取坦克的四分之一。
 */
public final class CombatPreset {
    /* 坚守者的生命与近战伤害, 作为坦克数值的基准 */
    private static final double WARDEN_HEALTH = 500.0D;
    private static final double WARDEN_DAMAGE = 30.0D;
    private static final double TANK_RATIO = 0.8D;
    private static final double GRUNT_RATIO = 0.25D;

    private CombatPreset() {
    }

    /* 难度系数: 简单偏低, 普通为基准, 困难偏高 */
    private static double scale(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> 0.8D;
            case NORMAL -> 1.0D;
            case HARD -> 1.2D;
            default -> 1.0D;
        };
    }

    public static double tankHealth(Difficulty difficulty) {
        return WARDEN_HEALTH * TANK_RATIO * scale(difficulty);
    }

    /* 坦克单发伤害, 与坚守者的近战伤害同比例 */
    public static double tankDamage(Difficulty difficulty) {
        return WARDEN_DAMAGE * TANK_RATIO * scale(difficulty);
    }

    public static double tankArmor(Difficulty difficulty) {
        return 12.0D * scale(difficulty);
    }

    /* 炮弹爆炸威力, 按伤害换算成整数威力 */
    public static int tankExplosionPower(Difficulty difficulty) {
        return (int) Math.round(Math.max(2.0D, tankDamage(difficulty) / 4.0D));
    }

    public static double gruntHealth(Difficulty difficulty) {
        return tankHealth(difficulty) * GRUNT_RATIO;
    }

    public static double gruntArmor(Difficulty difficulty) {
        return tankArmor(difficulty) * GRUNT_RATIO;
    }
}
