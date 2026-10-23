package com.taczenhancedpatch.network;

import com.taczenhancedpatch.hitindicator.HitIndicatorOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 受击方向同步包。
 * 只携带伤害来源在世界中的朝向, 由客户端结合当前镜头朝向换算成屏幕上的方向。
 */
public record HitDirectionMessage(float sourceYaw) {
    public static void encode(HitDirectionMessage message, FriendlyByteBuf buffer) {
        buffer.writeFloat(message.sourceYaw());
    }

    public static HitDirectionMessage decode(FriendlyByteBuf buffer) {
        return new HitDirectionMessage(buffer.readFloat());
    }

    public static void handle(HitDirectionMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HitIndicatorOverlay.accept(message.sourceYaw())));
        context.setPacketHandled(true);
    }
}
