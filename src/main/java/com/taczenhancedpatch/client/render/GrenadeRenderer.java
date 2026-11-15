package com.taczenhancedpatch.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczenhancedpatch.grenade.GrenadeProjectile;
import com.taczenhancedpatch.item.PatchItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/*
 * 手雷投掷物的渲染。
 * 投掷物本身只是一枚物品, 因此直接复用物品渲染, 朝向跟着镜头, 让玩家总能看清手里的东西飞出去。
 */
public class GrenadeRenderer extends EntityRenderer<GrenadeProjectile> {
    private final ItemRenderer itemRenderer;
    private final ItemStack stack = new ItemStack(PatchItems.GRENADE.get());

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
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        this.itemRenderer.renderStatic(this.stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
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
