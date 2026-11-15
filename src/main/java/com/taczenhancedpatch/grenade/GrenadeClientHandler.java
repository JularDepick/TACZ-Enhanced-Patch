package com.taczenhancedpatch.grenade;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.network.PatchNetwork;
import com.taczenhancedpatch.shell.ShellColor;
import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.shell.ShellSettings;
import com.taczenhancedpatch.shell.ShellTarget;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * 炮弹属性的客户端交互。
 * 手持任意炮弹时接管九个固定按键切换六项属性, 成对按键到边界即停, 颜色循环;
 * 按住不放会在短暂延迟后连续步进。修改直接写回手里这一叠物品并上报服务端。
 * 手雷额外支持按住右键蓄力与松开投掷, 蓄力中按左键取消。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class GrenadeClientHandler {
    private static final int[] KEYS = {
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
            GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD};
    /* 按键按住时长, 用于长按连续步进 */
    private static final Map<Integer, Integer> HOLD = new HashMap<>();
    private static final int REPEAT_DELAY = 12;
    private static final int REPEAT_INTERVAL = 2;
    /* 正在走引信的炮弹, 最新在前, 最多保留两颗 */
    private static final List<Fuse> FUSES = new ArrayList<>();
    private static final int FUSE_LIMIT = 2;
    private static boolean attackDown;
    private static int chargeTicks = -1;

    private GrenadeClientHandler() {
    }

    /* 手里这一叠的属性, 面板与投掷共用 */
    public static ShellSettings settings() {
        ItemStack stack = heldShell(Minecraft.getInstance().player);
        if (stack.isEmpty() || !(stack.getItem() instanceof ShellItem shell)) {
            return ShellSettings.DEFAULT;
        }
        return shell.settingsOf(stack);
    }

    public static ItemStack heldShell(@javax.annotation.Nullable Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (player.getMainHandItem().getItem() instanceof ShellItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof ShellItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    public static boolean isHoldingShell(Player player) {
        return !heldShell(player).isEmpty();
    }

    public static int chargeTicks() {
        return chargeTicks;
    }

    public static List<Fuse> fuses() {
        return FUSES;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        boolean holding = isHoldingShell(minecraft.player);
        if (!holding) {
            chargeTicks = -1;
        }
        pollKeys(minecraft, holding);
        /* 蓄力中按左键取消这次投掷 */
        if (pollAttack(minecraft) && chargeTicks >= 0) {
            chargeTicks = -1;
            if (!FUSES.isEmpty()) {
                FUSES.remove(0);
            }
            minecraft.player.stopUsingItem();
        }
        updateCharge(minecraft, holding);
        tickFuses();
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (chargeTicks < 0) {
            return;
        }
        float ratio = Math.min(1.0F, chargeTicks / (float) ShellSettings.CHARGE_TICKS);
        event.setNewFovModifier(event.getFovModifier() * (1.0F - 0.15F * ratio * ratio));
    }

    private static void pollKeys(Minecraft minecraft, boolean holding) {
        long window = minecraft.getWindow().getWindow();
        boolean canAct = holding && minecraft.screen == null;
        for (int key : KEYS) {
            if (!InputConstants.isKeyDown(window, key)) {
                HOLD.remove(key);
                continue;
            }
            int hold = HOLD.getOrDefault(key, 0);
            HOLD.put(key, hold + 1);
            boolean fire = hold == 0
                    || (hold >= REPEAT_DELAY && (hold - REPEAT_DELAY) % REPEAT_INTERVAL == 0);
            if (fire && canAct) {
                applyKey(minecraft, key);
            }
        }
    }

    private static void applyKey(Minecraft minecraft, int key) {
        ItemStack stack = heldShell(minecraft.player);
        if (stack.isEmpty() || !(stack.getItem() instanceof ShellItem shell)) {
            return;
        }
        ShellSettings current = shell.settingsOf(stack);
        ShellSettings next;
        String label;
        Component value;
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_EQUAL) {
            int fuse = ShellSettings.stepFuse(current.fuseSeconds(), key == GLFW.GLFW_KEY_EQUAL);
            next = new ShellSettings(fuse, current.sticky(), current.power(), current.target(),
                    current.color(), current.effects());
            label = "grenade.label.fuse";
            value = fuseText(fuse);
        } else if (key == GLFW.GLFW_KEY_LEFT_BRACKET || key == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            int power = ShellSettings.stepPower(current.power(), key == GLFW.GLFW_KEY_RIGHT_BRACKET);
            next = new ShellSettings(current.fuseSeconds(), current.sticky(), power, current.target(),
                    current.color(), current.effects());
            label = "grenade.label.power";
            value = Component.literal(String.valueOf(power));
        } else if (key == GLFW.GLFW_KEY_BACKSLASH) {
            ShellTarget target = current.target().next(true);
            next = new ShellSettings(current.fuseSeconds(), current.sticky(), current.power(), target,
                    current.color(), current.effects());
            label = "grenade.label.target";
            value = UiText.of(target.labelKey());
        } else if (key == GLFW.GLFW_KEY_SEMICOLON) {
            boolean sticky = !current.sticky();
            next = new ShellSettings(current.fuseSeconds(), sticky, current.power(), current.target(),
                    current.color(), current.effects());
            label = "grenade.label.sticky";
            value = UiText.of(sticky ? "grenade.on" : "grenade.off");
        } else if (key == GLFW.GLFW_KEY_APOSTROPHE) {
            boolean effects = !current.effects();
            next = new ShellSettings(current.fuseSeconds(), current.sticky(), current.power(), current.target(),
                    current.color(), effects);
            label = "grenade.label.effects";
            value = UiText.of(effects ? "grenade.on" : "grenade.off");
        } else if (key == GLFW.GLFW_KEY_COMMA || key == GLFW.GLFW_KEY_PERIOD) {
            ShellColor color = ShellSettings.stepColor(current.color(), key == GLFW.GLFW_KEY_PERIOD);
            next = new ShellSettings(current.fuseSeconds(), current.sticky(), current.power(), current.target(),
                    color, current.effects());
            label = "grenade.label.color";
            value = UiText.of(color.labelKey());
        } else {
            return;
        }
        shell.setSettings(stack, next);
        PatchNetwork.sendShellSettings(next);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("tacz_enhanced_patch.grenade.changed",
                    UiText.of(label), value), true);
        }
    }

    /* 只有手持手雷才蓄力投掷; 迫击炮炮弹不在这里投出 */
    private static void updateCharge(Minecraft minecraft, boolean holding) {
        if (minecraft.screen != null) {
            chargeTicks = -1;
            return;
        }
        ItemStack stack = heldShell(minecraft.player);
        boolean grenade = !stack.isEmpty() && stack.getItem() instanceof GrenadeItem;
        boolean useDown = minecraft.mouseHandler.isMouseGrabbed() && minecraft.options.keyUse.isDown();
        boolean using = minecraft.player != null && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof GrenadeItem;
        if (grenade && useDown && using) {
            if (chargeTicks < 0) {
                chargeTicks = 0;
                ShellSettings current = settings();
                pushFuse(current.impactFuse() ? -1 : current.fuseSeconds() * 20, current.impactFuse());
            } else {
                chargeTicks = Math.min(ShellSettings.CHARGE_TICKS, chargeTicks + 1);
            }
            return;
        }
        if (chargeTicks < 0) {
            return;
        }
        int charge = chargeTicks;
        chargeTicks = -1;
        if (!grenade) {
            return;
        }
        ShellSettings settings = settings();
        PatchNetwork.sendGrenadeThrow(charge);
        if (settings.impactFuse()) {
            /* 触发式没有倒计时, 投出后不再显示 */
            FUSES.removeIf(fuse -> fuse.impact);
        }
    }

    private static void pushFuse(int ticks, boolean impact) {
        FUSES.add(0, new Fuse(ticks, impact));
        while (FUSES.size() > FUSE_LIMIT) {
            FUSES.remove(FUSES.size() - 1);
        }
    }

    private static void tickFuses() {
        if (FUSES.isEmpty()) {
            return;
        }
        Iterator<Fuse> iterator = FUSES.iterator();
        while (iterator.hasNext()) {
            Fuse fuse = iterator.next();
            if (fuse.impact) {
                continue;
            }
            fuse.ticks--;
            if (fuse.ticks <= 0) {
                iterator.remove();
            }
        }
    }

    private static boolean pollAttack(Minecraft minecraft) {
        boolean down = minecraft.mouseHandler.isMouseGrabbed() && minecraft.options.keyAttack.isDown();
        boolean edge = down && !attackDown;
        attackDown = down;
        return edge;
    }

    public static Component fuseText(int fuseSeconds) {
        return fuseSeconds < 0
                ? UiText.of("shell.fuse_impact")
                : UiText.of("shell.fuse_value", fuseSeconds);
    }

    private static void reset() {
        chargeTicks = -1;
        HOLD.clear();
        FUSES.clear();
        attackDown = false;
    }

    /* 一颗正在走引信的炮弹, 倒计时单位 tick; 触发式没有倒计时, 只显示状态 */
    public static final class Fuse {
        private int ticks;
        private final boolean impact;

        private Fuse(int ticks, boolean impact) {
            this.ticks = ticks;
            this.impact = impact;
        }

        public int ticks() {
            return ticks;
        }

        public boolean impact() {
            return impact;
        }
    }
}
