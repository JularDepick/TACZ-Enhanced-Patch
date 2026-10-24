package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.TankElite;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 坦克精英怪渲染。
 * 使用自定义坦克模型与自绘贴图, 阴影半径较大以强化体量感。
 */
public class TankRenderer extends MobRenderer<TankElite, TankModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TACZEnhancedPatch.MOD_ID, "textures/entity/tank_elite.png");

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context, new TankModel(context.bakeLayer(TankModel.LAYER)), 1.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(TankElite entity) {
        return TEXTURE;
    }
}
