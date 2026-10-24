package com.taczenhancedpatch.item;

import com.taczenhancedpatch.faction.RuinSites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 遗迹指南针。
 * 右键时在服务端查找当前维度最近的遗迹, 把坐标写进物品, 之后指针始终指向该遗迹;
 * 找到后给出距离提示, 没有遗迹时给出提示且保持空指针。
 */
public class RuinCompassItem extends Item {
    private static final String TAG_HAS = "RuinFound";
    private static final String TAG_X = "RuinX";
    private static final String TAG_Y = "RuinY";
    private static final String TAG_Z = "RuinZ";

    public RuinCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockPos target = nearestRuin(serverLevel, player.blockPosition());
            if (target == null) {
                player.displayClientMessage(Component.translatable("item.tacz_enhanced_patch.ruin_compass.none")
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putBoolean(TAG_HAS, true);
                tag.putInt(TAG_X, target.getX());
                tag.putInt(TAG_Y, target.getY());
                tag.putInt(TAG_Z, target.getZ());
                int distance = (int) Math.sqrt(target.distSqr(player.blockPosition()));
                player.displayClientMessage(Component.translatable("item.tacz_enhanced_patch.ruin_compass.found", distance)
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /* 物品里记录的遗迹中心, 没有记录时返回空 */
    @Nullable
    public static BlockPos target(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
    }

    /* 当前维度里距离玩家最近的遗迹中心 */
    @Nullable
    private static BlockPos nearestRuin(ServerLevel level, BlockPos from) {
        RuinSites sites = RuinSites.get(level);
        sites.drainPending();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RuinSites.Site site : sites.sites()) {
            double distance = site.origin().distSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = site.origin();
            }
        }
        return best;
    }
}