package com.taczenhancedpatch.item;

import com.taczenhancedpatch.world.structure.RuinSiteIndexManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 遗迹指南针。
 * 右键时查一次遗迹选址索引, 索引还没覆盖到的方向由调度器向外扩展, 找到后把坐标写进物品;
 * 之后指针始终指向该遗迹, 提示里带距离, 坐标, 建筑名与生成状态。
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
        if (!level.isClientSide() && level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            RuinSiteIndexManager.query(serverPlayer, stack, RuinCompassItem::remember);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /* 把查到的遗迹写进物品 */
    public static void remember(ItemStack stack, BlockPos target) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_HAS, true);
        tag.putInt(TAG_X, target.getX());
        tag.putInt(TAG_Y, target.getY());
        tag.putInt(TAG_Z, target.getZ());
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
}
