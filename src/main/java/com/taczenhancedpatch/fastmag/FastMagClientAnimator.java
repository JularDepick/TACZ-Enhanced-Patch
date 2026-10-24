package com.taczenhancedpatch.fastmag;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 快速扩容弹匣的换弹动画加速。
 * 上游换弹动画是 Lua 动画片段, 播放长度固定; 这里在换弹期间按同一倍率额外推进
 * 当前播放中的换弹动画进度, 让动画与换弹状态同时提前结束, 观感与计时一致。
 * 只处理本地玩家手里的枪, 使用上游公开的动画控制器接口, 不注入任何 Mixin。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class FastMagClientAnimator {
    private static long lastTickNanos;

    private FastMagClientAnimator() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.nanoTime();
        long delta = lastTickNanos == 0L ? 0L : now - lastTickNanos;
        lastTickNanos = now;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || delta <= 0L) {
            return;
        }
        ItemStack gun = player.getMainHandItem();
        if (!TaczApi.isGun(gun) || !TaczApi.isReloading(player)) {
            return;
        }
        int level = FastMag.level(TaczApi.getExtendedMag(gun));
        if (level <= 0) {
            return;
        }
        double scale = FastMag.reloadScale(gun, level);
        if (scale >= 1.0D) {
            return;
        }
        long extra = Math.round(delta * (1.0D / scale - 1.0D));
        if (extra <= 0L) {
            return;
        }
        TimelessAPI.getGunDisplay(gun).ifPresent(display -> {
            var machine = display.getAnimationStateMachine();
            if (machine == null) {
                return;
            }
            var controller = machine.getAnimationController();
            for (int track : controller.getUpdatingTrackArray()) {
                var runner = controller.getAnimation(track);
                if (runner == null || runner.getAnimation() == null) {
                    continue;
                }
                String name = runner.getAnimation().name;
                if (name == null || !name.contains("reload")) {
                    continue;
                }
                runner.setProgressNs(runner.getProgressNs() + extra);
            }
        });
    }
}
