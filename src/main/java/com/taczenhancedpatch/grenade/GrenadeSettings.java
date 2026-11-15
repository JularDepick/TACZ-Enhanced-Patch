package com.taczenhancedpatch.grenade;

import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/*
 * 手雷配置快照。
 * 六项属性都属于玩家偏好: 客户端按键切换后写进客户端配置, 投掷时随包下发并绑定到这一颗手雷上。
 * 引信 -1 表示触发式, 碰撞到方块或生物立即爆炸; 1 到 10 表示按下右键后多少秒爆炸。
 */
public record GrenadeSettings(int fuseSeconds, boolean sticky, int power, GrenadeTarget target,
                              DyeColor color, boolean effects) {
    /* 蓄力上限三秒, 单位 tick */
    public static final int CHARGE_TICKS = 60;
    /* 初速度区间由服务端配置给出, 这里是配置不可用时的兜底与合法边界 */
    public static final double SPEED_MIN_DEFAULT = 4.0D;
    public static final double SPEED_MAX_DEFAULT = 16.0D;
    public static final double SPEED_MIN_LIMIT = 4.0D;
    public static final double SPEED_MAX_LIMIT = 64.0D;
    public static final int FUSE_IMPACT = -1;
    public static final int FUSE_MIN = 1;
    public static final int FUSE_MAX = 10;
    public static final int POWER_MIN = 0;
    public static final int POWER_MAX = 16;
    /* 威力到杀伤半径: 零到十六格, 一格威力一格半径 */
    public static final double RADIUS_MAX = 16.0D;
    /* 威力到中心伤害: 零到基础生命的三点二倍, 即六十四点 */
    public static final float DAMAGE_MAX = 64.0F;
    /* 反弹后法向速度低于该值即视为静止, 单位 格/秒 */
    public static final double REST_SPEED = 0.1D;
    /* 切向速度每次反弹固定损失的比例 */
    public static final double TANGENT_LOSS = 0.1D;

    /* 默认值: 三秒引信, 不粘, 威力四, 破坏地形且杀伤玩家, 墨绿, 开特效 */
    public static final GrenadeSettings DEFAULT = new GrenadeSettings(3, false, 4,
            GrenadeTarget.NO_MOB, DyeColor.GREEN, true);

    /* 初速度只由蓄力时长在服务端配置的区间内线性决定 */
    public static double speedFor(int chargeTicks, double min, double max) {
        double ratio = Mth.clamp(chargeTicks, 0, CHARGE_TICKS) / (double) CHARGE_TICKS;
        return min + (max - min) * ratio;
    }

    /* 威力到杀伤半径: 一格威力一格半径 */
    public double radius() {
        return RADIUS_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (double) POWER_MAX;
    }

    /* 威力到中心伤害: 满档为基础生命的百分之三百二十 */
    public float damage() {
        return DAMAGE_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (float) POWER_MAX;
    }

    public boolean impactFuse() {
        return fuseSeconds == FUSE_IMPACT;
    }

    /*
     * 引信档位: 一到十秒之后接触发式。
     * 成对按键到边界即停, 不溢出也不循环。
     */
    public static int stepFuse(int current, boolean forward) {
        int[] table = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, FUSE_IMPACT};
        int index = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] == current) {
                index = i;
                break;
            }
        }
        return table[Mth.clamp(index + (forward ? 1 : -1), 0, table.length - 1)];
    }

    public static int stepPower(int current, boolean forward) {
        return Mth.clamp(current + (forward ? 1 : -1), POWER_MIN, POWER_MAX);
    }

    /* 颜色到首尾即停, 不循环 */
    public static DyeColor stepColor(DyeColor current, boolean forward) {
        DyeColor[] colors = DyeColor.values();
        int index = current == null ? 0 : current.ordinal();
        return colors[Mth.clamp(index + (forward ? 1 : -1), 0, colors.length - 1)];
    }

    /* 把任意来源的数值夹到合法区间, 客户端配置被手工改坏时也不至于越界 */
    public static GrenadeSettings sanitize(GrenadeSettings settings) {
        int fuse = settings.fuseSeconds();
        if (fuse != FUSE_IMPACT) {
            fuse = Mth.clamp(fuse, FUSE_MIN, FUSE_MAX);
        }
        int power = Mth.clamp(settings.power(), POWER_MIN, POWER_MAX);
        DyeColor color = settings.color() == null ? DyeColor.GREEN : settings.color();
        GrenadeTarget target = settings.target() == null ? GrenadeTarget.NO_MOB : settings.target();
        return new GrenadeSettings(fuse, settings.sticky(), power, target, color, settings.effects());
    }
}
