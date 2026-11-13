package com.taczenhancedpatch.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 遗迹站点登记。
 * 世界生成线程只把中心点, 选中的建筑与底面高度放进待处理队列, 由服务端 tick 收敛到存档数据;
 * 每个站点记录建筑, 旗帜, 领地半径, 生成时随机的四个参数, 夺旗状态与战斗统计。
 */
public final class RuinSites extends SavedData {
    private static final String DATA_NAME = "tacz_enhanced_patch_ruins";
    /* 队列上限: 防守军生成器失联时队列无界增长拖垮内存 */
    private static final int PENDING_LIMIT = 4096;
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private final List<Site> sites = new ArrayList<>();

    private record Pending(BlockPos origin, String schematic, int baseY) {
    }

    /* 单座遗迹的全部运行期状态, 可变, 由服务端 tick 与事件在服务端线程修改 */
    public static final class Site {
        private final BlockPos origin;
        private int radius = 28;
        private String schematic = "";
        private int baseY;
        private boolean pasted;
        private BlockPos banner;
        private String bannerColor = "";
        private float reinforcePercent = 0.5F;
        private int reinforceInterval = 20;
        private float capturePercent = 0.4F;
        private int captureSeconds = 60;
        private boolean captured;
        private int nextReinforceTick;
        private int captureEndTick;
        private int cooldownEndTick;
        private int titleSecond = -1;
        private boolean hadPlayers;
        private RuinStats stats;

        private Site(BlockPos origin) {
            this.origin = origin;
        }

        public BlockPos origin() {
            return origin;
        }

        public int radius() {
            return radius;
        }

        public void setRadius(int radius) {
            this.radius = Math.max(16, radius);
        }

        public String schematic() {
            return schematic;
        }

        /* 站点登记的建筑被弃用时换成另一座, 名字随存档一起保存 */
        public void setSchematic(String schematic) {
            this.schematic = schematic == null ? "" : schematic;
        }

        public int baseY() {
            return baseY;
        }

        public boolean pasted() {
            return pasted;
        }

        public void setPasted(boolean pasted) {
            this.pasted = pasted;
        }

        @Nullable
        public BlockPos banner() {
            return banner;
        }

        public void setBanner(@Nullable BlockPos banner, String color) {
            this.banner = banner;
            this.bannerColor = color == null ? "" : color;
        }

        public String bannerColor() {
            return bannerColor;
        }

        public float reinforcePercent() {
            return reinforcePercent;
        }

        public void setReinforce(float percent, int intervalSeconds) {
            this.reinforcePercent = percent;
            this.reinforceInterval = intervalSeconds;
        }

        public int reinforceInterval() {
            return reinforceInterval;
        }

        public float capturePercent() {
            return capturePercent;
        }

        public int captureSeconds() {
            return captureSeconds;
        }

        public void setCapture(float percent, int seconds) {
            this.capturePercent = percent;
            this.captureSeconds = seconds;
        }

        public boolean captured() {
            return captured;
        }

        public void setCaptured(boolean captured) {
            this.captured = captured;
        }

        public int nextReinforceTick() {
            return nextReinforceTick;
        }

        public void setNextReinforceTick(int tick) {
            this.nextReinforceTick = tick;
        }

        public int captureEndTick() {
            return captureEndTick;
        }

        public void setCaptureEndTick(int tick) {
            this.captureEndTick = tick;
        }

        public int cooldownEndTick() {
            return cooldownEndTick;
        }

        public void setCooldownEndTick(int tick) {
            this.cooldownEndTick = tick;
        }

        public int titleSecond() {
            return titleSecond;
        }

        public void setTitleSecond(int second) {
            this.titleSecond = second;
        }

        @Nullable
        public RuinStats stats() {
            return stats;
        }

        public RuinStats ensureStats() {
            if (stats == null) {
                stats = new RuinStats();
            }
            return stats;
        }

        public void clearStats() {
            stats = null;
        }

        public boolean capturing() {
            return captureEndTick > 0;
        }

        public boolean hadPlayers() {
            return hadPlayers;
        }

        public void setHadPlayers(boolean hadPlayers) {
            this.hadPlayers = hadPlayers;
        }
    }

    /* 世界生成线程调用, 只入队, 超过上限后丢弃新站点 */
    public static void queue(BlockPos origin, String schematic, int baseY) {
        if (PENDING.size() >= PENDING_LIMIT) {
            return;
        }
        PENDING.add(new Pending(origin.immutable(), schematic, baseY));
    }

    public static RuinSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RuinSites::load, RuinSites::new, DATA_NAME);
    }

    /* 按遗迹中心查找站点, 生物与统计都靠它定位归属 */
    @Nullable
    public static Site find(ServerLevel level, BlockPos origin) {
        for (Site site : get(level).sites()) {
            if (site.origin().equals(origin)) {
                return site;
            }
        }
        return null;
    }

    /* 把队列里的新站点并入存档数据 */
    public void drainPending() {
        Pending pending;
        boolean changed = false;
        while ((pending = PENDING.poll()) != null) {
            boolean known = false;
            for (Site site : sites) {
                if (site.origin().equals(pending.origin())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                Site site = new Site(pending.origin());
                site.schematic = pending.schematic();
                site.baseY = pending.baseY();
                sites.add(site);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public List<Site> sites() {
        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Site site : sites) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("origin", site.origin().asLong());
            entry.putInt("radius", site.radius());
            entry.putString("schematic", site.schematic());
            entry.putInt("baseY", site.baseY());
            entry.putBoolean("pasted", site.pasted());
            if (site.banner() != null) {
                entry.putLong("banner", site.banner().asLong());
            }
            entry.putString("bannerColor", site.bannerColor());
            entry.putFloat("reinforcePercent", site.reinforcePercent());
            entry.putInt("reinforceInterval", site.reinforceInterval());
            entry.putFloat("capturePercent", site.capturePercent());
            entry.putInt("captureSeconds", site.captureSeconds());
            entry.putBoolean("captured", site.captured());
            entry.putInt("nextReinforceTick", site.nextReinforceTick());
            entry.putInt("cooldownEndTick", site.cooldownEndTick());
            if (site.stats() != null) {
                entry.put("stats", site.stats().save(new CompoundTag()));
            }
            list.add(entry);
        }
        tag.put("sites", list);
        return tag;
    }

    private static RuinSites load(CompoundTag tag) {
        RuinSites data = new RuinSites();
        ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Site site = new Site(BlockPos.of(entry.getLong("origin")));
            site.radius = Math.max(16, entry.getInt("radius"));
            site.schematic = entry.getString("schematic");
            site.baseY = entry.getInt("baseY");
            /* 旧存档没有建筑字段, 直接视为已经落地, 避免回贴出多余的建筑 */
            site.pasted = entry.contains("pasted") ? entry.getBoolean("pasted") : true;
            site.banner = entry.contains("banner") ? BlockPos.of(entry.getLong("banner")) : null;
            site.bannerColor = entry.getString("bannerColor");
            site.reinforcePercent = entry.contains("reinforcePercent") ? entry.getFloat("reinforcePercent") : 0.5F;
            site.reinforceInterval = entry.contains("reinforceInterval") ? entry.getInt("reinforceInterval") : 20;
            site.capturePercent = entry.contains("capturePercent") ? entry.getFloat("capturePercent") : 0.4F;
            site.captureSeconds = entry.contains("captureSeconds") ? entry.getInt("captureSeconds") : 60;
            site.captured = entry.getBoolean("captured");
            site.nextReinforceTick = entry.getInt("nextReinforceTick");
            site.cooldownEndTick = entry.getInt("cooldownEndTick");
            if (entry.contains("stats", Tag.TAG_COMPOUND)) {
                site.stats = RuinStats.load(entry.getCompound("stats"));
            }
            data.sites.add(site);
        }
        return data;
    }
}
