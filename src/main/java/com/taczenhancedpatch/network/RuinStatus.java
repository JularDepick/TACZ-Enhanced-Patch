package com.taczenhancedpatch.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

/**
 * 遗迹兵力状态的下发入口。
 */
public final class RuinStatus {
    private RuinStatus() {
    }

    public static void send(Collection<ServerPlayer> players, int percent) {
        if (players.isEmpty()) {
            return;
        }
        RuinStatusMessage message = new RuinStatusMessage(percent);
        for (ServerPlayer player : players) {
            PatchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    /* 离开遗迹时清掉右上角读数 */
    public static void clear(ServerPlayer player) {
        PatchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RuinStatusMessage(-1));
    }
}
