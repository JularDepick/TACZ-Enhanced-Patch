package com.taczenhancedpatch.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 遗迹站点登记。
 * 世界生成线程只把中心点, 选中的建筑与底面高度放进待处理队列, 由服务端 tick 收敛到存档数据,
 * 避免在区块生成线程里直接读写存档数据; 每个站点记录中心, 领地半径, 建筑是否已落地与守军是否已经生成。
 */
public final class RuinSites extends SavedData {
    private static final String DATA_NAME = "tacz_enhanced_patch_ruins";
    /* 队列上限: 防守军生成器失联时队列无界增长拖垮内存 */
    private static final int PENDING_LIMIT = 4096;
    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private final List<Site> sites = new ArrayList<>();

    /* origin 为遗迹中心, schematic 为建筑资源路径, baseY 为建筑底面高度, pasted 表示方块已经落地 */
    public record Site(BlockPos origin, int radius, boolean seeded, String schematic, int baseY, boolean pasted) {
    }

    private record Pending(BlockPos origin, String schematic, int baseY) {
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
                sites.add(new Site(pending.origin(), 28, false, pending.schematic(), pending.baseY(), false));
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

    public void markSeeded(Site site) {
        for (int i = 0; i < sites.size(); i++) {
            Site current = sites.get(i);
            if (current.origin().equals(site.origin())) {
                sites.set(i, new Site(current.origin(), current.radius(), true, current.schematic(),
                        current.baseY(), current.pasted()));
                setDirty();
                return;
            }
        }
    }

    /* 建筑落地后同时按建筑尺寸扩大领地半径 */
    public void markPasted(Site site, int radius) {
        for (int i = 0; i < sites.size(); i++) {
            Site current = sites.get(i);
            if (current.origin().equals(site.origin())) {
                sites.set(i, new Site(current.origin(), Math.max(28, radius), current.seeded(),
                        current.schematic(), current.baseY(), true));
                setDirty();
                return;
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Site site : sites) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("origin", site.origin().asLong());
            entry.putInt("radius", site.radius());
            entry.putBoolean("seeded", site.seeded());
            entry.putString("schematic", site.schematic());
            entry.putInt("baseY", site.baseY());
            entry.putBoolean("pasted", site.pasted());
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
            /* 旧存档没有建筑字段, 直接视为已经落地, 避免回贴出多余的建筑 */
            String schematic = entry.getString("schematic");
            boolean pasted = entry.contains("pasted") ? entry.getBoolean("pasted") : true;
            data.sites.add(new Site(BlockPos.of(entry.getLong("origin")), entry.getInt("radius"),
                    entry.getBoolean("seeded"), schematic, entry.getInt("baseY"), pasted));
        }
        return data;
    }
}
