package com.taczenhancedpatch.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczenhancedpatch.grenade.GrenadeProjectile;
import com.taczenhancedpatch.item.PatchItems;
import com.taczenhancedpatch.shell.ShellItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/*
 * 手雷投掷物的渲染。
 * 用三维物品模型按飞行方向摆正, 不再朝着镜头贴一张平面图, 因此转视角时手雷不会跟着转。
 * 静止后保持最后一次朝向, 只做缓慢自转。
 */
public class GrenadeRenderer extends EntityRenderer<GrenadeProjectile> {
    private final ItemRenderer itemRenderer;

    public GrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.4F;
    }

    @Override
    public void render(GrenadeProjectile entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.2D, 0.0D);
        Vec3 motion = entity.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal > 0.01D) {
            float heading = (float) (Math.toDegrees(Math.atan2(motion.x, motion.z)));
            float pitch = (float) (Math.toDegrees(Math.atan2(motion.y, horizontal)));
            poseStack.mulPose(Axis.YP.rotationDegrees(heading));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
        /* 缓慢自转, 让静止的手雷也有立体感 */
        poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 6.0F));
        /* 按投掷物自己的属性重建物品栈, 颜色与手雷实际属性一致 */
        ItemStack stack = new ItemStack(PatchItems.GRENADE.get());
        if (stack.getItem() instanceof ShellItem shell) {
            shell.setSettings(stack, entity.settings());
        }
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffers, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /* 物品渲染不按贴图定位, 返回方块图集占位即可 */
    @Override
    public ResourceLocation getTextureLocation(GrenadeProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
