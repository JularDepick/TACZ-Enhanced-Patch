package com.taczenhancedpatch.network;

import com.taczenhancedpatch.mortar.MortarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/*
 * 迫击炮控制包。
 * 动作: 0 进入驾驶, 1 退出, 2 水平角跟随视角, 3 仰角步进, 4 水平角步进, 5 口径步进, 6 发射, 7 装填并发射。
 */
public record MortarControlMessage(BlockPos pos, int action, float value) {
    /* 操作距离上限的平方, 超出即忽略, 防止远程操纵别人的迫击炮 */
    private static final double MAX_DISTANCE_SQR = 64.0D;
    public static void encode(MortarControlMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos());
        buffer.writeVarInt(message.action());
        buffer.writeFloat(message.value());
    }

    public static MortarControlMessage decode(FriendlyByteBuf buffer) {
        return new MortarControlMessage(buffer.readBlockPos(), buffer.readVarInt(), buffer.readFloat());
    }

    public static void handle(MortarControlMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            BlockEntity entity = player.level().getBlockEntity(message.pos());
            if (entity instanceof MortarBlockEntity mortar
                    && player.distanceToSqr(Vec3.atCenterOf(message.pos())) <= MAX_DISTANCE_SQR
                    && (message.action() == 0 || player.getUUID().equals(mortar.controller()))) {
                switch (message.action()) {
                    case 0 -> mortar.mount(player);
                    case 1 -> {
                        if (player.getUUID().equals(mortar.controller())) {
                            mortar.dismount();
                        }
                    }
                    case 2 -> mortar.setYaw(message.value());
                    case 3 -> mortar.setPitch(mortar.pitchDeg() + message.value());
                    case 4 -> mortar.setYaw(mortar.yawDeg() + message.value());
                    case 5 -> mortar.setCaliber(mortar.caliberMm() + (int) message.value());
                    case 6 -> mortar.startFire();
                    default -> {
                    }
                }
            }
        }
        ctx.setPacketHandled(true);
    }
}
