package com.taczenhancedpatch.network;

import com.taczenhancedpatch.grenade.GrenadeSettings;
import com.taczenhancedpatch.grenade.GrenadeTarget;
import com.taczenhancedpatch.grenade.GrenadeThrowHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/*
 * 手雷按下右键时的属性快照。
 * 服务端据此知道本机玩家选的是延时引信还是触发式, 用于手持引信到点就地爆炸的判定。
 */
public record GrenadePressMessage(GrenadeSettings settings) {
    public static void encode(GrenadePressMessage message, FriendlyByteBuf buffer) {
        GrenadeSettings settings = message.settings();
        buffer.writeVarInt(settings.fuseSeconds() + 1);
        buffer.writeDouble(settings.bounce());
        buffer.writeVarInt(settings.power());
        buffer.writeUtf(settings.target().id());
        buffer.writeUtf(settings.color().getName());
        buffer.writeBoolean(settings.effects());
    }

    public static GrenadePressMessage decode(FriendlyByteBuf buffer) {
        int fuse = buffer.readVarInt() - 1;
        double bounce = buffer.readDouble();
        int power = buffer.readVarInt();
        GrenadeTarget target = GrenadeTarget.byId(buffer.readUtf(16), GrenadeTarget.NO_MOB);
        DyeColor color = DyeColor.byName(buffer.readUtf(16), DyeColor.GREEN);
        boolean effects = buffer.readBoolean();
        return new GrenadePressMessage(GrenadeSettings.sanitize(
                new GrenadeSettings(fuse, bounce, power, target, color, effects)));
    }

    public static void handle(GrenadePressMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            GrenadeThrowHandler.press(player, message.settings());
        }
        ctx.setPacketHandled(true);
    }
}
