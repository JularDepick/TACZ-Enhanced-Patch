package com.taczenhancedpatch.hitindicator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 枪击来源提示。
 * 参考吃鸡类射击游戏的受击方向提醒: 屏幕中央一圈圆形上的弧形色块指向伤害来源,
 * 只取水平方位, 因此随镜头转动自动适配。圆环半径按屏幕高度换算, 色块内外缘与两端都做渐变, 观感为边缘模糊。
 * 最多同时显示三个来源, 旧的会被移除。停止受击一秒后整体淡出。仅本地玩家可见, 由客户端开关控制。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HitIndicatorOverlay {
    private static final long HOLD_MILLIS = 1000L;
    private static final long FADE_MILLIS = 300L;
    /* 圆环半径相对屏幕高度的比例: 取 0.4 表示圆环直径占满屏幕高度的 80% */
    private static final double RADIUS_HEIGHT_RATIO = 0.4D;
    /* 色块厚度, 单位像素 */
    private static final double THICKNESS = 10.0D;
    /* 径向与弧向的渐变层数, 层数越多边缘越柔和 */
    private static final int BLUR_LAYERS = 6;
    /* 渐变过渡占整层与整段弧长的比例, 中间部分保持满亮度 */
    private static final double RADIAL_SOFTNESS = 0.34D;
    private static final double ANGULAR_SOFTNESS = 0.18D;
    /* 最外侧与最末端的残留权重, 保证模糊边缘不会突然消失 */
    private static final double EDGE_FLOOR = 0.06D;
    private static final double SPAN_DEGREES = 45.0D;
    private static final int SEGMENTS = 18;
    private static final int COLOR = 0xFF3B30;
    /* 不透明度 80% */
    private static final int MAX_ALPHA = 0xCC;
    /* 同时保留的来源数量上限, 超出后移除最早的一条 */
    private static final int MAX_HITS = 3;

    private static final Deque<Hit> HITS = new ArrayDeque<>();

    private HitIndicatorOverlay() {
    }

    /* 由网络包在客户端主线程调用, 记录来源方位与时间戳 */
    public static void accept(float incomingYaw) {
        HITS.addLast(new Hit(incomingYaw, Util.getMillis()));
        while (HITS.size() > MAX_HITS) {
            HITS.removeFirst();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientConfig.hitIndicator() || HITS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        long now = Util.getMillis();
        HITS.removeIf(hit -> now - hit.time() > HOLD_MILLIS + FADE_MILLIS);
        if (HITS.isEmpty()) {
            return;
        }
        float cameraYaw = minecraft.gameRenderer.getMainCamera().getYRot();
        float centerX = minecraft.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = minecraft.getWindow().getGuiScaledHeight() / 2.0F;
        double radius = minecraft.getWindow().getGuiScaledHeight() * RADIUS_HEIGHT_RATIO;
        GuiGraphics graphics = event.getGuiGraphics();
        PoseStack pose = graphics.pose();
        double segmentStep = SPAN_DEGREES / SEGMENTS;
        double layerThickness = THICKNESS / BLUR_LAYERS;
        for (Hit hit : HITS) {
            long elapsed = now - hit.time();
            float fade = elapsed <= HOLD_MILLIS
                    ? 1.0F
                    : 1.0F - (float) (elapsed - HOLD_MILLIS) / (float) FADE_MILLIS;
            if (fade <= 0.0F) {
                continue;
            }
            float relative = Mth.wrapDegrees(hit.sourceYaw() - cameraYaw);
            pose.pushPose();
            pose.translate(centerX, centerY, 0.0F);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float angle = (float) (relative - SPAN_DEGREES / 2.0D + segment * segmentStep);
                double angular = taper((segment + 0.5D) / SEGMENTS, ANGULAR_SOFTNESS);
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(angle));
                for (int layer = 0; layer < BLUR_LAYERS; layer++) {
                    double radial = taper((layer + 0.5D) / BLUR_LAYERS, RADIAL_SOFTNESS);
                    int alpha = (int) Math.round(fade * MAX_ALPHA * radial * angular);
                    if (alpha <= 0) {
                        continue;
                    }
                    int inner = (int) Math.round(-(radius + layer * layerThickness));
                    int outer = (int) Math.round(-(radius + (layer + 1) * layerThickness));
                    graphics.fill((int) -(THICKNESS / 2.0D), outer, (int) (THICKNESS / 2.0D), inner,
                            (alpha << 24) | COLOR);
                }
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /* 两端平滑衰减: 位置越靠边权重越低, 中间保持满值 */
    private static double taper(double position, double softness) {
        double ramp = Math.min(position, 1.0D - position) / softness;
        double weight = Mth.clamp(ramp, 0.0D, 1.0D);
        return EDGE_FLOOR + (1.0D - EDGE_FLOOR) * weight;
    }

    private record Hit(float sourceYaw, long time) {
    }
}
