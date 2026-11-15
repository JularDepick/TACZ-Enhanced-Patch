package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.shell.ShellExplosion;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.shell.ShellSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * 手雷的投掷与手持引信。
 * 属性从手里这一叠物品读取, 客户端松开右键时只上报蓄力时长, 服务端据此生成投掷物并扣掉一个。
 * 引信从按下右键开始算, 若引信先到而玩家还在蓄力, 手雷在手里直接爆炸。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GrenadeThrowHandler {
    private GrenadeThrowHandler() {
    }

    public static void throwFrom(ServerPlayer player, int chargeTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = heldGrenade(player);
        if (stack.isEmpty()) {
            return;
        }
        ShellSettings settings = ((GrenadeItem) stack.getItem()).settingsOf(stack);
        int charge = Mth.clamp(chargeTicks, 0, ShellSettings.CHARGE_TICKS);
        double[] range = ServerConfig.grenadeSpeedRange();
        double speed = ShellSettings.speedFor(charge, range[0], range[1]) / 20.0D;
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(0.6D));
        int fuse = settings.impactFuse() ? -1 : Math.max(1, settings.fuseSeconds() * 20 - charge);
        GrenadeProjectile grenade = new GrenadeProjectile(level, start, direction.scale(speed), settings, player, fuse);
        level.addFreshEntity(grenade);
        consume(player, stack);
        player.stopUsingItem();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /* 手持引信到点: 就地爆炸并扣掉这一个 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof GrenadeItem)) {
            return;
        }
        ItemStack using = player.getUseItem();
        ShellSettings settings = ((GrenadeItem) using.getItem()).settingsOf(using);
        if (settings.fuseSeconds() < 1) {
            return;
        }
        int elapsed = using.getUseDuration() - player.getUseItemRemainingTicks();
        if (elapsed < settings.fuseSeconds() * 20) {
            return;
        }
        player.stopUsingItem();
        consume(player, using);
        if (player.level() instanceof ServerLevel level) {
            ShellExplosion.detonate(level, player.getEyePosition(), settings, player);
        }
    }

    private static ItemStack heldGrenade(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GrenadeItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof GrenadeItem ? off : ItemStack.EMPTY;
    }

    private static void consume(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild || stack.isEmpty()) {
            return;
        }
        stack.shrink(1);
        player.getInventory().setChanged();
    }
}
