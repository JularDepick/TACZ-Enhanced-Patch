package com.taczenhancedpatch.client.hud;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.client.MortarClientHandler;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.mortar.MortarBlockEntity;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/*
 * 迫击炮面板。
 * 屏幕左侧显示炮筒水平角, 仰角, 口径, 以及按无阻力抛体算出的水平打击距离与飞行时长。
 * 只在驾驶或遥控该迫击炮时显示, 与右侧的炮弹属性面板互不影响。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class MortarHudOverlay {
    private static final int LEFT = 8;
    private static final int TOP = 44;
    private static final int LINE = 10;
    private static final int PADDING = 4;
    private static final int WIDTH = 132;
    private static final int BACKGROUND = 0x66000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TITLE = 0xFFE8D870;

    private MortarHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        BlockPos pos = MortarClientHandler.displayPos();
        Minecraft minecraft = Minecraft.getInstance();
        if (pos == null || minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(pos) instanceof MortarBlockEntity mortar)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(LEFT, TOP, LEFT + WIDTH, TOP + PADDING * 2 + LINE * 6, BACKGROUND);
        int y = TOP + PADDING;
        graphics.drawString(minecraft.font, UiText.plain("mortar.hud.title"), LEFT + PADDING, y, TITLE, false);
        y += LINE;
        double speed = mortar.muzzleSpeed();
        double pitch = Math.toRadians(mortar.pitchDeg());
        double gravity = ServerConfig.mortarGravity();
        double vy = speed * Math.sin(pitch);
        double vh = speed * Math.cos(pitch);
        double time = gravity <= 0.0D ? 0.0D : 2.0D * vy / gravity;
        double range = vh * time;
        graphics.drawString(minecraft.font, String.format(Locale.ROOT, "Yaw %.1f", mortar.yawDeg()),
                LEFT + PADDING, y, TEXT, false);
        y += LINE;
        graphics.drawString(minecraft.font, String.format(Locale.ROOT, "Pitch %.1f", mortar.pitchDeg()),
                LEFT + PADDING, y, TEXT, false);
        y += LINE;
        graphics.drawString(minecraft.font, String.format(Locale.ROOT, "Caliber %d mm", mortar.caliberMm()),
                LEFT + PADDING, y, TEXT, false);
        y += LINE;
        graphics.drawString(minecraft.font, String.format(Locale.ROOT, "Range %.0f", range),
                LEFT + PADDING, y, TEXT, false);
        y += LINE;
        graphics.drawString(minecraft.font, String.format(Locale.ROOT, "Time %.2f s", time),
                LEFT + PADDING, y, TEXT, false);
    }
}
