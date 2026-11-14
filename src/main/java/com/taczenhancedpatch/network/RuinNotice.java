package com.taczenhancedpatch.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

/**
 * 遗迹小提示的发送入口。
 * 服务端只负责挑选接收者, 文本与排版由客户端决定。
 */
public final class RuinNotice {
    /* 默认显示时长: 比一次下发周期略长, 避免每秒刷新时闪烁 */
    private static final int DEFAULT_MILLIS = 1600;

    private RuinNotice() {
    }

    public static void send(Collection<ServerPlayer> players, String key, int value) {
        if (players.isEmpty()) {
            return;
        }
        RuinNoticeMessage message = new RuinNoticeMessage(key, value, DEFAULT_MILLIS);
        for (ServerPlayer player : players) {
            PatchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    /* 按范围发送, 用于刚离开核心区或领地边缘的玩家 */
    public static void sendNear(ServerLevel level, Vec3 center, double radius, String key, int value) {
        double limit = radius * radius;
        RuinNoticeMessage message = new RuinNoticeMessage(key, value, DEFAULT_MILLIS);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= limit) {
                PatchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
            }
        }
    }
}
