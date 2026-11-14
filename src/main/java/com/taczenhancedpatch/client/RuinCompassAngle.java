package com.taczenhancedpatch.client;

import com.taczenhancedpatch.item.PatchItems;
import com.taczenhancedpatch.item.RuinCompassItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 遗迹指南针的指针角度。
 * 复用原版指南针的角度属性: 计算目标方向与玩家朝向的夹角并归一到 0 到 1,
 * 模型按该角度切换 32 帧贴图, 因此不需要额外的渲染代码。
 */
public final class RuinCompassAngle {
    private RuinCompassAngle() {
    }

    /* 在客户端初始化时注册一次 */
    public static void register() {
        ItemProperties.register(PatchItems.RUIN_COMPASS.get(), new ResourceLocation("angle"),
                (stack, level, entity, seed) -> angle(stack, entity));
    }

    private static float angle(ItemStack stack, Entity entity) {
        BlockPos target = RuinCompassItem.target(stack);
        if (entity == null || target == null) {
            return 0.0F;
        }
        double dx = target.getX() + 0.5D - entity.getX();
        double dz = target.getZ() + 0.5D - entity.getZ();
        /* 目标方位角: 北为 0 度, 顺时针递增; 与方向标尺同一口径 */
        double bearing = Mth.positiveModulo(Math.toDegrees(Math.atan2(dx, -dz)), 360.0D);
        /* 玩家朝向的方位角: 游戏朝向加半圈 */
        double facing = Mth.positiveModulo(entity.getYRot() + 180.0D, 360.0D);
        /* 相对方位决定贴图帧: 目标在正前方时指针朝上 */
        double relative = Mth.positiveModulo(bearing - facing, 360.0D);
        return (float) (relative / 360.0D);
    }
}