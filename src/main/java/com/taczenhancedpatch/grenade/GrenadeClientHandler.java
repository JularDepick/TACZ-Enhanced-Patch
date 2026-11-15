package com.taczenhancedpatch.grenade;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ClientConfig;
import com.taczenhancedpatch.network.PatchNetwork;
import com.taczenhancedpatch.util.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/*
 * 手雷的客户端交互。
 * 手持手雷时接管九个固定按键切换六项属性, 成对按键到边界即停, 单键为开关或循环。
 * 按住右键蓄力, 松开投掷; 蓄力时靠拉弓姿态与视野收缩给出反馈, 不再占用屏幕空间。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT)
public final class GrenadeClientHandler {
    private static final int[] KEYS = {
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
            GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD};
    private static final Map<Integer, Boolean> DOWN = new HashMap<>();
    /* 触发式没有倒计时, 面板上只留短暂提示 */
    private static final int IMPACT_HINT_TICKS = 40;

    private static int chargeTicks = -1;
    private static int fuseTicks = -1;
    private static boolean impactFuse;

    private GrenadeClientHandler() {
    }

    /* 当前偏好换算出的属性快照, 面板与投掷共用同一份 */
    public static GrenadeSettings settings() {
        return GrenadeSettings.sanitize(new GrenadeSettings(
                ClientConfig.grenadeFuse(),
                ClientConfig.grenadeSticky(),
                ClientConfig.grenadePower(),
                GrenadeTarget.byId(ClientConfig.grenadeTarget(), GrenadeTarget.NO_MOB),
                DyeColor.byName(ClientConfig.grenadeColor(), DyeColor.GREEN),
                ClientConfig.grenadeEffects()));
    }

    public static boolean isHoldingGrenade(Player player) {
        return player.getMainHandItem().getItem() instanceof GrenadeItem
                || player.getOffhandItem().getItem() instanceof GrenadeItem;
    }

    /* 已蓄力时长, 未蓄力时为负 */
    public static int chargeTicks() {
        return chargeTicks;
    }

    /* 最新一颗手雷的剩余引信, 无投掷时为负 */
    public static int fuseTicks() {
        return fuseTicks;
    }

    public static boolean impactFuse() {
        return impactFuse;
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
        boolean holding = isHoldingGrenade(minecraft.player);
        if (!holding) {
            chargeTicks = -1;
        }
        pollKeys(minecraft, holding);
        updateCharge(minecraft, holding);
        tickFuse();
    }

    /* 蓄力时像拉弓一样收缩视野, 松开后恢复 */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (chargeTicks < 0) {
            return;
        }
        float ratio = Math.min(1.0F, chargeTicks / (float) GrenadeSettings.CHARGE_TICKS);
        event.setNewFovModifier(event.getFovModifier() * (1.0F - 0.15F * ratio * ratio));
    }

    /* 九个按键按按下沿触发, 按住不重复切换; 打开界面时只更新状态不处理 */
    private static void pollKeys(Minecraft minecraft, boolean holding) {
        long window = minecraft.getWindow().getWindow();
        boolean canAct = holding && minecraft.screen == null;
        for (int key : KEYS) {
            boolean down = InputConstants.isKeyDown(window, key);
            boolean was = DOWN.getOrDefault(key, false);
            DOWN.put(key, down);
            if (down && !was && canAct) {
                applyKey(minecraft, key);
            }
        }
    }

    private static void applyKey(Minecraft minecraft, int key) {
        GrenadeSettings current = settings();
        GrenadeSettings next;
        String label;
        Component value;
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_EQUAL) {
            int fuse = GrenadeSettings.stepFuse(current.fuseSeconds(), key == GLFW.GLFW_KEY_EQUAL);
            next = new GrenadeSettings(fuse, current.sticky(), current.power(), current.target(),
                    current.color(), current.effects());
            label = "grenade.label.fuse";
            value = fuseText(fuse);
        } else if (key == GLFW.GLFW_KEY_LEFT_BRACKET || key == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            int power = GrenadeSettings.stepPower(current.power(), key == GLFW.GLFW_KEY_RIGHT_BRACKET);
            next = new GrenadeSettings(current.fuseSeconds(), current.sticky(), power, current.target(),
                    current.color(), current.effects());
            label = "grenade.label.power";
            value = Component.literal(String.valueOf(power));
        } else if (key == GLFW.GLFW_KEY_BACKSLASH) {
            GrenadeTarget target = current.target().next(true);
            next = new GrenadeSettings(current.fuseSeconds(), current.sticky(), current.power(), target,
                    current.color(), current.effects());
            label = "grenade.label.target";
            value = UiText.of(target.labelKey());
        } else if (key == GLFW.GLFW_KEY_SEMICOLON) {
            boolean sticky = !current.sticky();
            next = new GrenadeSettings(current.fuseSeconds(), sticky, current.power(), current.target(),
                    current.color(), current.effects());
            label = "grenade.label.sticky";
            value = UiText.of(sticky ? "grenade.on" : "grenade.off");
        } else if (key == GLFW.GLFW_KEY_APOSTROPHE) {
            boolean effects = !current.effects();
            next = new GrenadeSettings(current.fuseSeconds(), current.sticky(), current.power(), current.target(),
                    current.color(), effects);
            label = "grenade.label.effects";
            value = UiText.of(effects ? "grenade.on" : "grenade.off");
        } else if (key == GLFW.GLFW_KEY_COMMA || key == GLFW.GLFW_KEY_PERIOD) {
            DyeColor color = GrenadeSettings.stepColor(current.color(), key == GLFW.GLFW_KEY_PERIOD);
            next = new GrenadeSettings(current.fuseSeconds(), current.sticky(), current.power(), current.target(),
                    color, current.effects());
            label = "grenade.label.color";
            value = UiText.of("grenade.color." + color.getName());
        } else {
            return;
        }
        ClientConfig.setGrenade(next.fuseSeconds(), next.sticky(), next.power(),
                next.target().id(), next.color().getName(), next.effects());
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("tacz_enhanced_patch.grenade.changed",
                    UiText.of(label), value), true);
        }
    }

    /*
     * 按住右键蓄力, 松开投掷; 蓄力满三秒后不再增长, 也不会自动投出。
     * 只有真正进入使用物品状态才开始蓄力, 这样右键开门或开箱时不会顺带扔出一颗。
     */
    private static void updateCharge(Minecraft minecraft, boolean holding) {
        if (minecraft.screen != null) {
            chargeTicks = -1;
            return;
        }
        boolean useDown = minecraft.mouseHandler.isMouseGrabbed() && minecraft.options.keyUse.isDown();
        boolean using = minecraft.player != null && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof GrenadeItem;
        if (holding && useDown && using) {
            if (chargeTicks < 0) {
                chargeTicks = 0;
                PatchNetwork.sendGrenadePress(settings());
            } else {
                chargeTicks = Math.min(GrenadeSettings.CHARGE_TICKS, chargeTicks + 1);
            }
            return;
        }
        if (chargeTicks < 0) {
            return;
        }
        int charge = chargeTicks;
        chargeTicks = -1;
        GrenadeSettings settings = settings();
        PatchNetwork.sendGrenadeThrow(charge);
        impactFuse = settings.impactFuse();
        fuseTicks = impactFuse ? IMPACT_HINT_TICKS : Math.max(1, settings.fuseSeconds() * 20 - charge);
    }

    private static void tickFuse() {
        if (fuseTicks > 0) {
            fuseTicks--;
        } else if (fuseTicks == 0) {
            fuseTicks = -1;
            impactFuse = false;
        }
    }

    public static Component fuseText(int fuseSeconds) {
        return fuseSeconds < 0
                ? UiText.of("grenade.fuse_impact")
                : UiText.of("grenade.fuse_value", fuseSeconds);
    }

    private static void reset() {
        chargeTicks = -1;
        fuseTicks = -1;
        impactFuse = false;
        DOWN.clear();
    }
}
