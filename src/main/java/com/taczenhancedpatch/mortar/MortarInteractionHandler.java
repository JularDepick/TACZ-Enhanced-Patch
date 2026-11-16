package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * 迫击炮左键交互: 有炮弹时弹出, 空手时调角。
 * 手持镐子一类能挖这个方块的工具时不抢左键, 交给原版挖掘, 长按也不会被交互打断。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class MortarInteractionHandler {
    private MortarInteractionHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof MortarBlock)) {
            return;
        }
        ItemStack held = event.getEntity().getMainHandItem();
        /* 能挖这个方块的镐子类工具走挖掘, 只有空手与无关物品才交给迫击炮调角 */
        if (held.getItem() instanceof DiggerItem || held.isCorrectToolForDrops(state)) {
            return;
        }
        /* 只有真正被本方块消费的点击才取消, 否则迫击炮就无法被工具挖掉 */
        if (MortarBlock.attack(event.getLevel(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
