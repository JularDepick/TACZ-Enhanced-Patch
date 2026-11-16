package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/*
 * 迫击炮遥控。
 * 右键迫击炮绑定或解绑, 一个遥控只记一台; 手持遥控右键非迫击炮方块或空气进入遥控控制。
 * 绑定关系记在物品数据里, 与迫击炮本身无关, 因此多台遥控可以绑同一台迫击炮。
 */
public class MortarRemoteItem extends Item {
    private static final String TAG = "Mortar";

    public MortarRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof MortarBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(TAG) && tag.getLong(TAG + "Pos") == pos.asLong()
                && tag.getString(TAG + "Dim").equals(level.dimension().location().toString())) {
            tag.remove(TAG + "Pos");
            tag.remove(TAG + "Dim");
            context.getPlayer().displayClientMessage(
                    Component.translatable("tacz_enhanced_patch.mortar.remote_unbound"), false);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.8F);
        } else {
            tag.putLong(TAG + "Pos", pos.asLong());
            tag.putString(TAG + "Dim", level.dimension().location().toString());
            context.getPlayer().displayClientMessage(
                    Component.translatable("tacz_enhanced_patch.mortar.remote_bound"), false);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.hasTag() || !stack.getTag().contains(TAG + "Pos")) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            com.taczenhancedpatch.client.MortarClientHandler.enterRemote(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    /* 玩家背包里是否有绑定这台迫击炮的遥控: 遥控控制包的鉴权依据 */
    public static boolean isBoundTo(Player player, BlockPos pos, net.minecraft.resources.ResourceKey<Level> dimension) {
        String dim = dimension.location().toString();
        for (ItemStack stack : player.getInventory().items) {
            if (boundTo(stack, pos, dim)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (boundTo(stack, pos, dim)) {
                return true;
            }
        }
        return false;
    }

    private static boolean boundTo(ItemStack stack, BlockPos pos, String dimension) {
        if (!(stack.getItem() instanceof MortarRemoteItem) || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag.contains(TAG + "Pos") && tag.getLong(TAG + "Pos") == pos.asLong()
                && dimension.equals(tag.getString(TAG + "Dim"));
    }

    /* 遥控绑定的坐标与维度 */
    @Nullable
    public static BlockPos boundPos(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG + "Pos")
                ? BlockPos.of(stack.getTag().getLong(TAG + "Pos")) : null;
    }

    @Nullable
    public static String boundDimension(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG + "Dim")
                ? stack.getTag().getString(TAG + "Dim") : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        BlockPos pos = boundPos(stack);
        if (pos == null) {
            lines.add(Component.translatable("tacz_enhanced_patch.mortar.remote_empty"));
            return;
        }
        lines.add(Component.translatable("tacz_enhanced_patch.mortar.remote_target",
                pos.getX(), pos.getY(), pos.getZ()));
    }
}
