package com.taczenhancedpatch.faction;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import com.taczenhancedpatch.world.structure.RuinBannerPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 遗迹站点登记。
 * 站点不再由区块加载事件创建: 世界生成只写方块, 站点在玩家靠近时由 RuinSiteDiscovery 幂等物化;
 * 领地半径, 守军基数, 补兵与夺旗参数, 旗帜位置都由建筑名与片段种子确定性推导, 崩溃后重建得到同一结果;
 * 每个站点记录建筑, 旗帜, 领地半径, 生成时随机的四个参数, 夺旗状态与战斗统计。
 */
public final class RuinSites extends SavedData {
    private static final String DATA_NAME = "tacz_enhanced_patch_ruins";
    /* 随机参数抽取的盐值, 与旗帜颜色的盐值分开, 改动其一不影响另一 */
    private static final long SALT_GARRISON = 0x2545F4914F6CDD1DL;
    private final List<Site> sites = new ArrayList<>();
    /* 两个 O(1) 索引: 按中心点查归属, 按旗帜位置查站点, 供战斗与破坏事件的热路径使用 */
    private final Map<Long, Site> byOrigin = new HashMap<>();
    private final Map<Long, Site> byBanner = new HashMap<>();

    /* 单座遗迹的全部运行期状态, 可变, 由服务端 tick 与事件在服务端线程修改 */
    public static final class Site {
        private final BlockPos origin;
        private int radius = 28;
        private String schematic = "";
        private int baseY;
        /* 生成时的片段种子与建筑尺寸: 领地半径与旗帜位置的重算依据, 老存档没有时为零 */
        private long seed;
        private int width;
        private int height;
        private int depth;
        private boolean pasted;
        /* 老存档补旗只尝试一次, 失败也不再每二十 tick 重试 */
        private boolean bannerRepaired;
        /* 上一次下发的兵力读数与人数, 用来避免每二十 tick 重复发同一条包 */
        private int statusPercent = -1;
        private int statusInside = -1;
        /* 落盘缓存: 只有改动过的站点才重新拼 NBT, 其余直接复用上次的结果 */
        private transient CompoundTag cached;
        private transient boolean dirty = true;
        private BlockPos banner;
        private String bannerColor = "";
        private float reinforcePercent = 0.5F;
        private int reinforceInterval = 20;
        private float capturePercent = 0.4F;
        private int captureSeconds = 60;
        /* 生成时随机确定的守军基数, 0 表示旧存档没有记录 */
        private int garrisonBase;
        /* 物资箱是否已经散布过: 崩溃后重贴时不重复投放 */
        private boolean suppliesPlaced;
        /* 蓝图全部不可用时放弃本座遗迹, 不再补兵与补旗 */
        private boolean abandoned;
        private boolean captured;
        /* 计时一律用 long 存游戏时间, 长期存档的 tick 数会超出 int 范围 */
        private long nextReinforceTick;
        private long captureEndTick;
        private long cooldownEndTick;
        /* 离开核心区后的宽限截止时刻, 0 表示不在宽限中 */
        private long graceEndTick;
        /* 最近一次已提示的剩余秒数, 避免同一秒重复下发 */
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
            markDirty();
            this.radius = Math.max(16, radius);
        }

        public String schematic() {
            return schematic;
        }

        /* 站点登记的建筑被弃用时换成另一座, 名字随存档一起保存 */
        public void setSchematic(String schematic) {
            markDirty();
            this.schematic = schematic == null ? "" : schematic;
        }

        public int baseY() {
            return baseY;
        }

        public long seed() {
            return seed;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int depth() {
            return depth;
        }

        public boolean bannerRepaired() {
            return bannerRepaired;
        }

        public void setBannerRepaired(boolean value) {
            this.bannerRepaired = value;
        }

        public int statusPercent() {
            return statusPercent;
        }

        public int statusInside() {
            return statusInside;
        }

        /* 兵力读数与人数一起记, 两者任一变化才需要重发 */
        public void setStatus(int percent, int inside) {
            this.statusPercent = percent;
            this.statusInside = inside;
        }

        /* 任何会影响落盘的改动都要调这里, 否则本站点会一直复用旧的 NBT */
        public void markDirty() {
            this.dirty = true;
        }

        private CompoundTag serialized() {
            if (this.cached == null || this.dirty) {
                this.cached = writeSite(this);
                this.dirty = false;
            }
            return this.cached;
        }

        public boolean pasted() {
            return pasted;
        }

        public void setPasted(boolean pasted) {
            markDirty();
            this.pasted = pasted;
        }

        @Nullable
        public BlockPos banner() {
            return banner;
        }

        public void setBanner(@Nullable BlockPos banner, String color) {
            markDirty();
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
            markDirty();
            this.reinforcePercent = percent;
            this.reinforceInterval = intervalSeconds;
        }

        public int reinforceInterval() {
            return reinforceInterval;
        }

        public int garrisonBase() {
            return garrisonBase;
        }

        public boolean abandoned() {
            return abandoned;
        }

        public void setAbandoned(boolean value) {
            markDirty();
            this.abandoned = value;
        }

        public boolean suppliesPlaced() {
            return suppliesPlaced;
        }

        public void setSuppliesPlaced(boolean placed) {
            markDirty();
            this.suppliesPlaced = placed;
        }

        public void setGarrisonBase(int base) {
            markDirty();
            this.garrisonBase = base;
        }

        public float capturePercent() {
            return capturePercent;
        }

        public int captureSeconds() {
            return captureSeconds;
        }

        public void setCapture(float percent, int seconds) {
            markDirty();
            this.capturePercent = percent;
            this.captureSeconds = seconds;
        }

        public boolean captured() {
            return captured;
        }

        public void setCaptured(boolean captured) {
            markDirty();
            this.captured = captured;
        }

        public long nextReinforceTick() {
            return nextReinforceTick;
        }

        public void setNextReinforceTick(long tick) {
            markDirty();
            this.nextReinforceTick = tick;
        }

        public long captureEndTick() {
            return captureEndTick;
        }

        public void setCaptureEndTick(long tick) {
            markDirty();
            this.captureEndTick = tick;
        }

        public long cooldownEndTick() {
            return cooldownEndTick;
        }

        public void setCooldownEndTick(long tick) {
            markDirty();
            this.cooldownEndTick = tick;
        }

        public long graceEndTick() {
            return graceEndTick;
        }

        public void setGraceEndTick(long tick) {
            markDirty();
            this.graceEndTick = tick;
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
            markDirty();
            if (stats == null) {
                stats = new RuinStats();
            }
            return stats;
        }

        public void clearStats() {
            markDirty();
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

    public static RuinSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RuinSites::load, RuinSites::new, DATA_NAME);
    }

    /* 按遗迹中心查找站点, 生物与统计都靠它定位归属 */
    @Nullable
    public static Site find(ServerLevel level, BlockPos origin) {
        return get(level).byOrigin.get(origin.asLong());
    }

    /* 按旗帜位置查找站点, 破坏与爆炸事件靠它判定保护范围 */
    @Nullable
    public static Site siteAt(ServerLevel level, BlockPos pos) {
        return get(level).byBanner.get(pos.asLong());
    }

    /* 旗帜位置的唯一写入口: 同时维护按旗帜查站点的索引 */
    public static void bindBanner(ServerLevel level, Site site, @Nullable BlockPos banner, String color) {
        RuinSites data = get(level);
        BlockPos previous = site.banner();
        if (previous != null) {
            data.byBanner.remove(previous.asLong(), site);
        }
        site.setBanner(banner, color);
        if (banner != null) {
            data.byBanner.put(banner.asLong(), site);
        }
        data.setDirty();
    }

    /*
     * 物化一座站点。
     * 只由已加载区块里的结构起点触发, 幂等: 已登记的站点返回空, 调用方据此判断是否新建;
     * 领地半径, 守军基数, 补兵与夺旗参数, 旗帜位置全部由建筑名与片段种子确定性推导,
     * 因此崩溃后重新物化得到同一组数值, 不会出现第二次补兵基数。
     */
    @Nullable
    public static Site materialize(ServerLevel level, BlockPos origin, String schematic, long seed,
                                   int baseY, int width, int height, int depth) {
        RuinSites data = get(level);
        if (data.byOrigin.containsKey(origin.asLong())) {
            return null;
        }
        Site site = new Site(origin);
        site.schematic = schematic == null ? "" : schematic;
        site.seed = seed;
        site.baseY = baseY;
        site.width = Math.max(1, width);
        site.height = Math.max(1, height);
        site.depth = Math.max(1, depth);
        site.radius = RuinTerritory.radiusFor(site.width, site.depth);
        RandomSource random = RandomSource.create(seed ^ SALT_GARRISON);
        int[] baseRange = ServerConfig.ruinGarrisonBaseRange();
        int low = Math.min(baseRange[0], baseRange[1]);
        int high = Math.max(baseRange[0], baseRange[1]);
        site.garrisonBase = low + random.nextInt(Math.max(1, high - low + 1));
        site.reinforcePercent = percentIn(ServerConfig.ruinReinforcePercentMin(),
                ServerConfig.ruinReinforcePercentMax(), random);
        site.reinforceInterval = randomIn(ServerConfig.ruinReinforceIntervalMin(),
                ServerConfig.ruinReinforceIntervalMax(), random);
        site.capturePercent = percentIn(ServerConfig.ruinCapturePercentMin(),
                ServerConfig.ruinCapturePercentMax(), random);
        site.captureSeconds = randomIn(ServerConfig.ruinCaptureTimeMin(),
                ServerConfig.ruinCaptureTimeMax(), random);
        RuinSchematic blueprint = site.schematic.isEmpty() ? null : SchematicCatalog.load(site.schematic);
        if (blueprint != null) {
            BlockPos min = new BlockPos(origin.getX() - site.width / 2, baseY, origin.getZ() - site.depth / 2);
            RuinBannerPlan.Plan plan = RuinBannerPlan.plan(blueprint, min, seed);
            if (plan != null) {
                site.banner = plan.banner();
                site.bannerColor = plan.color().getSerializedName();
            }
        }
        site.pasted = true;
        site.nextReinforceTick = level.getGameTime() + 20L;
        data.sites.add(site);
        data.byOrigin.put(origin.asLong(), site);
        if (site.banner != null) {
            data.byBanner.put(site.banner.asLong(), site);
        }
        data.setDirty();
        return site;
    }

    /* 百分比参数抽取: 结果是一到一百之间的整数折算 */
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

    public List<Site> sites() {
        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Site site : sites) {
            list.add(site.serialized());
        }
        tag.put("sites", list);
        return tag;
    }

    /* 单座站点的落盘形态: 只有改动过的站点才会重新拼一次, 其余复用上一次的结果 */
    private static CompoundTag writeSite(Site site) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("origin", site.origin().asLong());
        entry.putInt("radius", site.radius());
        entry.putString("schematic", site.schematic());
        entry.putInt("baseY", site.baseY());
        entry.putLong("seed", site.seed());
        entry.putInt("width", site.width());
        entry.putInt("height", site.height());
        entry.putInt("depth", site.depth());
        entry.putBoolean("pasted", site.pasted());
        if (site.banner() != null) {
            entry.putLong("banner", site.banner().asLong());
        }
        entry.putString("bannerColor", site.bannerColor());
        entry.putFloat("reinforcePercent", site.reinforcePercent());
        entry.putInt("reinforceInterval", site.reinforceInterval());
        entry.putFloat("capturePercent", site.capturePercent());
        entry.putInt("captureSeconds", site.captureSeconds());
        entry.putInt("garrisonBase", site.garrisonBase());
        entry.putBoolean("suppliesPlaced", site.suppliesPlaced());
        entry.putBoolean("abandoned", site.abandoned());
        entry.putBoolean("captured", site.captured());
        entry.putLong("nextReinforceTick", site.nextReinforceTick());
        entry.putLong("cooldownEndTick", site.cooldownEndTick());
        /* 进行中的夺旗与宽限也要落盘, 否则重启会静默丢掉倒计时 */
        entry.putLong("captureEndTick", site.captureEndTick());
        entry.putLong("graceEndTick", site.graceEndTick());
        if (site.stats() != null) {
            entry.put("stats", site.stats().save(new CompoundTag()));
        }
        return entry;
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
            site.seed = entry.contains("seed") ? entry.getLong("seed") : 0L;
            site.width = entry.getInt("width");
            site.height = entry.getInt("height");
            site.depth = entry.getInt("depth");
            /* 旧存档没有建筑字段, 直接视为已经落地, 避免回贴出多余的建筑 */
            site.pasted = entry.contains("pasted") ? entry.getBoolean("pasted") : true;
            site.banner = entry.contains("banner") ? BlockPos.of(entry.getLong("banner")) : null;
            site.bannerColor = entry.getString("bannerColor");
            site.reinforcePercent = entry.contains("reinforcePercent") ? entry.getFloat("reinforcePercent") : 0.5F;
            site.reinforceInterval = entry.contains("reinforceInterval") ? entry.getInt("reinforceInterval") : 20;
            site.capturePercent = entry.contains("capturePercent") ? entry.getFloat("capturePercent") : 0.4F;
            site.captureSeconds = entry.contains("captureSeconds") ? entry.getInt("captureSeconds") : 60;
            site.garrisonBase = entry.contains("garrisonBase") ? entry.getInt("garrisonBase") : 0;
            /* 旧存档没有种子, 无法重算物资箱位置, 内容保持旧逻辑留下的样子, 不再补填 */
            site.suppliesPlaced = site.seed == 0L || entry.getBoolean("suppliesPlaced");
            site.abandoned = entry.getBoolean("abandoned");
            site.captured = entry.getBoolean("captured");
            site.nextReinforceTick = entry.getLong("nextReinforceTick");
            site.cooldownEndTick = entry.getLong("cooldownEndTick");
            site.captureEndTick = entry.getLong("captureEndTick");
            site.graceEndTick = entry.getLong("graceEndTick");
            if (entry.contains("stats", Tag.TAG_COMPOUND)) {
                site.stats = RuinStats.load(entry.getCompound("stats"));
            }
            data.sites.add(site);
            data.byOrigin.put(site.origin().asLong(), site);
            if (site.banner != null) {
                data.byBanner.put(site.banner.asLong(), site);
            }
        }
        return data;
    }
}
