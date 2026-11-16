package com.taczenhancedpatch.network;

import com.taczenhancedpatch.mortar.MortarBlockEntity;
import com.taczenhancedpatch.mortar.MortarRemoteItem;
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
    /* 驾驶与手动操作的距离上限的平方, 超出即忽略 */
    private static final double MAX_DISTANCE_SQR = 64.0D;
    /* 遥控的距离上限的平方: 遥控就是隔着一段距离用, 但仍然有界 */
    private static final double REMOTE_DISTANCE_SQR = 128.0D * 128.0D;
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
            if (entity instanceof MortarBlockEntity mortar) {
                double distance = player.distanceToSqr(Vec3.atCenterOf(message.pos()));
                boolean near = distance <= MAX_DISTANCE_SQR;
                /* 遥控: 背包里带着绑定这台迫击炮的遥控, 且在遥控距离内 */
                boolean remote = MortarRemoteItem.isBoundTo(player, message.pos(), player.level().dimension())
                        && distance <= REMOTE_DISTANCE_SQR;
                /* 进入驾驶必须贴身; 其余动作由驾驶者贴身发起, 或由遥控发起 */
                boolean allowed = message.action() == 0
                        ? near
                        : remote || (near && player.getUUID().equals(mortar.controller()));
                if (!allowed) {
                    ctx.setPacketHandled(true);
                    return;
                }
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
                    case 6 -> mortar.startFire(player);
                    default -> {
                    }
                }
            }
        }
        ctx.setPacketHandled(true);
    }
}
