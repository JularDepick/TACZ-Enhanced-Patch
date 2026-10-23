package com.taczenhancedpatch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * 配置编辑权限判定。
 * 与服务端处于内存连接时, 说明本机就是存档创建者所在的服务端; 其余情况一律视为普通客户端。
 */
public final class ConfigAccess {
    private ConfigAccess() {
    }

    public static boolean isWorldOwner() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener != null && listener.getConnection().isMemoryConnection();
    }
}
