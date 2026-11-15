package com.taczenhancedpatch.shell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/*
 * 炮弹属性快照。
 * 手雷与迫击炮炮弹共用这一套字段, 属性随物品数据走, 同一物品 ID 可以有不同属性。
 * 引信 -1 表示触发式, 碰撞到方块或生物立即爆炸; 1 到 10 表示按下或发射后多少秒爆炸。
 */
public record ShellSettings(int fuseSeconds, boolean sticky, int power, ShellTarget target,
                            ShellColor color, boolean effects) {
    public static final int CHARGE_TICKS = 20;
    public static final double SPEED_MIN_DEFAULT = 4.0D;
    public static final double SPEED_MAX_DEFAULT = 16.0D;
    public static final double SPEED_MIN_LIMIT = 4.0D;
    public static final double SPEED_MAX_LIMIT = 64.0D;
    public static final int FUSE_IMPACT = -1;
    public static final int FUSE_MIN = 3;
    public static final int FUSE_MAX = 10;
    public static final int POWER_MIN = 0;
    public static final int POWER_MAX = 16;
    public static final double RADIUS_MAX = 16.0D;
    public static final float DAMAGE_MAX = 64.0F;
    /* 单颗手雷的质量, 单位千克, 只用于提示与手感换算 */
    public static final double MASS_KG = 0.3D;
    public static final double REST_SPEED = 0.1D;
    public static final double TANGENT_LOSS = 0.1D;

    /* 新物品的出厂属性: 三秒引信, 不粘, 威力四, 不杀伤任何目标, 墨绿, 开特效 */
    public static final ShellSettings DEFAULT = new ShellSettings(3, false, 4, ShellTarget.NO_DAMAGE, ShellColor.GREEN, true);

    public static double speedFor(int chargeTicks, double min, double max) {
        double ratio = Mth.clamp(chargeTicks, 0, CHARGE_TICKS) / (double) CHARGE_TICKS;
        return min + (max - min) * ratio;
    }

    public double radius() {
        return RADIUS_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (double) POWER_MAX;
    }

    public float damage() {
        return DAMAGE_MAX * Mth.clamp(power, POWER_MIN, POWER_MAX) / (float) POWER_MAX;
    }

    public boolean impactFuse() {
        return fuseSeconds == FUSE_IMPACT;
    }

    /* 成对按键到边界即停, 不溢出也不循环 */
    public static int stepFuse(int current, boolean forward) {
        int[] table = {3, 4, 5, 6, 7, 8, 9, 10, FUSE_IMPACT};
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

    public static ShellColor stepColor(ShellColor current, boolean forward) {
        ShellColor[] colors = ShellColor.values();
        int index = current == null ? 0 : current.ordinal();
        int next = (index + (forward ? 1 : colors.length - 1)) % colors.length;
        return colors[next];
    }

    public static ShellSettings sanitize(ShellSettings settings) {
        int fuse = settings.fuseSeconds();
        if (fuse != FUSE_IMPACT) {
            fuse = Mth.clamp(fuse, FUSE_MIN, FUSE_MAX);
        }
        int power = Mth.clamp(settings.power(), POWER_MIN, POWER_MAX);
        ShellColor color = settings.color() == null ? ShellColor.GREEN : settings.color();
        ShellTarget target = settings.target() == null ? ShellTarget.NO_DAMAGE : settings.target();
        return new ShellSettings(fuse, settings.sticky(), power, target, color, settings.effects());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Fuse", fuseSeconds);
        tag.putBoolean("Sticky", sticky);
        tag.putInt("Power", power);
        tag.putString("Target", target.id());
        tag.putString("Color", color.id());
        tag.putBoolean("Effects", effects);
        return tag;
    }

    public static ShellSettings fromTag(CompoundTag tag) {
        return sanitize(new ShellSettings(
                tag.getInt("Fuse"),
                tag.getBoolean("Sticky"),
                tag.getInt("Power"),
                ShellTarget.byId(tag.getString("Target"), ShellTarget.NO_DAMAGE),
                ShellColor.byId(tag.getString("Color"), ShellColor.GREEN),
                !tag.contains("Effects") || tag.getBoolean("Effects")));
    }
}
