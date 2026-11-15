package com.taczenhancedpatch.grenade;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/*
 * 手雷物品。
 * 全模组只有这一种手雷, 堆叠上限 64; 六项属性不存在物品数据里, 而是玩家偏好, 由客户端配置持有。
 * 右键开始蓄力, 松开投掷; 蓄力时长与属性快照都由客户端得出, 服务端只接收结果并模拟。
 */
public class GrenadeItem extends Item {
    public GrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /* 蓄力超过三秒不再累计, 使用时长给足即可, 松开才投出 */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
        /* 投掷由客户端按键状态驱动, 这里只负责结束使用状态 */
    }
}
