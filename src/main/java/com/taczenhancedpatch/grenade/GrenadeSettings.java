package com.taczenhancedpatch.grenade;

import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/*
 * 手雷配置快照。
 * 六项属性都属于玩家偏好: 客户端按键切换后写进客户端配置, 投掷时随包下发, 服务端只按快照模拟。
 * 引信 -1 表示触发式, 碰撞到方块或生物立即爆炸; 1 到 10 表示按下右键后多少秒爆炸。
 */
public record GrenadeSettings(int fuseSeconds, double bounce, int power, GrenadeTarget target,
                              DyeColor color, boolean effects) {
    /* 蓄力上限三秒, 单位 tick */
    public static final int CHARGE_TICKS = 60;
    /* 初速度区间, 单位 格/秒 */
    public static final double SPEED_MIN = 2.0D;
    public static final double SPEED_MAX = 16.0D;
    public static final int FUSE_IMPACT = -1;
    public static final int FUSE_MIN = 1;
    public static final int FUSE_MAX = 10;
    public static final double BOUNCE_MIN = 0.0D;
    public static final double BOUNCE_MAX = 0.9D;
    public static final double BOUNCE_STEP = 0.1D;
    public static final int POWER_MIN = 0;
    public static final int POWER_MAX = 16;
    /* 威力满档对应的方块破坏半径与中心伤害 */
    public static final double RADIUS_MAX = 8.0D;
    public static final float DAMAGE_MAX = 32.0F;
    /* 反弹后法向速度低于该值即视为静止, 单位 格/秒 */
    public static final double REST_SPEED = 0.1D;
    /* 切向速度每次反弹固定损失的比例 */
    public static final double TANGENT_LOSS = 0.1D;

    /* 默认值: 三秒引信, 不反弹, 威力四, 破坏地形且杀伤玩家, 墨绿, 开特效 */
    public static final GrenadeSettings DEFAULT = new GrenadeSettings(3, BOUNCE_MIN, 4,
            GrenadeTarget.NO_MOB, DyeColor.GREEN, true);

    /* 初速度只由蓄力时长线性决定 */
    public static double speedFor(int chargeTicks) {
        double ratio = Mth.clamp(chargeTicks, 0, CHARGE_TICKS) / (double) CHARGE_TICKS;
        return SPEED_MIN + (SPEED_MAX - SPEED_MIN) * ratio;
    }

    /* 威力到破坏半径: 零到八格 */
    public double blockRadius() {
        return RADIUS_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (double) POWER_MAX;
    }

    /* 威力到中心伤害: 零到三十二点, 即基础生命的两倍 */
    public float damage() {
        return DAMAGE_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (float) POWER_MAX;
    }

    public boolean impactFuse() {
        return fuseSeconds == FUSE_IMPACT;
    }

    /* 引信档位循环: 1 到 10 秒之后接触发式 */
    public static int nextFuse(int current, boolean forward) {
        int[] table = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, FUSE_IMPACT};
        int index = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] == current) {
                index = i;
                break;
            }
        }
        int next = (index + (forward ? 1 : table.length - 1)) % table.length;
        return table[next];
    }

    /* 反弹系数按零点一档位步进 */
    public static double stepBounce(double current, boolean forward) {
        double next = current + (forward ? BOUNCE_STEP : -BOUNCE_STEP);
        return Math.round(Mth.clamp(next, BOUNCE_MIN, BOUNCE_MAX) * 10.0D) / 10.0D;
    }

    public static int stepPower(int current, boolean forward) {
        return Mth.clamp(current + (forward ? 1 : -1), POWER_MIN, POWER_MAX);
    }

    /* 把任意来源的数值夹到合法区间, 客户端配置被手工改坏时也不至于越界 */
    public static GrenadeSettings sanitize(GrenadeSettings settings) {
        int fuse = settings.fuseSeconds();
        if (fuse != FUSE_IMPACT) {
            fuse = Mth.clamp(fuse, FUSE_MIN, FUSE_MAX);
        }
        double bounce = Math.round(Mth.clamp(settings.bounce(), BOUNCE_MIN, BOUNCE_MAX) * 10.0D) / 10.0D;
        int power = Mth.clamp(settings.power(), POWER_MIN, POWER_MAX);
        DyeColor color = settings.color() == null ? DyeColor.GREEN : settings.color();
        GrenadeTarget target = settings.target() == null ? GrenadeTarget.NO_MOB : settings.target();
        return new GrenadeSettings(fuse, bounce, power, target, color, settings.effects());
    }
}
