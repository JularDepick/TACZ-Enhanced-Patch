package com.taczenhancedpatch.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/*
 * 生物血量显示。
 * 在生物与玩家头顶渲染一条随剩余生命收缩的血条, 上方再写当前生命与生命上限;
 * 只影响本客户端显示, 默认开启, 距离超过 48 格不画。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HealthBarOverlay {
    private static final double MAX_DISTANCE = 48.0D;
    private static final float BAR_WIDTH = 40.0F;
    private static final float BAR_HEIGHT = 4.0F;
    private static final float SCALE = 0.025F;

    /*
     * 血条专用批次: 半透明四边形, 关闭上传前的距离排序。
     * 底色与填充两块四边形共面, 排序按质心到相机的距离决定先后, 而填充块未满血时质心会偏移,
     * 两者的先后顺序随视角翻转, 底色偶尔盖到填充上, 看上去就是颜色在闪;
     * 关闭排序后按提交顺序绘制, 底色恒定在填充之下。
     * 不写深度, 避免血条挡住之后绘制的文字与粒子。
     */
    private static final RenderStateShard.ShaderStateShard BAR_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader);
    private static final RenderStateShard.TransparencyStateShard BAR_TRANSLUCENT =
            new RenderStateShard.TransparencyStateShard("tacz_enhanced_patch_translucent", () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
    private static final RenderStateShard.DepthTestStateShard BAR_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("<=", GL11.GL_LEQUAL);
    private static final RenderStateShard.WriteMaskStateShard BAR_COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);
    private static final RenderStateShard.CullStateShard BAR_NO_CULL =
            new RenderStateShard.CullStateShard(false);
    private static final RenderType HEALTH_BAR = RenderType.create("tacz_enhanced_patch_health_bar",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(BAR_SHADER)
                    .setTransparencyState(BAR_TRANSLUCENT)
                    .setDepthTestState(BAR_DEPTH_TEST)
                    .setWriteMaskState(BAR_COLOR_WRITE)
                    .setCullState(BAR_NO_CULL)
                    .createCompositeState(false));

    private HealthBarOverlay() {
    }

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<LivingEntity, ?> event) {
        if (!ClientConfig.healthBars()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !entity.isAlive() || entity.isInvisible()) {
            return;
        }
        if (entity == minecraft.getCameraEntity()) {
            return;
        }
        if (entity.distanceToSqr(minecraft.player) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        float ratio = Mth.clamp(entity.getHealth() / Math.max(1.0F, entity.getMaxHealth()), 0.0F, 1.0F);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        pose.pushPose();
        pose.translate(0.0D, entity.getBbHeight() + 0.55D, 0.0D);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SCALE, -SCALE, SCALE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer quads = buffers.getBuffer(HEALTH_BAR);
        quad(quads, matrix, -BAR_WIDTH / 2.0F - 1.0F, -1.0F, BAR_WIDTH + 2.0F, BAR_HEIGHT + 2.0F, 0.0F, 0.0F, 0.0F, 0.6F);
        quad(quads, matrix, -BAR_WIDTH / 2.0F, 0.0F, BAR_WIDTH * ratio, BAR_HEIGHT, red(ratio), green(ratio), 0.2F, 1.0F);
        pose.popPose();
        Component text = Component.literal(String.format(Locale.ROOT, "%.0f/%.0f",
                entity.getHealth(), entity.getMaxHealth()));
        pose.pushPose();
        pose.translate(0.0D, entity.getBbHeight() + 0.78D, 0.0D);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SCALE * 0.8F, -SCALE * 0.8F, SCALE * 0.8F);
        Matrix4f textMatrix = pose.last().pose();
        float width = minecraft.font.width(text);
        /* 不画阴影: 阴影会把数字压成一团, 远看只剩一片黑 */
        minecraft.font.drawInBatch(text, -width / 2.0F, -8.0F, 0xFFFFFFFF, false, textMatrix, buffers,
                Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float width, float height,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x + width, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x + width, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    /* 满血偏绿, 半血偏黄, 残血偏红 */
    private static float red(float ratio) {
        return ratio < 0.5F ? 1.0F : (1.0F - ratio) * 2.0F;
    }

    private static float green(float ratio) {
        return ratio < 0.5F ? ratio * 2.0F : 1.0F;
    }
}
