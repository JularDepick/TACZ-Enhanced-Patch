package com.taczenhancedpatch.mortar;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/*
 * 迫击炮方块物品。
 * 悬浮提示逐行列出装填, 发射, 驾驶与遥控的交互方式, 放下之前就能看明白怎么用。
 */
public class MortarBlockItem extends BlockItem {
    public MortarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.load"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.angle"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.drive"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.remote"));
    }
}
