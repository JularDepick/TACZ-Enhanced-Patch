package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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
    private static final String BUFFED = "buffed";
    private static final String IMPROVISED = "improvised";
    /* 被枪械覆盖掉的原版武器, 弹尽后还原 */
    private static final String REPLACED_WEAPON = "replacedWeapon";
    /* 发给该枪手的那把枪, 弹尽收起后由战利品结算使用 */
    private static final String GUN_STACK = "gunStack";
    /* 是否已经做过临场武装判定, 记录在实体持久化数据的顶层 */
    private static final String RETALIATION_CHECKED = "tacz_enhanced_patch_retaliation_checked";
    /* 是否已经做过枪手判定, 记录在实体持久化数据的顶层, 不随身份标记一起清除 */
    private static final String SPAWN_EVALUATED = "tacz_enhanced_patch_spawn_evaluated";

    public record Loadout(ResourceLocation gunId,
                          ResourceLocation ammoId,
                          GunnerArsenal.Tier tier,
                          TaczApi.FireMode fireMode,
                          int ammoPool,
                          int ammoDropMin,
                          int ammoDropMax,
                          boolean improvised) {
    }

    private GunnerMarker() {
    }

    public static void mark(LivingEntity entity, GunnerArsenal.GunProfile profile, int ammoPool, boolean improvised) {
        CompoundTag data = new CompoundTag();
        data.putString(GUN, profile.gunId().toString());
        data.putString(AMMO, profile.ammoId().toString());
        data.putString(TIER, profile.tier().name());
        data.putString(FIRE_MODE, profile.fireMode().name());
        data.putInt(AMMO_POOL, Math.max(1, ammoPool));
        data.putInt(DROP_MIN, profile.ammoDropMin());
        data.putInt(DROP_MAX, profile.ammoDropMax());
        data.putBoolean(DRY, false);
        data.putBoolean(BUFFED, false);
        data.putBoolean(IMPROVISED, improvised);
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
        return new Loadout(gunId, ammoId, tier, fireMode, ammoPool, dropMin, dropMax, data.getBoolean(IMPROVISED));
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

    public static boolean isBuffed(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        return root.getCompound(ROOT).getBoolean(BUFFED);
    }

    public static void setBuffed(LivingEntity entity, boolean buffed) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        root.getCompound(ROOT).putBoolean(BUFFED, buffed);
    }

    public static void setReplacedWeapon(LivingEntity entity, ItemStack weapon) {
        CompoundTag data = data(entity);
        if (data == null) {
            return;
        }
        if (weapon.isEmpty()) {
            data.remove(REPLACED_WEAPON);
            return;
        }
        data.put(REPLACED_WEAPON, weapon.save(new CompoundTag()));
    }

    public static ItemStack replacedWeapon(LivingEntity entity) {
        CompoundTag data = data(entity);
        if (data == null || !data.contains(REPLACED_WEAPON, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(data.getCompound(REPLACED_WEAPON));
    }

    public static void setStoredGun(LivingEntity entity, ItemStack gun) {
        CompoundTag data = data(entity);
        if (data == null) {
            return;
        }
        if (gun.isEmpty()) {
            data.remove(GUN_STACK);
            return;
        }
        data.put(GUN_STACK, gun.copy().save(new CompoundTag()));
    }

    public static ItemStack storedGun(LivingEntity entity) {
        CompoundTag data = data(entity);
        if (data == null || !data.contains(GUN_STACK, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(data.getCompound(GUN_STACK));
    }

    @Nullable
    private static CompoundTag data(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(ROOT);
    }

    public static boolean isSpawnEvaluated(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(SPAWN_EVALUATED);
    }

    public static boolean isRetaliationChecked(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(RETALIATION_CHECKED);
    }

    public static void markRetaliationChecked(LivingEntity entity) {
        entity.getPersistentData().putBoolean(RETALIATION_CHECKED, true);
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
