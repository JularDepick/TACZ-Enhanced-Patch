package com.taczenhancedpatch.client.hud;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * 遗迹兵力读数。
 * 位于屏幕右上角, 显示玩家所在遗迹的剩余兵力百分比, 服务端每 20 tick 刷新一次;
 * 数值按危险程度分层染色, 药水效果占用右上角时自动下移。
 * 离开遗迹或超过刷新时限时自动隐藏。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class RuinGarrisonOverlay {
    private static final int MARGIN = 8;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int BACKGROUND = 0x66000000;
    /* 原版药水效果每行占用的高度 */
    private static final int EFFECT_ROW = 26;
    private static final int MAX_EFFECT_ROWS = 6;
    /* 超过该时长没有刷新就认为已经离开遗迹 */
    private static final long STALE_MILLIS = 2500L;

    private static int percent = -1;
    private static long updatedAt;

    private RuinGarrisonOverlay() {
    }

    /* 服务端下发时调用, -1 表示隐藏 */
    public static void accept(int value) {
        percent = value;
        updatedAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (percent < 0 || System.currentTimeMillis() - updatedAt > STALE_MILLIS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        String text = UiText.of("hud.ruin.garrison", percent + "%").getString();
        int width = minecraft.font.width(text);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int left = screenWidth - MARGIN - width - PADDING_X * 2;
        int top = MARGIN + effectOffset(minecraft.player.getActiveEffects());
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(left, top, left + width + PADDING_X * 2,
                top + minecraft.font.lineHeight + PADDING_Y * 2, BACKGROUND);
        graphics.drawString(minecraft.font, text, left + PADDING_X, top + PADDING_Y,
                colorFor(percent), false);
    }

    /* 有药水效果时把读数压到效果图标之下, 避免重叠 */
    private static int effectOffset(Collection<MobEffectInstance> effects) {
        if (effects.isEmpty()) {
            return 0;
        }
        return EFFECT_ROW * Math.min(effects.size(), MAX_EFFECT_ROWS);
    }

    /* 兵力越多越危险: 高占比红色, 低占比绿色 */
    private static int colorFor(int value) {
        if (value >= 75) {
            return 0xFFFF5555;
        }
        if (value >= 50) {
            return 0xFFFFAA33;
        }
        if (value >= 25) {
            return 0xFFFFFF55;
        }
        return 0xFF77FF77;
    }
}
