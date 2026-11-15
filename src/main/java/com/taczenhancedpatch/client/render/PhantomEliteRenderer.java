package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;

/*
 * 幻机渲染。
 * 模型与动画沿用原版幻翼, 只把贴图换成与小兵和坦克同一套迷彩配色, 让三种遗迹生物看起来是一支队伍。
 */
public class PhantomEliteRenderer extends PhantomRenderer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":textures/entity/phantom_elite.png");

    public PhantomEliteRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Phantom phantom) {
        return TEXTURE;
    }
}
