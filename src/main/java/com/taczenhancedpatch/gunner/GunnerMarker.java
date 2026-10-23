package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 枪手身份标记。
 * 数据写入实体的持久化 NBT, 因此跨存档与区块重载后仍然有效。
 */
public final class GunnerMarker {
    private static final String ROOT = "tacz_enhanced_patch_gunner";
    private static final String GUN = "gun";
    private static final String AMMO = "ammo";
    private static final String TIER = "tier";
    private static final String FIRE_MODE = "fireMode";
    private static final String AMMO_POOL = "ammoPool";
    private static final String DROP_MIN = "dropMin";
    private static final String DROP_MAX = "dropMax";
    private static final String DRY = "dry";
    /* 是否已经做过枪手判定, 记录在实体持久化数据的顶层, 不随身份标记一起清除 */
    private static final String SPAWN_EVALUATED = "tacz_enhanced_patch_spawn_evaluated";

    public record Loadout(ResourceLocation gunId,
                          ResourceLocation ammoId,
                          GunnerArsenal.Tier tier,
                          TaczApi.FireMode fireMode,
                          int ammoPool,
                          int ammoDropMin,
                          int ammoDropMax) {
    }

    private GunnerMarker() {
    }

    public static void mark(LivingEntity entity, GunnerArsenal.GunProfile profile) {
        CompoundTag data = new CompoundTag();
        data.putString(GUN, profile.gunId().toString());
        data.putString(AMMO, profile.ammoId().toString());
        data.putString(TIER, profile.tier().name());
        data.putString(FIRE_MODE, profile.fireMode().name());
        data.putInt(AMMO_POOL, profile.ammoPool());
        data.putInt(DROP_MIN, profile.ammoDropMin());
        data.putInt(DROP_MAX, profile.ammoDropMax());
        data.putBoolean(DRY, false);
        entity.getPersistentData().put(ROOT, data);
    }

    public static boolean isGunner(LivingEntity entity) {
        return entity.getPersistentData().contains(ROOT, Tag.TAG_COMPOUND);
    }

    @Nullable
    public static Loadout loadout(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag data = root.getCompound(ROOT);
        ResourceLocation gunId = ResourceLocation.tryParse(data.getString(GUN));
        ResourceLocation ammoId = ResourceLocation.tryParse(data.getString(AMMO));
        if (gunId == null || ammoId == null) {
            return null;
        }
        GunnerArsenal.Tier tier = parseTier(data.getString(TIER));
        TaczApi.FireMode fireMode = TaczApi.FireMode.fromName(data.getString(FIRE_MODE));
        int ammoPool = Math.max(0, data.getInt(AMMO_POOL));
        int dropMin = Math.max(1, data.getInt(DROP_MIN));
        int dropMax = Math.max(dropMin, data.getInt(DROP_MAX));
        return new Loadout(gunId, ammoId, tier, fireMode, ammoPool, dropMin, dropMax);
    }

    public static boolean isDry(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        return root.getCompound(ROOT).getBoolean(DRY);
    }

    public static void setDry(LivingEntity entity, boolean dry) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        root.getCompound(ROOT).putBoolean(DRY, dry);
    }

    public static boolean isSpawnEvaluated(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(SPAWN_EVALUATED);
    }

    public static void markSpawnEvaluated(LivingEntity entity) {
        entity.getPersistentData().putBoolean(SPAWN_EVALUATED, true);
    }

    public static void clear(LivingEntity entity) {
        entity.getPersistentData().remove(ROOT);
    }

    private static GunnerArsenal.Tier parseTier(String name) {
        try {
            return GunnerArsenal.Tier.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return GunnerArsenal.Tier.LOW;
        }
    }

}
