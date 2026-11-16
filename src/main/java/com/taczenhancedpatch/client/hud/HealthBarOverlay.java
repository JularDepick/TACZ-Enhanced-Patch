package com.taczenhancedpatch.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * 生物血量显示。
 * 在生物与玩家头顶渲染一条随剩余生命收缩的血条, 上方再写当前生命与生命上限, 数值不画阴影。
 * 绘制放在关卡渲染的最后一个阶段, 因此不会被云层, 其他生物或粒子盖住; 同时关掉深度测试, 方块也挡不住。
 * 血条走即时绘制(Tesselator), 文字走独立的一次性缓冲源, 两者都不借用关卡渲染共用的缓冲, 避免顶点格式被别的批次污染。
 * 只影响本客户端显示, 默认开启; 只画玩家 16 格以内的生物, 被方块挡住的也不画。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HealthBarOverlay {
    /* 只显示玩家附近的生物, 太远的直接不画 */
    private static final double MAX_DISTANCE = 16.0D;
    private static final float BAR_WIDTH = 40.0F;
    private static final float BAR_HEIGHT = 4.0F;
    private static final float SCALE = 0.025F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private HealthBarOverlay() {
    }

    /* 一帧内要画的一个条目: 相对相机的坐标, 高度, 生命比例与文字 */
    private record Row(double x, double y, double z, float height, float ratio, String text) {
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
        List<Row> rows = new ArrayList<>();
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
            /* 被方块挡住的也不画: 从镜头往生物躯干打一条视线, 中途撞到方块就跳过 */
            if (occluded(minecraft, living, camera)) {
                continue;
            }
            float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
            rows.add(new Row(
                    Mth.lerp(partial, living.xOld, living.getX()) - camera.x,
                    Mth.lerp(partial, living.yOld, living.getY()) - camera.y,
                    Mth.lerp(partial, living.zOld, living.getZ()) - camera.z,
                    living.getBbHeight(),
                    ratio,
                    format(living.getHealth()) + "/" + format(living.getMaxHealth())));
        }
        if (rows.isEmpty()) {
            return;
        }
        /*
         * 用自己的位姿栈, 不用关卡那一个。
         * 关卡渲染到这一阶段时位姿栈里还压着相机旋转, 在它上面再做平移会把血条甩到别处;
         * 顶点坐标本来就是相对相机的, 相机旋转已经由模型视图矩阵负责, 这里只需要单位矩阵起步。
         */
        PoseStack pose = new PoseStack();
        drawBars(minecraft, pose, rows);
        drawTexts(minecraft, pose, rows);
    }

    /* 视线判定: 镜头到生物躯干中心之间是否有实体方块挡着 */
    private static boolean occluded(Minecraft minecraft, LivingEntity living, Vec3 camera) {
        if (minecraft.level == null) {
            return false;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(camera, living.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, living));
        return hit.getType() != HitResult.Type.MISS;
    }

    /* 血条本体: 即时绘制, 不碰关卡渲染的共用缓冲源 */
    private static void drawBars(Minecraft minecraft, PoseStack pose, List<Row> rows) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Row row : rows) {
                pose.pushPose();
                pose.translate(row.x(), row.y() + row.height() + 0.55D, row.z());
                pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
                pose.scale(-SCALE, -SCALE, SCALE);
                Matrix4f matrix = pose.last().pose();
                quad(builder, matrix, -BAR_WIDTH / 2.0F - 1.0F, -1.0F, BAR_WIDTH + 2.0F, BAR_HEIGHT + 2.0F,
                        0.0F, 0.0F, 0.0F, 0.6F);
                quad(builder, matrix, -BAR_WIDTH / 2.0F, 0.0F, BAR_WIDTH * row.ratio(), BAR_HEIGHT,
                        red(row.ratio()), green(row.ratio()), 0.2F, 1.0F);
                pose.popPose();
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    /* 数值: 文字只能走缓冲源, 这里用一次性的即时源, 画完立刻冲刷 */
    private static void drawTexts(Minecraft minecraft, PoseStack pose, List<Row> rows) {
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        for (Row row : rows) {
            Component text = Component.literal(row.text());
            pose.pushPose();
            pose.translate(row.x(), row.y() + row.height() + 0.78D, row.z());
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-SCALE * 0.8F, -SCALE * 0.8F, SCALE * 0.8F);
            float width = minecraft.font.width(text);
            /* 不画阴影: 阴影会把数字压成一团, 远看只剩一片黑 */
            minecraft.font.drawInBatch(text, -width / 2.0F, -8.0F, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void quad(BufferBuilder consumer, Matrix4f matrix, float x, float y, float width, float height,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x + width, y + height, 0.0F).color(red, green, blue, alpha).endVertex();
        consumer.vertex(matrix, x + width, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    /* 整数就不带小数, 出现小数才保留一位 */
    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /* 满血偏绿, 半血偏黄, 残血偏红 */
    private static float red(float ratio) {
        return ratio < 0.5F ? 1.0F : (1.0F - ratio) * 2.0F;
    }

    private static float green(float ratio) {
        return ratio < 0.5F ? ratio * 2.0F : 1.0F;
    }
}
