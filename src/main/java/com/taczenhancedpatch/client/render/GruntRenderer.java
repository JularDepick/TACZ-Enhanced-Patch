package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.GunnerGrunt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 持枪小兵渲染。
 * 以僵尸村民的模型层为骨架, 头部与四肢齐全, 只把贴图换成自绘的特种兵迷彩;
 * 整体按小尺寸绘制, 手中的枪械通过手持物品图层显示。
 */
public class GruntRenderer extends MobRenderer<GunnerGrunt, HumanoidModel<GunnerGrunt>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":textures/entity/gunner_grunt.png");
    /* 相对成人模型的缩放, 与 0.95 格高的碰撞箱匹配 */
    private static final float MODEL_SCALE = 0.6F;

    public GruntRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER)), 0.3F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GunnerGrunt entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(GunnerGrunt entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
