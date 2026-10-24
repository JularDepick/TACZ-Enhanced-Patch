package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.item.InfiniteMag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 无限弹匣的物品提示。
 * 只在客户端为带标记的物品追加容量与行为说明, 并移除上游按扩容等级生成的说明行,
 * 因为无限弹匣的容量已经不是有限档位, 保留会与正无穷的说明矛盾。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class InfiniteMagTooltipHandler {
    /* 上游扩容弹匣自带的说明行, 无限弹匣需要把它们换成容量正无穷 */
    private static final String[] UPSTREAM_KEYS = {
            "tooltip.tacz.attachment.extended_mag",
            "tooltip.tacz.attachment.extended_mag_level_1",
            "tooltip.tacz.attachment.extended_mag_level_2",
            "tooltip.tacz.attachment.extended_mag_level_3"
    };

    private InfiniteMagTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!InfiniteMag.isInfinite(event.getItemStack())) {
            return;
        }
        List<String> upstream = new ArrayList<>(UPSTREAM_KEYS.length);
        for (String key : UPSTREAM_KEYS) {
            upstream.add(Component.translatable(key).getString());
        }
        event.getToolTip().removeIf(line -> upstream.contains(line.getString()));
        event.getToolTip().add(Component.translatable(InfiniteMag.CAPACITY_KEY).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable(InfiniteMag.BEHAVIOR_KEY).withStyle(ChatFormatting.GRAY));
    }
}
