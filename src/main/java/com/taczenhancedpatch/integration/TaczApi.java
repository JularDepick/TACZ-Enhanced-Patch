package com.taczenhancedpatch.integration;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TACZ 公开 API 的统一入口。
 * 所有对 TACZ 的类型引用集中在此处, 其余包只使用本类的抽象类型,
 * 便于上游模组版本变化时定位适配点。
 */
public final class TaczApi {
    /** 对外暴露的开火模式, 与 TACZ 的 FireMode 一一对应 */
    public enum FireMode {
        SEMI,
        AUTO,
        BURST;

        com.tacz.guns.api.item.gun.FireMode toTacz() {
            return com.tacz.guns.api.item.gun.FireMode.valueOf(name());
        }

        public static FireMode fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return SEMI;
            }
        }
    }

    /** 一条上游扩容弹匣条目: 配件 ID, 名称翻译键与按名称键推断的等级(1 到 3) */
    public record ExtendedMagEntry(ResourceLocation id, String nameKey, int level) {
    }

    /** 对外暴露的射击结果, 收窄 TACZ 的 ShootResult 取值 */
    public enum ShotOutcome {
        SUCCESS,
        COOLDOWN,
        NO_AMMO,
        BLOCKED,
        INVALID,
        UNKNOWN
    }

    private TaczApi() {
    }

    @Nullable
    public static IGun getGun(@Nullable ItemStack stack) {
        return IGun.getIGunOrNull(stack);
    }

    public static boolean isGun(@Nullable ItemStack stack) {
        return getGun(stack) != null;
    }

    @Nullable
    public static ResourceLocation getGunId(@Nullable ItemStack stack) {
        IGun gun = getGun(stack);
        return gun == null ? null : gun.getGunId(stack);
    }

    @Nullable
    public static IAttachment getAttachment(@Nullable ItemStack stack) {
        return IAttachment.getIAttachmentOrNull(stack);
    }

    public static boolean isAttachment(@Nullable ItemStack stack) {
        return getAttachment(stack) != null;
    }

    @Nullable
    public static ResourceLocation getAttachmentId(@Nullable ItemStack stack) {
        IAttachment attachment = getAttachment(stack);
        return attachment == null ? null : attachment.getAttachmentId(stack);
    }

    /**
     * 构建一把带虚拟备弹的枪械物品。
     * 虚拟备弹让非玩家实体无需背包即可换弹, 同时保留弹尽断火的平衡阀门。
     */
    public static ItemStack createGun(ResourceLocation gunId, FireMode fireMode, int dummyAmmo) {
        ItemStack gun = GunItemBuilder.create()
                .setId(gunId)
                .setFireMode(fireMode.toTacz())
                .build();
        if (gun.isEmpty()) {
            gun = GunItemBuilder.create()
                    .setId(gunId)
                    .setFireMode(fireMode.toTacz())
                    .forceBuild();
        }
        IGun iGun = getGun(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        if (dummyAmmo > 0) {
            iGun.setMaxDummyAmmoAmount(gun, dummyAmmo);
            iGun.setDummyAmmoAmount(gun, dummyAmmo);
        }
        return gun;
    }

    public static ItemStack createAmmo(ResourceLocation ammoId, int count) {
        if (ammoId == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        return AmmoItemBuilder.create()
                .setId(ammoId)
                .setCount(count)
                .build();
    }

    public static Optional<ItemStack> randomAttachment(RandomSource random) {
        ResourceLocation chosen = null;
        int seen = 0;
        for (Map.Entry<ResourceLocation, ?> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            seen++;
            if (random.nextInt(seen) == 0) {
                chosen = entry.getKey();
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        ItemStack stack = AttachmentItemBuilder.create().setId(chosen).build();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    /**
     * 给枪械随机安装一件配件。
     * 返回 false 表示池中没有可用配件或该枪不接受这件配件。
     */
    public static boolean installRandomAttachment(ItemStack gunStack, RandomSource random) {
        IGun gun = getGun(gunStack);
        if (gun == null) {
            return false;
        }
        return randomAttachment(random).map(attachment -> {
            gun.installAttachment(gunStack, attachment);
            return true;
        }).orElse(false);
    }

    /* 按配件 ID 构建一个上游配件物品, ID 无效时返回空物品 */
    public static ItemStack createAttachment(ResourceLocation attachmentId) {
        if (attachmentId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = AttachmentItemBuilder.create().setId(attachmentId).build();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /* 枪械的弹匣容量, 已计入当前装配的扩容弹匣带来的加成 */
    public static int maxMagazineAmmo(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        if (gun == null) {
            return 0;
        }
        ResourceLocation gunId = gun.getGunId(gunStack);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> AttachmentDataUtils.getAmmoCountWithAttachment(gunStack, index.getGunData()))
                .orElse(0);
    }

    /* 直接把弹匣子弹数写到指定值 */
    public static void setMagazineAmmo(@Nullable ItemStack gunStack, int amount) {
        IGun gun = getGun(gunStack);
        if (gun != null) {
            gun.setCurrentAmmoCount(gunStack, Math.max(0, amount));
        }
    }

    /* 把虚拟备弹补满, 让使用背包弹药的枪械改为消耗虚拟备弹 */
    public static void fillDummyAmmo(@Nullable ItemStack gunStack, int amount) {
        IGun gun = getGun(gunStack);
        if (gun == null) {
            return;
        }
        if (!gun.hasMaxDummyAmmo(gunStack) || gun.getMaxDummyAmmoAmount(gunStack) != amount) {
            gun.setMaxDummyAmmoAmount(gunStack, amount);
        }
        if (gun.getDummyAmmoAmount(gunStack) != amount) {
            gun.setDummyAmmoAmount(gunStack, amount);
        }
    }

    /* 枪械当前装配的扩容弹匣, 未装配时返回空物品 */
    public static ItemStack getExtendedMag(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }
        ItemStack attachment = gun.getAttachment(gunStack, AttachmentType.EXTENDED_MAG);
        return attachment == null ? ItemStack.EMPTY : attachment;
    }

    /* 枪械自带的扩容弹匣(内置配件), 部分枪械没有可拆卸弹匣槽 */
    public static ItemStack getBuiltinExtendedMag(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }
        ItemStack attachment = gun.getBuiltinAttachment(gunStack, AttachmentType.EXTENDED_MAG);
        return attachment == null ? ItemStack.EMPTY : attachment;
    }

    /* 无限弹匣的判定: 可拆卸弹匣与内置弹匣都检查一遍 */
    public static boolean hasInfiniteMag(ItemStack gunStack) {
        return com.taczenhancedpatch.item.InfiniteMag.isInfinite(getExtendedMag(gunStack))
                || com.taczenhancedpatch.item.InfiniteMag.isInfinite(getBuiltinExtendedMag(gunStack));
    }

    /**
     * 上游全部扩容弹匣条目, 按上游排序值升序。
     * 等级优先从名称翻译键里的 level_N 读取, 读不到时退回配件 ID 的数字后缀。
     */
    public static List<ExtendedMagEntry> extendedMags() {
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : TimelessAPI.getAllCommonAttachmentIndex()) {
            if (entry.getValue().getType() == AttachmentType.EXTENDED_MAG) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingInt(entry -> entry.getValue().getSort()));
        List<ExtendedMagEntry> result = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : entries) {
            String nameKey = entry.getValue().getPojo().getName();
            result.add(new ExtendedMagEntry(entry.getKey(), nameKey, levelOf(nameKey, entry.getKey())));
        }
        return result;
    }

    private static int levelOf(@Nullable String nameKey, ResourceLocation id) {
        if (nameKey != null) {
            int index = nameKey.lastIndexOf("_level_");
            if (index >= 0) {
                int level = parseLevel(nameKey.substring(index + "_level_".length()));
                if (level > 0) {
                    return level;
                }
            }
        }
        String path = id.getPath();
        int separator = path.lastIndexOf('_');
        if (separator >= 0) {
            int level = parseLevel(path.substring(separator + 1));
            if (level > 0) {
                return level;
            }
        }
        return 1;
    }

    private static int parseLevel(String text) {
        try {
            int level = Integer.parseInt(text.trim());
            return level >= 1 && level <= 3 ? level : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /* 当前换弹开始时间戳, 未在换弹时为 -1 */
    public static long reloadStartTimestamp(LivingEntity entity) {
        return IGunOperator.fromLivingEntity(entity).getDataHolder().reloadTimestamp;
    }

    /* 把换弹开始时间戳前移, 让换弹进度以更快的速度推进 */
    public static void advanceReloadStart(LivingEntity entity, long millis) {
        if (millis <= 0L) {
            return;
        }
        var data = IGunOperator.fromLivingEntity(entity).getDataHolder();
        if (data.reloadTimestamp > 0L) {
            data.reloadTimestamp -= millis;
        }
    }

    public static int magazineAmmo(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        return gun == null ? 0 : gun.getCurrentAmmoCount(gunStack);
    }

    public static boolean hasBulletInBarrel(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        return gun != null && gun.hasBulletInBarrel(gunStack);
    }

    public static boolean usesInventoryAmmo(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        return gun != null && gun.useInventoryAmmo(gunStack);
    }

    public static int dummyAmmo(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        return gun == null ? 0 : gun.getDummyAmmoAmount(gunStack);
    }

    /**
     * 判断实体是否为上游的枪械子弹。
     * 子弹实体不在公开 API 内, 这里用类名判断, 避免直接引用其内部类型。
     */
    public static boolean isGunProjectile(@Nullable net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.tacz.guns.entity.EntityKineticBullet");
    }

    /* 清空虚拟备弹, 用于把剩余弹药单独作为战利品掉落, 避免与枪械重复 */
    public static void clearDummyAmmo(@Nullable ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        if (gun != null) {
            gun.setDummyAmmoAmount(gunStack, 0);
        }
    }

    /**
     * 初始化实体的射击状态, 必须在使用任何射击 API 前调用一次。
     * 未初始化时 TACZ 内部的配件属性缓存为空, 射击会被静默跳过。
     */
    public static void initOperator(LivingEntity entity) {
        IGunOperator.fromLivingEntity(entity).initialData();
    }

    public static void aim(LivingEntity entity, boolean aiming) {
        IGunOperator.fromLivingEntity(entity).aim(aiming);
    }

    public static void reload(LivingEntity entity) {
        IGunOperator.fromLivingEntity(entity).reload();
    }

    /* 实体当前是否处于换弹过程中 */
    public static boolean isReloading(LivingEntity entity) {
        return IGunOperator.fromLivingEntity(entity).getSynReloadState().getStateType().isReloading();
    }

    /* 取消当前换弹, 用于拦截上游"弹匣已满仍然换弹"的行为 */
    public static void cancelReload(LivingEntity entity) {
        IGunOperator.fromLivingEntity(entity).cancelReload();
    }

    public static ShotOutcome shoot(LivingEntity shooter, float pitch, float yaw) {
        return switch (IGunOperator.fromLivingEntity(shooter).shoot(() -> pitch, () -> yaw)) {
            case SUCCESS -> ShotOutcome.SUCCESS;
            case COOL_DOWN -> ShotOutcome.COOLDOWN;
            case NO_AMMO -> ShotOutcome.NO_AMMO;
            case NOT_GUN, NOT_DRAW, ID_NOT_EXIST -> ShotOutcome.INVALID;
            case IS_RELOADING, IS_DRAWING, IS_BOLTING, IS_SPRINTING, IS_MELEE, NEED_BOLT,
                 NETWORK_FAIL, OVERHEATED, FORGE_EVENT_CANCEL -> ShotOutcome.BLOCKED;
            case UNKNOWN_FAIL -> ShotOutcome.UNKNOWN;
        };
    }
}
