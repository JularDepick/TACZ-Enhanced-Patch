package com.taczenhancedpatch.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * 阵营数据的读写。
 * 只操作实体持久化数据里的几个字段, 不引入新的实体类型;
 * 阵营 ID, 归属遗迹中心与复活深度都随实体保存。
 */
public final class FactionData {
    private static final String TAG_FACTION = "PatchFaction";
    private static final String TAG_HOME_X = "PatchHomeX";
    private static final String TAG_HOME_Y = "PatchHomeY";
    private static final String TAG_HOME_Z = "PatchHomeZ";
    private static final String TAG_HAS_HOME = "PatchHasHome";
    private static final String TAG_REVIVE_DEPTH = "PatchReviveDepth";

    private FactionData() {
    }

    public static String factionOf(@Nullable Entity entity) {
        if (entity == null) {
            return Factions.NONE;
        }
        CompoundTag tag = entity.getPersistentData();
        return tag.contains(TAG_FACTION) ? tag.getString(TAG_FACTION) : Factions.NONE;
    }

    public static void setFaction(Entity entity, String faction) {
        CompoundTag tag = entity.getPersistentData();
        if (faction == null || faction.isEmpty()) {
            tag.remove(TAG_FACTION);
        } else {
            tag.putString(TAG_FACTION, faction);
        }
    }

    public static boolean hasHome(Entity entity) {
        return entity.getPersistentData().getBoolean(TAG_HAS_HOME);
    }

    @Nullable
    public static BlockPos homeOf(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (!tag.getBoolean(TAG_HAS_HOME)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_HOME_X), tag.getInt(TAG_HOME_Y), tag.getInt(TAG_HOME_Z));
    }

    public static void setHome(Entity entity, @Nullable BlockPos home) {
        CompoundTag tag = entity.getPersistentData();
        if (home == null) {
            tag.remove(TAG_HAS_HOME);
            return;
        }
        tag.putBoolean(TAG_HAS_HOME, true);
        tag.putInt(TAG_HOME_X, home.getX());
        tag.putInt(TAG_HOME_Y, home.getY());
        tag.putInt(TAG_HOME_Z, home.getZ());
    }

    public static int reviveDepth(Entity entity) {
        return entity.getPersistentData().getInt(TAG_REVIVE_DEPTH);
    }

    public static void setReviveDepth(Entity entity, int depth) {
        entity.getPersistentData().putInt(TAG_REVIVE_DEPTH, Math.max(0, depth));
    }

    /* 由遗迹中心生成稳定的阵营 ID */
    public static String factionOfRuin(BlockPos origin) {
        return "ruin:" + origin.getX() + ":" + origin.getZ();
    }
}
