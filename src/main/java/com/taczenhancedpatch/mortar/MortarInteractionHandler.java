package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * 迫击炮左键交互: 有炮弹时弹出, 空手时调角。
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
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof MortarBlock)) {
            return;
        }
        MortarBlock.attack(event.getLevel(), event.getPos(), event.getEntity());
        event.setCanceled(true);
    }
}
