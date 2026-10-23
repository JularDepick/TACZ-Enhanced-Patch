package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.integration.TaczAutoReload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自动换弹的本地开关。
 * 换弹动作由上游实现, 本类只负责在本机开关打开时把上游的开关一并打开,
 * 因此两个开关都能开启该功能, 关闭需要两边都关, 形成双控。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class AutoReloadHandler {
    private static final int CHECK_INTERVAL = 20;

    private AutoReloadHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        if (!ClientConfig.autoReload()) {
            return;
        }
        TaczAutoReload.enableUpstream();
    }
}
