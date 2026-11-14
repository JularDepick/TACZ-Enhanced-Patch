package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import com.taczenhancedpatch.network.RuinNotice;
import com.taczenhancedpatch.network.RuinStatus;
import com.taczenhancedpatch.world.schematic.RuinPasteHandler;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 遗迹守军调度。
 * 每二十 tick 检查一次: 领地内兵力不足补兵触发比例时按固定间隔补一只,
 * 核心区有玩家且兵力低于夺旗触发比例时开始共享倒计时, 倒计时结束按兵力判定夺旗成败。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID)
public final class RuinGarrison {
    private static final int CHECK_INTERVAL = 20;
    /* 玩家进入该距离内才跑补兵与夺旗调度, 单位方块 */
    /* 活跃判定: 领地半径之外再加这么多格, 玩家在这段内也算附近 */
    private static final double ACTIVE_MARGIN = 32.0D;
    /* 坦克在补兵里的权重: 每四只补兵出一辆坦克 */
    private static final int TANK_ONE_IN = 4;
    /* 玩家离开核心区后的宽限时长, 超过即退出夺旗状态 */
    private static final int GRACE_TICKS = 100;
    /* 倒计时前若干秒用大标题, 之后改用顶部小提示 */
    private static final int TITLE_SECONDS = 5;
    /* 玩家当前所在的遗迹与核心区状态, 用于进出提示 */
    private static final Map<UUID, Presence> PRESENCE = new HashMap<>();
    private static int tick;

    /* 玩家所在遗迹与是否处于核心区 */
    private record Presence(BlockPos site, boolean inCore) {
    }

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
        data.drainPending(level);
        List<RuinSites.Site> sites = data.sites();
        if (sites.isEmpty()) {
            return;
        }
        int now = (int) level.getGameTime();
        /* 先算出每座活跃遗迹里的参与玩家, 用于判断谁留在这座, 谁已经转到别处 */
        Map<BlockPos, List<ServerPlayer>> insideBySite = new HashMap<>();
        for (RuinSites.Site site : sites) {
            if (!site.pasted() || RuinPasteHandler.isPasting(level, site.origin())) {
                continue;
            }
            /* 站点没有旗帜时补放一次, 老存档的遗迹因此重新参与领地与夺旗 */
            if (site.banner() == null) {
                int height = 8;
                RuinSchematic schematic = site.schematic().isEmpty() ? null : SchematicCatalog.load(site.schematic());
                if (schematic != null) {
                    height = schematic.height();
                }
                RuinBanner.place(level, site, site.origin(), site.baseY(), height, level.getRandom());
                data.setDirty();
                if (site.banner() == null) {
                    continue;
                }
            }
            /* 没有玩家在附近时整座遗迹休眠, 不做任何实体查询 */
            if (!hasPlayerNear(level, site)) {
                continue;
            }
            insideBySite.put(site.origin(), RuinTerritory.playersInside(level, site));
        }
        boolean playerInSomeSite = false;
        for (List<ServerPlayer> players : insideBySite.values()) {
            if (!players.isEmpty()) {
                playerInSomeSite = true;
                break;
            }
        }
        notifyPresence(level, sites, insideBySite);
        for (RuinSites.Site site : sites) {
            List<ServerPlayer> inside = insideBySite.get(site.origin());
            if (inside == null) {
                continue;
            }
            /* 夺旗前旗帜不可破坏, 任何非正常途径抹掉都在这里还原 */
            RuinBanner.restore(level, site);
            /* 玩家已经转到另一座遗迹时, 这座遗迹的旧战绩立即作废 */
            if (inside.isEmpty() && site.hadPlayers() && playerInSomeSite) {
                site.clearStats();
                site.setHadPlayers(false);
                data.setDirty();
            }
            /* 夺旗成功后战绩冻结, 供旗帜纪念品读取 */
            if (!site.captured()) {
                trackStats(level, site, inside, data);
            }
            /* 兵力只统计一次, 补兵, 夺旗与右上角读数共用, 避免同一 tick 反复扫描实体 */
            int count = RuinTerritory.countMobs(level, site);
            RuinStatus.send(inside, percentOf(site, count));
            if (site.captured()) {
                continue;
            }
            reinforce(level, site, count, now, data);
            capture(level, site, inside, count, now, data);
        }
    }

    /* 玩家是否在遗迹附近, 用于决定这座遗迹要不要跑调度 */
    private static boolean hasPlayerNear(ServerLevel level, RuinSites.Site site) {
        double active = site.radius() + ACTIVE_MARGIN;
        double limit = active * active;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(site.origin()) <= limit) {
                return true;
            }
        }
        return false;
    }

    /* 建筑落地后初始化: 领地半径, 四个随机参数与旗帜 */
    public static void onPasted(ServerLevel level, RuinSites.Site site, RuinSites data,
                                int width, int height, int depth, BlockPos min, int baseY) {
        RandomSource random = level.getRandom();
        site.setRadius(RuinTerritory.radiusFor(width, depth));
        /* 基数在生成时从配置区间里取一个值, 之后这座遗迹一直沿用它 */
        int[] baseRange = ServerConfig.ruinGarrisonBaseRange();
        site.setGarrisonBase(baseRange[0] + random.nextInt(baseRange[1] - baseRange[0] + 1));
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

    private static void reinforce(ServerLevel level, RuinSites.Site site, int count, int now, RuinSites data) {
        int base = baseOf(site);
        if (count >= base * site.reinforcePercent()) {
            return;
        }
        if (now < site.nextReinforceTick()) {
            return;
        }
        if (!spawnOne(level, site, level.getRandom())) {
            site.setNextReinforceTick(now + CHECK_INTERVAL);
            data.setDirty();
            return;
        }
        site.setNextReinforceTick(now + site.reinforceInterval() * 20);
        data.setDirty();
    }

    /*
     * 生成一只守军。
     * 位置按碰撞箱校验: 坦克两格半高, 只判空气格会让它卡在楼板与地面之间窒息,
     * 因此先在若干候选点上试放, 找到整箱无碰撞的位置再真正加入世界。
     */
    private static boolean spawnOne(ServerLevel level, RuinSites.Site site, RandomSource random) {
        EntityType<?> type = random.nextInt(TANK_ONE_IN) == 0
                ? TankRegistry.TANK_ELITE.get() : GruntRegistry.GUNNER_GRUNT.get();
        if (!(type.create(level) instanceof Mob mob)) {
            return false;
        }
        BlockPos pos = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos candidate = RuinTerritory.randomSpawnPos(level, site, random);
            if (candidate == null) {
                continue;
            }
            mob.moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            if (level.noCollision(mob, mob.getBoundingBox())) {
                pos = candidate;
                break;
            }
        }
        if (pos == null) {
            mob.discard();
            return false;
        }
        FactionData.setFaction(mob, Factions.ofColor(site.bannerColor()));
        FactionData.setHome(mob, site.origin());
        FactionData.setReviveDepth(mob, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(mob);
        return true;
    }

    private static void capture(ServerLevel level, RuinSites.Site site, List<ServerPlayer> inside,
                                int count, int now, RuinSites data) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return;
        }
        List<ServerPlayer> core = RuinTerritory.playersInCore(level, site);
        int base = baseOf(site);
        if (site.capturing()) {
            if (core.isEmpty()) {
                /* 核心区没人: 先进入宽限, 倒计时反向增加, 宽限耗尽才真正退出 */
                if (site.graceEndTick() == 0) {
                    site.setGraceEndTick(now + GRACE_TICKS);
                    tellNear(level, site, "tacz_enhanced_patch.ruin.capture_left_core");
                    data.setDirty();
                } else if (now >= site.graceEndTick()) {
                    site.setCaptureEndTick(0);
                    site.setGraceEndTick(0);
                    site.setTitleSecond(-1);
                    tellNear(level, site, "tacz_enhanced_patch.ruin.capture_abort");
                    RuinNotice.sendNear(level, Vec3.atCenterOf(banner),
                            site.radius() + RuinTerritory.PURSUIT_MARGIN, "hud.ruin.capture_abort", 0);
                    data.setDirty();
                    TACZEnhancedPatch.LOGGER.info("[ruin] capture aborted at {} after grace", site.origin());
                    return;
                }
                /* 反向计时: 每次检查把截止时刻往后推, 剩余时间随之增加 */
                site.setCaptureEndTick(site.captureEndTick() + CHECK_INTERVAL * 2);
                site.setTitleSecond(-1);
                int graceLeft = Math.max(1, (site.graceEndTick() - now) / 20 + 1);
                RuinNotice.sendNear(level, Vec3.atCenterOf(banner),
                        site.radius() + RuinTerritory.PURSUIT_MARGIN, "hud.ruin.capture_reversed", graceLeft);
                data.setDirty();
                return;
            }
            if (site.graceEndTick() != 0) {
                site.setGraceEndTick(0);
                tellNear(level, site, "tacz_enhanced_patch.ruin.capture_resumed");
                data.setDirty();
            }
            int remaining = site.captureEndTick() - now;
            if (remaining <= 0) {
                judge(level, site, base, count, inside, now, data);
                return;
            }
            showCountdown(core, site, remaining);
            return;
        }
        if (now < site.cooldownEndTick() || core.isEmpty()) {
            return;
        }
        if (count >= base * site.capturePercent()) {
            return;
        }
        site.setCaptureEndTick(now + site.captureSeconds() * 20);
        site.setGraceEndTick(0);
        site.setTitleSecond(-1);
        showTitle(core, site, site.captureSeconds());
        tell(inside, "tacz_enhanced_patch.ruin.capture_start", site.captureSeconds());
        data.setDirty();
        TACZEnhancedPatch.LOGGER.info("[ruin] capture started at {} count={} threshold={} seconds={}",
                site.origin(), count, base * site.capturePercent(), site.captureSeconds());
    }

    private static void judge(ServerLevel level, RuinSites.Site site, int base, int count,
                              List<ServerPlayer> inside, int now, RuinSites data) {
        site.setCaptureEndTick(0);
        site.setGraceEndTick(0);
        site.setTitleSecond(-1);
        if (count >= base) {
            site.setCooldownEndTick(now + ServerConfig.ruinCaptureCooldown() * 20);
            tell(inside, "tacz_enhanced_patch.ruin.capture_failed", count, base,
                    ServerConfig.ruinCaptureCooldown());
            RuinNotice.send(inside, "hud.ruin.capture_failed", count);
            data.setDirty();
            TACZEnhancedPatch.LOGGER.info("[ruin] capture failed at {} count={} base={}", site.origin(), count, base);
            return;
        }
        site.setCaptured(true);
        /* 势力瓦解: 剩余守军转为溃军, 阵营与归属保持不变 */
        RuinRout.rout(level, site);
        RuinLoot.placeChest(level, site, level.getRandom());
        RuinLoot.grantExperience(level, site);
        tell(inside, "tacz_enhanced_patch.ruin.capture_success");
        RuinNotice.send(inside, "hud.ruin.capture_success", 0);
        for (ServerPlayer player : inside) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("tacz_enhanced_patch.ruin.capture_title_success")));
        }
        data.setDirty();
        TACZEnhancedPatch.LOGGER.info("[ruin] captured at {} count={}", site.origin(), count);
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

    /* 倒计时前几秒用大标题, 之后改用顶部小提示, 避免长时间占据屏幕中央 */
    private static void showCountdown(List<ServerPlayer> core, RuinSites.Site site, int remainingTicks) {
        int seconds = Math.max(1, (remainingTicks + 19) / 20);
        if (site.titleSecond() == seconds) {
            return;
        }
        int elapsedTicks = site.captureSeconds() * 20 - remainingTicks;
        if (elapsedTicks < TITLE_SECONDS * 20) {
            /* 秒数由大标题自己记录, 这里不能抢先写入, 否则会被判等直接跳过 */
            showTitle(core, site, seconds);
        } else {
            site.setTitleSecond(seconds);
            RuinNotice.send(core, "hud.ruin.capture_remaining", seconds);
        }
    }

    /* 玩家进出遗迹与核心区的提示, 只在状态真正变化时发一次 */
    private static void notifyPresence(ServerLevel level, List<RuinSites.Site> sites,
                                       Map<BlockPos, List<ServerPlayer>> insideBySite) {
        Map<UUID, Presence> current = new HashMap<>();
        for (RuinSites.Site site : sites) {
            List<ServerPlayer> inside = insideBySite.get(site.origin());
            if (inside == null || inside.isEmpty()) {
                continue;
            }
            BlockPos banner = site.banner();
            for (ServerPlayer player : inside) {
                boolean inCore = banner != null
                        && RuinTerritory.inCore(banner, player.getX(), player.getY(), player.getZ());
                current.put(player.getUUID(), new Presence(site.origin(), inCore));
            }
        }
        Set<UUID> touched = new HashSet<>(current.keySet());
        touched.addAll(PRESENCE.keySet());
        for (UUID id : touched) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                PRESENCE.remove(id);
                continue;
            }
            Presence before = PRESENCE.get(id);
            Presence after = current.get(id);
            if (before == null && after == null) {
                continue;
            }
            if (before == null) {
                tell(player, "tacz_enhanced_patch.ruin.enter", after.site().getX(), after.site().getZ());
                if (after.inCore()) {
                    tell(player, "tacz_enhanced_patch.ruin.enter_core");
                }
            } else if (after == null) {
                tell(player, "tacz_enhanced_patch.ruin.leave");
                RuinStatus.clear(player);
            } else if (!before.site().equals(after.site())) {
                tell(player, "tacz_enhanced_patch.ruin.leave");
                RuinStatus.clear(player);
                tell(player, "tacz_enhanced_patch.ruin.enter", after.site().getX(), after.site().getZ());
                if (after.inCore()) {
                    tell(player, "tacz_enhanced_patch.ruin.enter_core");
                }
            } else if (before.inCore() != after.inCore()) {
                tell(player, after.inCore()
                        ? "tacz_enhanced_patch.ruin.enter_core"
                        : "tacz_enhanced_patch.ruin.leave_core");
            }
            if (after == null) {
                PRESENCE.remove(id);
            } else {
                PRESENCE.put(id, after);
            }
        }
    }

    /* 剩余兵力占基数的百分比, 上限 100 */
    private static int percentOf(RuinSites.Site site, int count) {
        int base = baseOf(site);
        if (base <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, Math.round(count * 100.0F / base)));
    }

    /* 站点自己的守军基数; 旧存档没有记录时取配置区间的中值 */
    private static int baseOf(RuinSites.Site site) {
        int base = site.garrisonBase();
        if (base > 0) {
            return base;
        }
        int[] range = ServerConfig.ruinGarrisonBaseRange();
        return (range[0] + range[1]) / 2;
    }

    /* 服务端停止时清空在线状态, 避免切换存档后残留 */
    public static void clearPresence() {
        PRESENCE.clear();
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    private static void tell(List<ServerPlayer> players, String key, Object... args) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.translatable(key, args));
        }
    }

    /* 按范围广播: 中断一类的提示要让刚走出核心区甚至走出领地的人也看到 */
    private static void tellNear(ServerLevel level, RuinSites.Site site, String key, Object... args) {
        BlockPos banner = site.banner();
        if (banner == null) {
            return;
        }
        double limit = site.radius() + RuinTerritory.PURSUIT_MARGIN;
        for (ServerPlayer player : level.players()) {
            if (!RuinTerritory.participates(player)) {
                continue;
            }
            if (RuinTerritory.horizontalDistance(banner, player.getX(), player.getZ()) <= limit) {
                player.sendSystemMessage(Component.translatable(key, args));
            }
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
