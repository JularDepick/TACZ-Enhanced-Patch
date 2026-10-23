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
 * 目前只用于把枪击来源方向发给受击玩家, 协议版本变化时递增即可。
 */
public final class PatchNetwork {
    private static final String PROTOCOL = "1";

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
    }

    public static void sendHitDirection(ServerPlayer player, float sourceYaw) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HitDirectionMessage(sourceYaw));
    }
}
