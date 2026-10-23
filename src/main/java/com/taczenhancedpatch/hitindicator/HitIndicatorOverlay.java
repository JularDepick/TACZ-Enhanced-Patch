package com.taczenhancedpatch.hitindicator;

import com.mojang.math.Axis;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪击来源提示。
 * 参考吃鸡类射击游戏的受击方向提醒: 屏幕中央一圈圆形上的弧形色块指向伤害来源,
 * 只取水平方位, 因此随镜头转动自动适配, 第一人称与第三人称一致。
 * 停止受击一秒后整体淡出。仅本地玩家可见, 由客户端开关控制。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HitIndicatorOverlay {
    /* 命中后保持完全可见的时长, 单位毫秒 */
    private static final long HOLD_MILLIS = 1000L;
    /* 保持结束后的淡出时长, 单位毫秒 */
    private static final long FADE_MILLIS = 300L;
    /* 弧块到屏幕中心的距离与厚度, 单位像素 */
    private static final double RADIUS = 78.0D;
    private static final double THICKNESS = 10.0D;
    /* 弧块覆盖的角度范围与分段数量, 分段越多越平滑 */
    private static final double SPAN_DEGREES = 45.0D;
    private static final int SEGMENTS = 18;
    /* 提示色, 不带透明度 */
    private static final int COLOR = 0xFF3B30;
    private static final int MAX_ALPHA = 0xE6;

    private static float sourceYaw;
    private static long lastHitAt = -1L;

    private HitIndicatorOverlay() {
    }

    /* 由网络包在客户端主线程调用, 记录来源方位与时间戳 */
    public static void accept(float incomingYaw) {
        sourceYaw = incomingYaw;
        lastHitAt = Util.getMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientConfig.hitIndicator() || lastHitAt < 0L) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        long elapsed = Util.getMillis() - lastHitAt;
        if (elapsed > HOLD_MILLIS + FADE_MILLIS) {
            return;
        }
        float alpha = elapsed <= HOLD_MILLIS
                ? 1.0F
                : 1.0F - (float) (elapsed - HOLD_MILLIS) / (float) FADE_MILLIS;
        /* 镜头朝向对三种人称都有效, 用它把世界方位换算成屏幕方向 */
        float cameraYaw = minecraft.gameRenderer.getMainCamera().getYRot();
        float relative = Mth.wrapDegrees(sourceYaw - cameraYaw);
        int color = ((int) (alpha * MAX_ALPHA) << 24) | COLOR;

        GuiGraphics graphics = event.getGuiGraphics();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(minecraft.getWindow().getGuiScaledWidth() / 2.0F,
                minecraft.getWindow().getGuiScaledHeight() / 2.0F, 0.0F);
        double step = SPAN_DEGREES / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = (float) (relative - SPAN_DEGREES / 2.0D + i * step);
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            graphics.fill((int) -(THICKNESS / 2.0D), (int) -(RADIUS + THICKNESS),
                    (int) (THICKNESS / 2.0D), (int) -RADIUS, color);
            pose.popPose();
        }
        pose.popPose();
    }
}
