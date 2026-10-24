package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 快速扩容弹夹的物品提示。
 * 只在客户端为带标记的物品追加一行换弹时长说明, 不改变上游原有的配件提示。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class FastMagTooltipHandler {
    private FastMagTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        int level = FastMag.level(event.getItemStack());
        if (level <= 0) {
            return;
        }
        event.getToolTip().add(FastMag.tooltip(level).copy().withStyle(ChatFormatting.GRAY));
    }
}
