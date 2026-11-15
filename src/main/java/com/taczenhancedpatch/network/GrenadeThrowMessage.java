package com.taczenhancedpatch.network;

import com.taczenhancedpatch.grenade.GrenadeThrowHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/*
 * 手雷投掷请求。
 * 只带蓄力时长: 属性快照已在按下右键时下发, 服务端据此计算初速度与剩余引信。
 */
public record GrenadeThrowMessage(int chargeTicks) {
    public static void encode(GrenadeThrowMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.chargeTicks());
    }

    public static GrenadeThrowMessage decode(FriendlyByteBuf buffer) {
        return new GrenadeThrowMessage(buffer.readVarInt());
    }

    public static void handle(GrenadeThrowMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            GrenadeThrowHandler.throwFrom(player, message.chargeTicks());
        }
        ctx.setPacketHandled(true);
    }
}
