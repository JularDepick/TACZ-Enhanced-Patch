package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自动换弹。
 * 只在本地玩家侧生效, 每 5 tick 检查一次主手枪械, 弹匣打空时发起一次换弹。
 * 上游模组自带的自动换弹与此开关互不影响, 关闭本开关即恢复原版操作。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class AutoReloadHandler {
    private static final int CHECK_INTERVAL = 5;

    private AutoReloadHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer player)) {
            return;
        }
        if (!ClientConfig.autoReload() || player.isSpectator() || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        ItemStack gun = player.getMainHandItem();
        if (!TaczApi.isGun(gun) || TaczApi.usesInventoryAmmo(gun)) {
            return;
        }
        if (TaczApi.magazineAmmo(gun) > 0 || TaczApi.hasBulletInBarrel(gun)) {
            return;
        }
        TaczApi.reload(player);
    }
}
