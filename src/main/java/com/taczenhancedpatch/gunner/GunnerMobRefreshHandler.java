package com.taczenhancedpatch.gunner;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 世界加载时刷新持械生物列表。
 * 中间层只在世界开始时扫描一次注册表, 之后由配置界面的修改触发重扫。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GunnerMobRefreshHandler {
    private GunnerMobRefreshHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GunnerMobRegistry.refresh();
    }
}
