package com.taczenhancedpatch.network;

import com.taczenhancedpatch.client.hud.RuinGarrisonOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 遗迹兵力包。
 * 服务端每 20 tick 把玩家所在遗迹的剩余兵力百分比下发一次, 取 -1 表示玩家已不在遗迹内, 客户端据此隐藏。
 */
public record RuinStatusMessage(int percent) {
    public static void encode(RuinStatusMessage message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.percent());
    }

    public static RuinStatusMessage decode(FriendlyByteBuf buffer) {
        return new RuinStatusMessage(buffer.readInt());
    }

    public static void handle(RuinStatusMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RuinGarrisonOverlay.accept(message.percent())));
        context.setPacketHandled(true);
    }
}
