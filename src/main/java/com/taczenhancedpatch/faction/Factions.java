package com.taczenhancedpatch.faction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 阵营规则。
 * 阵营 ID 形如 ruin:<x>:<z>, 来自生成它的遗迹; 空字符串表示无阵营。
 * 同一阵营天然合作; 不同阵营按两者的稳定散列随机决定合作或敌对;
 * 无阵营生物不参与阵营战争, 也不会被阵营生物主动攻击。
 */
public final class Factions {
    public static final String NONE = "";

    private Factions() {
    }

    public static String normalize(@Nullable String id) {
        return id == null ? NONE : id;
    }

    public static boolean same(String a, String b) {
        return !normalize(a).isEmpty() && normalize(a).equals(normalize(b));
    }

    /* 两个有阵营的生物是否敌对: 同阵营永不敌对, 不同阵营按配对散列决定 */
    public static boolean hostile(String a, String b) {
        if (normalize(a).isEmpty() || normalize(b).isEmpty()) {
            return false;
        }
        if (same(a, b)) {
            return false;
        }
        String first = normalize(a);
        String second = normalize(b);
        int hash = first.compareTo(second) <= 0
                ? 31 * first.hashCode() + second.hashCode()
                : 31 * second.hashCode() + first.hashCode();
        return (hash & 1) == 0;
    }

    /* 目标是否是自己的敌人: 玩家单独判定, 无阵营生物不主动敌对 */
    public static boolean isEnemy(String faction, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target instanceof Player) {
            return false;
        }
        String other = FactionData.factionOf(target);
        return hostile(faction, other);
    }

    /* 判断两个实体是否属于同一阵营或合作阵营 */
    public static boolean allied(@Nullable Entity first, @Nullable Entity second) {
        if (first == null || second == null) {
            return false;
        }
        String a = FactionData.factionOf(first);
        String b = FactionData.factionOf(second);
        if (normalize(a).isEmpty() || normalize(b).isEmpty()) {
            return false;
        }
        return same(a, b) || !hostile(a, b);
    }
}
