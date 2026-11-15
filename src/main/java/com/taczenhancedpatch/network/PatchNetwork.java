package com.taczenhancedpatch.network;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;

/**
 * 模组网络通道。
 * 下行用于把枪击来源方向发给受击玩家, 上行用于坦克开火, 炮弹属性变更与投掷请求; 协议版本变化时递增即可。
 */
public final class PatchNetwork {
    /* 0.3.0 新增了炮弹属性与投掷两个上行包, 协议版本随之递增, 避免旧客户端握手通过后解码失败 */
    private static final String PROTOCOL = "3";

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
        CHANNEL.messageBuilder(ShellSettingsMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShellSettingsMessage::encode)
                .decoder(ShellSettingsMessage::decode)
                .consumerMainThread(ShellSettingsMessage::handle)
                .add();
        CHANNEL.messageBuilder(MortarControlMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MortarControlMessage::encode)
                .decoder(MortarControlMessage::decode)
                .consumerMainThread(MortarControlMessage::handle)
                .add();
        CHANNEL.messageBuilder(GrenadeThrowMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GrenadeThrowMessage::encode)
                .decoder(GrenadeThrowMessage::decode)
                .consumerMainThread(GrenadeThrowMessage::handle)
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

    /* 炮弹属性变更 */
    public static void sendShellSettings(ShellSettings settings) {
        CHANNEL.sendToServer(new ShellSettingsMessage(settings));
    }

    /* 手雷投掷请求, 只带蓄力时长 */
    public static void sendGrenadeThrow(int chargeTicks) {
        CHANNEL.sendToServer(new GrenadeThrowMessage(chargeTicks));
    }

    /* 迫击炮控制 */
    public static void sendMortarControl(net.minecraft.core.BlockPos pos, int action, float value) {
        CHANNEL.sendToServer(new MortarControlMessage(pos, action, value));
    }

    public static void sendHitDirection(ServerPlayer player, float sourceYaw) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HitDirectionMessage(sourceYaw));
    }
}
