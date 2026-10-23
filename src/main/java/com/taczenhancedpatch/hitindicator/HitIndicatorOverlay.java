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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 枪击来源提示。
 * 参考吃鸡类射击游戏的受击方向提醒: 屏幕中央一圈圆形上的弧形色块指向伤害来源,
 * 只取水平方位, 因此随镜头转动自动适配。最多同时显示三个来源, 旧的会被移除。
 * 停止受击一秒后整体淡出。仅本地玩家可见, 由客户端开关控制。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class HitIndicatorOverlay {
    private static final long HOLD_MILLIS = 1000L;
    private static final long FADE_MILLIS = 300L;
    private static final double RADIUS = 78.0D;
    private static final double THICKNESS = 10.0D;
    private static final double SPAN_DEGREES = 45.0D;
    private static final int SEGMENTS = 18;
    private static final int COLOR = 0xFF3B30;
    private static final int MAX_ALPHA = 0xE6;
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
        GuiGraphics graphics = event.getGuiGraphics();
        PoseStack pose = graphics.pose();
        for (Hit hit : HITS) {
            long elapsed = now - hit.time();
            float alpha = elapsed <= HOLD_MILLIS
                    ? 1.0F
                    : 1.0F - (float) (elapsed - HOLD_MILLIS) / (float) FADE_MILLIS;
            if (alpha <= 0.0F) {
                continue;
            }
            int color = ((int) (alpha * MAX_ALPHA) << 24) | COLOR;
            float relative = Mth.wrapDegrees(hit.sourceYaw() - cameraYaw);
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

    private record Hit(float sourceYaw, long time) {
    }
}
