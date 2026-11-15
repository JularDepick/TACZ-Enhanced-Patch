package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.shell.ShellItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/*
 * 手雷物品。
 * 属性随物品数据走, 手持时用按键切换并写回这一叠; 右键蓄力, 松开投掷。
 * 蓄力沿用拉弓姿态, 视野同时收缩, 不占用屏幕空间。
 */
public class GrenadeItem extends ShellItem {
    public GrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        /* 投掷由客户端按键状态驱动, 这里只负责结束使用状态 */
    }
}
