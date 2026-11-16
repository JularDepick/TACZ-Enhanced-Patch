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
 * 驾驶模式: 炮筒水平角跟随视角, W/S 调仰角, A/D 转动玩家朝向并带动水平角, Z/X 调口径, R 与空格发射, 蹲下退出。
 * 遥控模式: 视角不影响炮筒, A/D 调水平角, W/S 调仰角, Z/X 调口径, R 与空格发射, 并屏蔽移动输入。
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
    private static BlockPos infoPos;
    private static boolean remote;
    /* 每个键按住多久, 用于长按加速: 越久步进越密, 封顶为每 tick 一次 */
    private static final java.util.Map<Integer, Integer> HOLDS = new java.util.HashMap<>();

    private MortarClientHandler() {
    }

    @Nullable
    public static BlockPos active() {
        return active;
    }

    public static boolean remoteMode() {
        return remote;
    }

    /* 面板显示的目标: 正在控制的那台, 或蹲下右键打开的哪台 */
    @Nullable
    public static BlockPos displayPos() {
        return active != null ? active : infoPos;
    }

    public static void toggleInfo(BlockPos pos) {
        infoPos = pos.equals(infoPos) ? null : pos;
    }

    /* 遥控是开关: 再按一次同一台就退出, 否则玩家会被锁在原地无法移动 */
    public static void enterRemote(ItemStack stack) {
        BlockPos pos = MortarRemoteItem.boundPos(stack);
        if (pos == null) {
            return;
        }
        if (remote && pos.equals(active)) {
            active = null;
            remote = false;
            return;
        }
        active = pos;
        remote = true;
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
        /* 遥控模式蹲下即退出, 与驾驶模式一致 */
        if (remote && minecraft.player.isShiftKeyDown()) {
            active = null;
            remote = false;
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
        step(window, active, GLFW.GLFW_KEY_W, ACTION_PITCH, ANGLE_STEP);
        step(window, active, GLFW.GLFW_KEY_S, ACTION_PITCH, -ANGLE_STEP);
        if (driving) {
            /* 驾驶时水平角跟随视角: A/D 直接把玩家朝向一起转, 炮筒由每 tick 的绝对值同步跟上 */
            turnPlayer(window, minecraft.player, GLFW.GLFW_KEY_A, -ANGLE_STEP);
            turnPlayer(window, minecraft.player, GLFW.GLFW_KEY_D, ANGLE_STEP);
        } else {
            step(window, active, GLFW.GLFW_KEY_A, ACTION_YAW, -ANGLE_STEP);
            step(window, active, GLFW.GLFW_KEY_D, ACTION_YAW, ANGLE_STEP);
        }
        step(window, active, GLFW.GLFW_KEY_Z, ACTION_CALIBER, -CALIBER_STEP, false);
        step(window, active, GLFW.GLFW_KEY_X, ACTION_CALIBER, CALIBER_STEP, false);
        if (firePressed(window, GLFW.GLFW_KEY_R) || firePressed(window, GLFW.GLFW_KEY_SPACE)) {
            PatchNetwork.sendMortarControl(active, ACTION_FIRE, 0.0F);
        }
    }

    /* 遥控时屏蔽移动输入, 让 WASD 只用于调炮 */
    /* 蹲下右键迫击炮: 开关信息面板, 并拦下这次交互 */
    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.taczenhancedpatch.mortar.MortarBlock)) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            toggleInfo(event.getPos());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!remote) {
            return;
        }
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().jumping = false;
        /* 蹲下要留给退出遥控, 不能一起清零 */
    }

    /*
     * 长按加速。
     * 前十 tick 按原步进每四 tick 发一次, 之后步进本身放大十倍并每两 tick 一次, 二十五 tick 之后放大五十倍且每 tick 一次;
     * 角度调节走这套倍率, 口径调节只加快发送频率, 避免一次跳几十毫米。
     */
    private static void step(long window, BlockPos pos, int key, int action, float value) {
        step(window, pos, key, action, value, true);
    }

    private static void step(long window, BlockPos pos, int key, int action, float value, boolean accelerateStep) {
        boolean down = InputConstants.isKeyDown(window, key);
        int held = HOLDS.getOrDefault(key, 0);
        if (!down) {
            HOLDS.remove(key);
            return;
        }
        held++;
        HOLDS.put(key, held);
        int interval = held < 10 ? 4 : (held < 25 ? 2 : 1);
        if (held == 1 || held % interval == 0) {
            PatchNetwork.sendMortarControl(pos, action, accelerateStep ? value * multiplierFor(held) : value);
        }
    }

    /* 步进倍率: 长按后放大的是每一步的大小, 不只是发得更密 */
    private static float multiplierFor(int held) {
        if (held < 10) {
            return 1.0F;
        }
        return held < 25 ? 10.0F : 50.0F;
    }

    /* 按住 A/D 时按同样的加速节奏转动玩家朝向, 驾驶视角与炮筒一起转 */
    private static void turnPlayer(long window, net.minecraft.world.entity.player.Player player, int key, float delta) {
        boolean down = InputConstants.isKeyDown(window, key);
        int held = HOLDS.getOrDefault(key, 0);
        if (!down) {
            HOLDS.remove(key);
            return;
        }
        held++;
        HOLDS.put(key, held);
        int interval = held < 10 ? 4 : (held < 25 ? 2 : 1);
        if (held == 1 || held % interval == 0) {
            float scaled = delta * multiplierFor(held);
            player.setYRot(player.getYRot() + scaled);
            player.yRotO += scaled;
            player.setYHeadRot(player.getYRot());
        }
    }

    /* 开火只在按下沿发一次, 按住不会每 tick 都发包 */
    private static boolean firePressed(long window, int key) {
        boolean down = InputConstants.isKeyDown(window, key);
        int held = HOLDS.getOrDefault(key, 0);
        if (!down) {
            HOLDS.remove(key);
            return false;
        }
        HOLDS.put(key, held + 1);
        return held == 0;
    }
}
