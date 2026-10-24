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
        double direction = Math.atan2(dz, dx) / (Math.PI * 2.0D);
        double body = entity.getYRot() / 360.0D;
        return (float) Mth.positiveModulo(direction - body, 1.0D);
    }
}