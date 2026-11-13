package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 遗迹守军调度。
 * 每二十 tick 检查一次: 领地内兵力不足补兵触发比例时按固定间隔补一只,
 * 核心区有玩家且兵力低于夺旗触发比例时开始共享倒计时, 倒计时结束按兵力判定夺旗成败。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinGarrison {
    private static final int CHECK_INTERVAL = 20;
    /* 坦克在补兵里的权重: 每四只补兵出一辆坦克 */
    private static final int TANK_ONE_IN = 4;
    private static int tick;

    private RuinGarrison() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tick % CHECK_INTERVAL != 0) {
            return;
        }
        event.getServer().getAllLevels().forEach(RuinGarrison::tickLevel);
    }

    private static void tickLevel(ServerLevel level) {
        RuinSites data = RuinSites.get(level);
        data.drainPending();
        List<RuinSites.Site> sites = data.sites();
        if (sites.isEmpty()) {
            return;
        }
        int now = (int) level.getGameTime();
        for (RuinSites.Site site : sites) {
            if (!site.pasted() || site.banner() == null) {
                continue;
            }
            List<ServerPlayer> inside = RuinTerritory.playersInside(level, site);
            trackStats(level, site, inside, data);
            if (site.captured()) {
                continue;
            }
            reinforce(level, site, now, data);
            capture(level, site, inside, now, data);
        }
    }

    /* 建筑落地后初始化: 领地半径, 四个随机参数与旗帜 */
    public static void onPasted(ServerLevel level, RuinSites.Site site, RuinSites data,
                                int width, int height, int depth, BlockPos min, int baseY) {
        RandomSource random = level.getRandom();
        site.setRadius(RuinTerritory.radiusFor(width, depth));
        site.setReinforce(percentIn(ServerConfig.ruinReinforcePercentMin(), ServerConfig.ruinReinforcePercentMax(), random),
                randomIn(ServerConfig.ruinReinforceIntervalMin(), ServerConfig.ruinReinforceIntervalMax(), random));
        site.setCapture(percentIn(ServerConfig.ruinCapturePercentMin(), ServerConfig.ruinCapturePercentMax(), random),
                randomIn(ServerConfig.ruinCaptureTimeMin(), ServerConfig.ruinCaptureTimeMax(), random));
        BlockPos center = new BlockPos(min.getX() + width / 2, baseY, min.getZ() + depth / 2);
        RuinBanner.place(level, site, center, baseY, height, random);
        site.setPasted(true);
        site.setNextReinforceTick((int) level.getGameTime() + CHECK_INTERVAL);
        data.setDirty();
    }

    private static void reinforce(ServerLevel level, RuinSites.Site site, int now, RuinSites data) {
        int base = ServerConfig.ruinGarrisonBase();
        int count = RuinTerritory.countMobs(level, site);
        if (count >= base * site.reinforcePercent()) {
            return;
        }
        if (now < site.nextReinforceTick()) {
            return;
        }
        BlockPos pos = RuinTerritory.randomSpawnPos(level, site, level.getRandom());
        if (pos == null) {
            site.setNextReinforceTick(now + CHECK_INTERVAL);
            return;
        }
        spawnOne(level, site, pos);
        site.setNextReinforceTick(now + site.reinforceInterval() * 20);
        data.setDirty();
    }

    private static void spawnOne(ServerLevel level, RuinSites.Site site, BlockPos pos) {
        RandomSource random = level.getRandom();
        EntityType<?> type = random.nextInt(TANK_ONE_IN) == 0
                ? TankRegistry.TANK_ELITE.get() : GruntRegistry.GUNNER_GRUNT.get();
        if (!(type.create(level) instanceof Mob mob)) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        FactionData.setFaction(mob, Factions.ofColor(site.bannerColor()));
        FactionData.setHome(mob, site.origin());
        FactionData.setReviveDepth(mob, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(mob);
    }

    private static void capture(ServerLevel level, RuinSites.Site site, List<ServerPlayer> inside,
                                int now, RuinSites data) {
        List<ServerPlayer> core = RuinTerritory.playersInCore(level, site);
        int base = ServerConfig.ruinGarrisonBase();
        if (site.capturing()) {
            if (core.isEmpty()) {
                site.setCaptureEndTick(0);
                site.setTitleSecond(-1);
                tell(inside, "tacz_enhanced_patch.ruin.capture_abort");
                data.setDirty();
                return;
            }
            int remaining = site.captureEndTick() - now;
            if (remaining <= 0) {
                judge(level, site, base, inside, now, data);
                return;
            }
            showTitle(core, site, remaining / 20);
            return;
        }
        if (now < site.cooldownEndTick() || core.isEmpty()) {
            return;
        }
        int count = RuinTerritory.countMobs(level, site);
        if (count >= base * site.capturePercent()) {
            return;
        }
        site.setCaptureEndTick(now + site.captureSeconds() * 20);
        site.setTitleSecond(-1);
        showTitle(core, site, site.captureSeconds());
        tell(inside, "tacz_enhanced_patch.ruin.capture_start");
        data.setDirty();
    }

    private static void judge(ServerLevel level, RuinSites.Site site, int base, List<ServerPlayer> inside,
                              int now, RuinSites data) {
        int count = RuinTerritory.countMobs(level, site);
        site.setCaptureEndTick(0);
        site.setTitleSecond(-1);
        if (count >= base) {
            site.setCooldownEndTick(now + ServerConfig.ruinCaptureCooldown() * 20);
            tell(inside, "tacz_enhanced_patch.ruin.capture_failed");
            data.setDirty();
            return;
        }
        site.setCaptured(true);
        /* 势力瓦解: 剩余守军转为溃军, 阵营与归属保持不变 */
        RuinRout.rout(level, site);
        RuinLoot.placeChest(level, site, level.getRandom());
        RuinLoot.grantExperience(level, site);
        tell(inside, "tacz_enhanced_patch.ruin.capture_success");
        for (ServerPlayer player : inside) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("tacz_enhanced_patch.ruin.capture_title_success")));
        }
        data.setDirty();
        TACZEnhancedPatch.LOGGER.info("[ruin] captured at {}", site.origin());
    }

    private static void showTitle(List<ServerPlayer> core, RuinSites.Site site, int seconds) {
        if (site.titleSecond() == seconds) {
            return;
        }
        site.setTitleSecond(seconds);
        Component title = Component.translatable("tacz_enhanced_patch.ruin.capture_title");
        Component subtitle = Component.translatable("tacz_enhanced_patch.ruin.capture_subtitle", seconds);
        for (ServerPlayer player : core) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void tell(List<ServerPlayer> players, String key) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.translatable(key));
        }
    }

    /* 统计: 有玩家时开始并保持, 玩家全部离开后计时, 超时清空 */
    private static void trackStats(ServerLevel level, RuinSites.Site site, List<ServerPlayer> inside, RuinSites data) {
        RuinStats stats = site.stats();
        if (stats == null) {
            if (inside.isEmpty()) {
                site.setHadPlayers(false);
                return;
            }
            RuinStats fresh = site.ensureStats();
            fresh.beginSnapshot(RuinCombatStats.snapshotWeaponValue(level, site),
                    RuinCombatStats.snapshotAmmo(level, site));
            site.setHadPlayers(true);
            data.setDirty();
            return;
        }
        if (!inside.isEmpty()) {
            stats.resetIdle();
            site.setHadPlayers(true);
            return;
        }
        if (site.hadPlayers()) {
            site.setHadPlayers(false);
            int timeout = ServerConfig.ruinStatsIdleTimeout();
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(site.origin().getX(), player.getY(), site.origin().getZ()) < 96.0D * 96.0D) {
                    player.sendSystemMessage(Component.translatable(
                            "tacz_enhanced_patch.ruin.stats_expiring", timeout));
                }
            }
            data.setDirty();
        }
        stats.tickIdle();
        if (stats.idleTicks() >= ServerConfig.ruinStatsIdleTimeout()) {
            site.clearStats();
            data.setDirty();
        }
    }

    private static float percentIn(int min, int max, RandomSource random) {
        int low = Math.max(1, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        return (low + random.nextInt(high - low + 1)) / 100.0F;
    }

    private static int randomIn(int min, int max, RandomSource random) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + random.nextInt(high - low + 1);
    }
}
