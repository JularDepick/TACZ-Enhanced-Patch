package com.taczenhancedpatch.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczenhancedpatch.mortar.MortarBlockEntity;
import com.taczenhancedpatch.mortar.MortarRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.model.data.ModelData;

/*
 * 迫击炮渲染。
 * 基座固定不动, 炮筒单独渲染并按水平角与仰角旋转, 所以调节角度时能直接看到炮管转向。
 */
public class MortarBlockEntityRenderer implements BlockEntityRenderer<MortarBlockEntity> {
    private static final ModelData DATA = ModelData.EMPTY;

    public MortarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MortarBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        /* 基座已经由方块模型渲染, 这里只画会转的炮筒 */
        poseStack.pushPose();
        /* 枢轴落在中柱顶端, 炮筒从这里起转; 水平角按世界方位换算, 与射向一致 */
        poseStack.translate(0.5D, 0.4375D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.yawDeg()));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F - entity.pitchDeg()));
        poseStack.translate(-0.5D, -0.4375D, -0.5D);
        dispatcher.renderSingleBlock(MortarRegistry.MORTAR_BARREL.get().defaultBlockState(), poseStack, buffers,
                light, OverlayTexture.NO_OVERLAY, DATA, RenderType.solid());
        poseStack.popPose();
    }
}
