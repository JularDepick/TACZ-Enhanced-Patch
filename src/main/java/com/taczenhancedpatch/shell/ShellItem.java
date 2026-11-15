package com.taczenhancedpatch.shell;

import com.taczenhancedpatch.util.UiText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/*
 * 炮弹物品基类。
 * 属性以物品数据的形式随这一叠物品走, 悬浮提示逐行列出, 因此同一种炮弹可以有不同属性。
 * 手雷与迫击炮炮弹都继承本类, 只在右键行为上分化。
 */
public abstract class ShellItem extends Item {
    private static final String TAG = "ShellSettings";

    protected ShellItem(Properties properties) {
        super(properties);
    }

    public ShellSettings settingsOf(ItemStack stack) {
        var tag = stack.getTagElement(TAG);
        return tag == null ? ShellSettings.DEFAULT : ShellSettings.fromTag(tag);
    }

    public void setSettings(ItemStack stack, ShellSettings settings) {
        stack.getOrCreateTag().put(TAG, ShellSettings.sanitize(settings).toTag());
    }

    /* 悬浮提示: 六项属性逐行列出, 玩家看这一叠就知道会怎么炸 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        ShellSettings settings = settingsOf(stack);
        lines.add(UiText.of("shell.tooltip.fuse", settings.impactFuse()
                ? UiText.of("shell.fuse_impact")
                : UiText.of("shell.fuse_value", settings.fuseSeconds())));
        lines.add(UiText.of("shell.tooltip.power", Component.literal(String.valueOf(settings.power()))));
        lines.add(UiText.of("shell.tooltip.target", UiText.of(settings.target().labelKey())));
        lines.add(UiText.of("shell.tooltip.sticky", UiText.of(settings.sticky() ? "shell.on" : "shell.off")));
        lines.add(UiText.of("shell.tooltip.effects", UiText.of(settings.effects() ? "shell.on" : "shell.off")));
        lines.add(UiText.of("shell.tooltip.color", UiText.of(settings.color().labelKey())));
        lines.add(UiText.of("shell.tooltip.mass", Component.literal(String.format(java.util.Locale.ROOT, "%.1f", ShellSettings.MASS_KG))));
    }
}
