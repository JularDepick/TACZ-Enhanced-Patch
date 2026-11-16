package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.util.UiText;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/*
 * 迫击炮方块物品。
 * 悬浮提示逐行列出装填, 发射, 驾驶与遥控的交互方式, 放下之前就能看明白怎么用;
 * 物品数据里带着这台迫击炮上一次的水平角, 仰角与口径, 挖掉再放下时接着用。
 */
public class MortarBlockItem extends BlockItem {
    /* 调校状态挂在物品数据的这个键上 */
    public static final String TAG_STATE = "MortarState";

    public MortarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.load"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.angle"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.drive"));
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.tooltip.remote"));
        CompoundTag state = stack.getTagElement(TAG_STATE);
        if (state != null) {
            lines.add(UiText.of("mortar.tooltip.state",
                    String.format(Locale.ROOT, "%.1f", state.getFloat("Yaw")),
                    String.format(Locale.ROOT, "%.1f", state.getFloat("Pitch")),
                    String.valueOf(state.getInt("Caliber"))));
        }
    }

    /* 放下时把物品里的调校状态写回方块实体 */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        CompoundTag tag = stack.getTagElement(TAG_STATE);
        if (!level.isClientSide() && tag != null && level.getBlockEntity(pos) instanceof MortarBlockEntity mortar) {
            mortar.readState(tag);
            return true;
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
