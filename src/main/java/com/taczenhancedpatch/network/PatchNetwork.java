package com.taczenhancedpatch.network;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;

/**
 * 模组网络通道。
 * 下行用于把枪击来源方向发给受击玩家, 上行用于玩家驾驶坦克时的开火请求; 协议版本变化时递增即可。
 */
public final class PatchNetwork {
    /* 0.2.0 新增了遗迹提示与兵力读数两个下行包, 协议版本随之递增, 避免旧客户端握手通过后解码失败 */
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(Objects.requireNonNull(ResourceLocation.tryParse(TACZEnhancedPatch.MOD_ID + ":main")))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private PatchNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(HitDirectionMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HitDirectionMessage::encode)
                .decoder(HitDirectionMessage::decode)
                .consumerMainThread(HitDirectionMessage::handle)
                .add();
        CHANNEL.messageBuilder(RuinNoticeMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RuinNoticeMessage::encode)
                .decoder(RuinNoticeMessage::decode)
                .consumerMainThread(RuinNoticeMessage::handle)
                .add();
        CHANNEL.messageBuilder(RuinStatusMessage.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RuinStatusMessage::encode)
                .decoder(RuinStatusMessage::decode)
                .consumerMainThread(RuinStatusMessage::handle)
                .add();
        CHANNEL.messageBuilder(TankFireMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TankFireMessage::encode)
                .decoder(TankFireMessage::decode)
                .consumerMainThread(TankFireMessage::handle)
                .add();
    }

    public static void sendTankFire() {
        CHANNEL.sendToServer(new TankFireMessage());
    }

    public static void sendHitDirection(ServerPlayer player, float sourceYaw) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HitDirectionMessage(sourceYaw));
    }
}
