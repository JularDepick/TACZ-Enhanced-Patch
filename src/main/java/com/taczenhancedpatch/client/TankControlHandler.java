package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.TankElite;
import com.taczenhancedpatch.faction.FactionData;
import com.taczenhancedpatch.network.PatchNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家驾驶无阵营坦克的开火输入。
 * 骑在无阵营坦克上按住空格时每 tick 发送一次开火请求, 由服务端按冷却与状态决定是否真的开炮;
 * 移动与瞄准复用原版载具的同步链路, 不需要额外的移动包。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class TankControlHandler {
    private TankControlHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!(minecraft.player.getVehicle() instanceof TankElite tank)) {
            return;
        }
        if (!FactionData.factionOf(tank).isEmpty()) {
            return;
        }
        if (minecraft.options.keyJump.isDown()) {
            PatchNetwork.sendTankFire();
        }
    }
}
