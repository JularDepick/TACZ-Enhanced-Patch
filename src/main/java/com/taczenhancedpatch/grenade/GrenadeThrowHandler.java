package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.item.PatchItems;
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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * 手雷的投掷与手持引信。
 * 客户端在按下右键时下发属性快照, 松开时下蓄力时长; 服务端据此生成投掷物并扣掉一个手雷。
 * 引信从按下右键开始算, 若引信先到而玩家还在蓄力, 手雷在手里直接爆炸。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class GrenadeThrowHandler {
    private static final Map<UUID, Press> PRESS = new HashMap<>();

    /* 按下右键时的属性快照与当时的游戏刻 */
    private record Press(GrenadeSettings settings, long tick) {
    }

    private GrenadeThrowHandler() {
    }

    public static void press(ServerPlayer player, GrenadeSettings settings) {
        PRESS.put(player.getUUID(), new Press(GrenadeSettings.sanitize(settings), player.level().getGameTime()));
    }

    public static void throwFrom(ServerPlayer player, int chargeTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = heldGrenade(player);
        if (stack.isEmpty()) {
            return;
        }
        Press press = PRESS.remove(player.getUUID());
        GrenadeSettings settings = press == null ? GrenadeSettings.DEFAULT : press.settings();
        int charge = Mth.clamp(chargeTicks, 0, GrenadeSettings.CHARGE_TICKS);
        double[] range = ServerConfig.grenadeSpeedRange();
        /* 配置单位是 格/秒, 原版投掷物用 格/tick, 按二十换算 */
        double speed = GrenadeSettings.speedFor(charge, range[0], range[1]) / 20.0D;
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(0.6D));
        /* 引信从按下右键开始计时, 蓄力用掉的秒数直接抵掉 */
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
        Press press = PRESS.get(player.getUUID());
        if (press == null) {
            return;
        }
        ItemStack using = player.getUseItem();
        if (!player.isUsingItem() || !(using.getItem() instanceof GrenadeItem)) {
            PRESS.remove(player.getUUID());
            return;
        }
        int fuse = press.settings().fuseSeconds();
        if (fuse < 1) {
            return;
        }
        if (player.level().getGameTime() - press.tick() < fuse * 20L) {
            return;
        }
        PRESS.remove(player.getUUID());
        /* 结束使用会把 useItem 置空, 但背包槽里仍是同一个对象, 因此先留引用再扣减 */
        player.stopUsingItem();
        consume(player, using);
        if (player.level() instanceof ServerLevel level) {
            GrenadeExplosion.detonate(level, player.getEyePosition(), press.settings(), player);
        }
    }

    @Nullable
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

    /* 服务端停止时清空按下记录, 避免切换存档后残留 */
    public static void clearAll() {
        PRESS.clear();
    }
}
