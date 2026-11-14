package com.taczenhancedpatch.network;

import com.taczenhancedpatch.client.hud.RuinNoticeOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 遗迹小提示包。
 * 只携带语言键, 一个整数参数与显示时长, 由客户端在屏幕上方居中绘制一行小字;
 * 用于夺旗倒计时与遗迹事件, 避免长时间占用大标题或刷聊天栏。
 */
public record RuinNoticeMessage(String key, int value, int durationMillis) {
    private static final int MAX_KEY = 96;

    public static void encode(RuinNoticeMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.key(), MAX_KEY);
        buffer.writeVarInt(message.value());
        buffer.writeVarInt(message.durationMillis());
    }

    public static RuinNoticeMessage decode(FriendlyByteBuf buffer) {
        return new RuinNoticeMessage(buffer.readUtf(MAX_KEY), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(RuinNoticeMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RuinNoticeOverlay.accept(message.key(), message.value(), message.durationMillis())));
        context.setPacketHandled(true);
    }
}
