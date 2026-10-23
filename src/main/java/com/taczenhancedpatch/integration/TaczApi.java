package com.taczenhancedpatch.integration;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * TACZ 公开 API 的统一入口。
 * 所有对 TACZ 的类型引用集中在此处, 便于前置模组版本变化时定位适配点。
 */
public final class TaczApi {
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
}
