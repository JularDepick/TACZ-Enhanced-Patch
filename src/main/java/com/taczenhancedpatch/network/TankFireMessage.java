package com.taczenhancedpatch.network;

import com.taczenhancedpatch.entity.TankElite;
import com.taczenhancedpatch.faction.FactionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 玩家驾驶无阵营坦克时的开火请求。
 * 客户端按住空格时发送, 服务端确认玩家确实坐在无阵营坦克上才执行开炮;
 * 冷却与弹药由坦克自己维护, 客户端不做任何判定。
 */
public record TankFireMessage() {
    public static void encode(TankFireMessage message, FriendlyByteBuf buffer) {
    }

    public static TankFireMessage decode(FriendlyByteBuf buffer) {
        return new TankFireMessage();
    }

    public static void handle(TankFireMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null && player.getVehicle() instanceof TankElite tank
                && !tank.level().isClientSide() && FactionData.factionOf(tank).isEmpty()) {
            tank.tryFire(player);
        }
        ctx.setPacketHandled(true);
    }
}
