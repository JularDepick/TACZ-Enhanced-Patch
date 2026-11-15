package com.taczenhancedpatch.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

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
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
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
        minecraft.font.drawInBatch(text, -width / 2.0F, -8.0F, 0xFFFFFFFF, true, textMatrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, light);
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
