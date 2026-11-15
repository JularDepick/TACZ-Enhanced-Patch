package com.taczenhancedpatch.network;

import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.shell.ShellSettings;
import com.taczenhancedpatch.shell.ShellTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/*
 * 炮弹属性同步包。
 * 客户端按键改完手里这一叠的属性后上报, 服务端校验手持物品并把属性写回物品数据。
 */
public record ShellSettingsMessage(ShellSettings settings) {
    public static void encode(ShellSettingsMessage message, FriendlyByteBuf buffer) {
        ShellSettings settings = message.settings();
        buffer.writeVarInt(settings.fuseSeconds() + 1);
        buffer.writeBoolean(settings.sticky());
        buffer.writeVarInt(settings.power());
        buffer.writeUtf(settings.target().id());
        buffer.writeUtf(settings.color().getName());
        buffer.writeBoolean(settings.effects());
    }

    public static ShellSettingsMessage decode(FriendlyByteBuf buffer) {
        int fuse = buffer.readVarInt() - 1;
        boolean sticky = buffer.readBoolean();
        int power = buffer.readVarInt();
        ShellTarget target = ShellTarget.byId(buffer.readUtf(16), ShellTarget.NO_MOB);
        DyeColor color = DyeColor.byName(buffer.readUtf(16), DyeColor.GREEN);
        boolean effects = buffer.readBoolean();
        return new ShellSettingsMessage(ShellSettings.sanitize(
                new ShellSettings(fuse, sticky, power, target, color, effects)));
    }

    public static void handle(ShellSettingsMessage message, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            ItemStack target = main.getItem() instanceof ShellItem ? main
                    : (off.getItem() instanceof ShellItem ? off : ItemStack.EMPTY);
            if (!target.isEmpty()) {
                ((ShellItem) target.getItem()).setSettings(target, message.settings());
                player.getInventory().setChanged();
            }
        }
        ctx.setPacketHandled(true);
    }
}
