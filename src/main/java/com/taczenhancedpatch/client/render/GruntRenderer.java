package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.GunnerGrunt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 持枪小兵渲染。
 * 直接使用小村民的模型与骨骼, 只换成特种兵迷彩贴图, 整体按一半尺寸绘制;
 * 手中的枪械通过交叉手臂图层呈现, 与村民抱物的表现一致。
 */
public class GruntRenderer extends MobRenderer<GunnerGrunt, VillagerModel<GunnerGrunt>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":textures/entity/gunner_grunt.png");

    public GruntRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.3F);
        this.model.hatVisible(false);
        this.addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GunnerGrunt entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(GunnerGrunt entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.5F, 0.5F, 0.5F);
    }
}
