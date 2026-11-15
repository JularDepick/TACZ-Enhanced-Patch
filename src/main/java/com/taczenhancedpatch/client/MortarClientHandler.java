package com.taczenhancedpatch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.mortar.MortarBlockEntity;
import com.taczenhancedpatch.mortar.MortarRemoteItem;
import com.taczenhancedpatch.network.PatchNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/*
 * 迫击炮客户端控制。
 * 驾驶模式: 炮筒水平角跟随视角, W/S 调仰角, A/D 微调水平角, Q/E 调口径, R 与空格发射, 蹲下退出。
 * 遥控模式: 视角不影响炮筒, WASD 调角度, Q/E 调口径, R 与空格发射, 并屏蔽移动输入。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class MortarClientHandler {
    private static final float ANGLE_STEP = 0.1F;
    private static final int CALIBER_STEP = 5;
    private static final int ACTION_DRIVE_EXIT = 1;
    private static final int ACTION_YAW_ABS = 2;
    private static final int ACTION_PITCH = 3;
    private static final int ACTION_YAW = 4;
    private static final int ACTION_CALIBER = 5;
    private static final int ACTION_FIRE = 6;

    private static BlockPos active;
    private static boolean remote;

    private MortarClientHandler() {
    }

    @Nullable
    public static BlockPos active() {
        return active;
    }

    public static boolean remoteMode() {
        return remote;
    }

    public static void enterRemote(ItemStack stack) {
        BlockPos pos = MortarRemoteItem.boundPos(stack);
        if (pos != null) {
            active = pos;
            remote = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            active = null;
            remote = false;
            return;
        }
        boolean driving = minecraft.player.getVehicle() instanceof ArmorStand;
        if (driving && active == null) {
            active = minecraft.player.getVehicle().blockPosition();
            remote = false;
        }
        if (!driving && !remote) {
            active = null;
            return;
        }
        if (active == null) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(active) instanceof MortarBlockEntity)) {
            active = null;
            remote = false;
            return;
        }
        if (driving) {
            if (minecraft.player.isShiftKeyDown()) {
                PatchNetwork.sendMortarControl(active, ACTION_DRIVE_EXIT, 0.0F);
                active = null;
                return;
            }
            PatchNetwork.sendMortarControl(active, ACTION_YAW_ABS, minecraft.player.getYRot());
        }
        long window = minecraft.getWindow().getWindow();
        if (held(window, GLFW.GLFW_KEY_W)) {
            PatchNetwork.sendMortarControl(active, ACTION_PITCH, ANGLE_STEP);
        }
        if (held(window, GLFW.GLFW_KEY_S)) {
            PatchNetwork.sendMortarControl(active, ACTION_PITCH, -ANGLE_STEP);
        }
        if (held(window, GLFW.GLFW_KEY_A)) {
            PatchNetwork.sendMortarControl(active, ACTION_YAW, remote ? ANGLE_STEP : ANGLE_STEP);
        }
        if (held(window, GLFW.GLFW_KEY_D)) {
            PatchNetwork.sendMortarControl(active, ACTION_YAW, -ANGLE_STEP);
        }
        if (held(window, GLFW.GLFW_KEY_Q)) {
            PatchNetwork.sendMortarControl(active, ACTION_CALIBER, CALIBER_STEP);
        }
        if (held(window, GLFW.GLFW_KEY_E)) {
            PatchNetwork.sendMortarControl(active, ACTION_CALIBER, -CALIBER_STEP);
        }
        if (pressed(window, GLFW.GLFW_KEY_R) || pressed(window, GLFW.GLFW_KEY_SPACE)) {
            PatchNetwork.sendMortarControl(active, ACTION_FIRE, 0.0F);
        }
    }

    /* 遥控时屏蔽移动输入, 让 WASD 只用于调炮 */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!remote) {
            return;
        }
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    private static boolean held(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    private static boolean pressed(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
