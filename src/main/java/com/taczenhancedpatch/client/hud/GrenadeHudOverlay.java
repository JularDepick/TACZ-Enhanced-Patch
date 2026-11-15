package com.taczenhancedpatch.client.hud;

import com.taczenhancedpatch.shell.ShellSettings;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.grenade.GrenadeClientHandler;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * 手雷面板。
 * 靠屏幕右侧显示, 避开顶部居中的方向标尺; 手持手雷时列出六项属性与对应按键,
 * 成对按键只写两个键, 单键只写一个键, 不再用斜杠分隔。
 * 蓄力过程不在这里显示, 改用拉弓姿态与视野收缩; 面板只在投出后保留这一颗的引信倒计时。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class GrenadeHudOverlay {
    private static final int MARGIN = 8;
    /* 顶部留给方向标尺与遗迹读数 */
    private static final int TOP = 44;
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;
    private static final int ROW_HEIGHT = 10;
    private static final int GAP = 8;
    private static final int BACKGROUND = 0x66000000;
    private static final int TITLE_COLOR = 0xFFE8D870;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFF7F7F7F;
    private static final int WARN_COLOR = 0xFFFF7070;

    private GrenadeHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        boolean holding = GrenadeClientHandler.isHoldingShell(minecraft.player);
        if (!holding) {
            /* 手雷已全部投出时仍保留这一颗的引信倒计时, 直到爆炸或计时结束 */
            String status = statusText();
            if (!status.isEmpty()) {
                GuiGraphics graphics = event.getGuiGraphics();
                int width = minecraft.font.width(status) + PADDING_X * 2;
                int left = minecraft.getWindow().getGuiScaledWidth() - MARGIN - width;
                graphics.fill(left, TOP, left + width, TOP + ROW_HEIGHT, BACKGROUND);
                graphics.drawString(minecraft.font, status, left + PADDING_X, TOP + 2, WARN_COLOR, false);
            }
            return;
        }
        ShellSettings settings = GrenadeClientHandler.settings();
        List<String[]> rows = rows(settings);
        int labelWidth = 0;
        int valueWidth = 0;
        int hintWidth = 0;
        for (String[] row : rows) {
            labelWidth = Math.max(labelWidth, minecraft.font.width(row[0]));
            valueWidth = Math.max(valueWidth, minecraft.font.width(row[1]));
            hintWidth = Math.max(hintWidth, minecraft.font.width(row[2]));
        }
        String title = UiText.plain("grenade.title");
        int contentWidth = Math.max(minecraft.font.width(title),
                labelWidth + GAP + valueWidth + GAP * 2 + hintWidth);
        int width = contentWidth + PADDING_X * 2;
        int height = PADDING_Y * 2 + ROW_HEIGHT + rows.size() * ROW_HEIGHT + ROW_HEIGHT;
        int left = minecraft.getWindow().getGuiScaledWidth() - MARGIN - width;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(left, TOP, left + width, TOP + height, BACKGROUND);
        graphics.drawString(minecraft.font, title, left + PADDING_X, TOP + PADDING_Y, TITLE_COLOR, false);
        int y = TOP + PADDING_Y + ROW_HEIGHT;
        for (String[] row : rows) {
            graphics.drawString(minecraft.font, row[0], left + PADDING_X, y, LABEL_COLOR, false);
            graphics.drawString(minecraft.font, row[1], left + PADDING_X + labelWidth + GAP, y, VALUE_COLOR, false);
            graphics.drawString(minecraft.font, row[2], left + width - PADDING_X - minecraft.font.width(row[2]),
                    y, HINT_COLOR, false);
            y += ROW_HEIGHT;
        }
        String status = statusText();
        if (!status.isEmpty()) {
            graphics.drawString(minecraft.font, status, left + PADDING_X, y, WARN_COLOR, false);
        }
    }

    /* 面板行序: 引信, 威力, 杀伤, 粘性, 特效, 颜色 */
    private static List<String[]> rows(ShellSettings settings) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{UiText.plain("grenade.label.fuse"),
                GrenadeClientHandler.fuseText(settings.fuseSeconds()).getString(), "- ="});
        rows.add(new String[]{UiText.plain("grenade.label.power"),
                String.valueOf(settings.power()), "[ ]"});
        rows.add(new String[]{UiText.plain("grenade.label.target"),
                UiText.plain(settings.target().labelKey()), "\\"});
        rows.add(new String[]{UiText.plain("grenade.label.sticky"),
                UiText.plain(settings.sticky() ? "grenade.on" : "grenade.off"), ";"});
        rows.add(new String[]{UiText.plain("grenade.label.effects"),
                UiText.plain(settings.effects() ? "grenade.on" : "grenade.off"), "'"});
        rows.add(new String[]{UiText.plain("grenade.label.color"),
                UiText.plain("grenade.color." + settings.color().getName()), ", ."});
        return rows;
    }

    private static String statusText() {
        int fuse = GrenadeClientHandler.fuseTicks();
        if (fuse < 0) {
            return "";
        }
        if (GrenadeClientHandler.impactFuse()) {
            return UiText.plain("grenade.fuse_impact");
        }
        return UiText.of("grenade.fuse_countdown", String.format(Locale.ROOT, "%.1f", fuse / 20.0D)).getString();
    }
}
