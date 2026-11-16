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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/*
 * 生物血量显示。
 * 在生物与玩家头顶渲染一条随剩余生命收缩的血条, 上方再写当前生命与生命上限, 数值不画阴影。
 * 绘制放在关卡渲染的最后一个阶段, 因此不会被云层, 其他生物或粒子盖住;
 * 同时关掉深度测试, 方块与地形也挡不住它。只影响本客户端显示, 默认开启, 距离超过 48 格不画。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HealthBarOverlay {
    private static final double MAX_DISTANCE = 48.0D;
    private static final float BAR_WIDTH = 40.0F;
    private static final float BAR_HEIGHT = 4.0F;
    private static final float SCALE = 0.025F;
    private static final int FULL_BRIGHT = 0xF000F0;

    /*
     * 血条专用批次: 半透明四边形, 关闭上传前的距离排序。
     * 底色与填充两块四边形共面, 排序按质心到相机的距离决定先后, 而填充块未满血时质心会偏移,
     * 两者的先后顺序随视角翻转, 底色偶尔盖到填充上, 看上去就是颜色在闪;
     * 关闭排序后按提交顺序绘制, 底色恒定在填充之下。
     * 深度测试恒通过: 血条要压在云层与生物之上, 不写深度, 避免挡住之后绘制的文字。
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
            new RenderStateShard.DepthTestStateShard("always", GL11.GL_ALWAYS);
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
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!ClientConfig.healthBars()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(HEALTH_BAR);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
                continue;
            }
            if (living == minecraft.getCameraEntity()) {
                continue;
            }
            if (living.distanceToSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            if (!event.getFrustum().isVisible(living.getBoundingBox().inflate(0.5D))) {
                continue;
            }
            double x = Mth.lerp(partial, living.xOld, living.getX()) - camera.x;
            double y = Mth.lerp(partial, living.yOld, living.getY()) - camera.y;
            double z = Mth.lerp(partial, living.zOld, living.getZ()) - camera.z;
            float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
            pose.pushPose();
            pose.translate(x, y + living.getBbHeight() + 0.55D, z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-SCALE, -SCALE, SCALE);
            Matrix4f matrix = pose.last().pose();
            quad(quads, matrix, -BAR_WIDTH / 2.0F - 1.0F, -1.0F, BAR_WIDTH + 2.0F, BAR_HEIGHT + 2.0F, 0.0F, 0.0F, 0.0F, 0.6F);
            quad(quads, matrix, -BAR_WIDTH / 2.0F, 0.0F, BAR_WIDTH * ratio, BAR_HEIGHT, red(ratio), green(ratio), 0.2F, 1.0F);
            pose.popPose();
            Component text = Component.literal(format(living.getHealth()) + "/" + format(living.getMaxHealth()));
            pose.pushPose();
            pose.translate(x, y + living.getBbHeight() + 0.78D, z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-SCALE * 0.8F, -SCALE * 0.8F, SCALE * 0.8F);
            float width = minecraft.font.width(text);
            /* 不画阴影: 阴影会把数字压成一团, 远看只剩一片黑 */
            minecraft.font.drawInBatch(text, -width / 2.0F, -8.0F, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            pose.popPose();
        }
        /* 关卡末阶段没有统一的批次收尾, 这里自己把血条与文字刷出去 */
        buffers.endBatch();
    }

    /* 整数就不带小数, 出现小数才保留一位 */
    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
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
