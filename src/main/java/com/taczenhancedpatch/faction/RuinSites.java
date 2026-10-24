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
 * 世界生成线程只把中心点放进待处理队列, 由服务端 tick 收敛到存档数据,
 * 避免在区块生成线程里直接读写存档数据; 每个站点记录中心, 领地半径与守军是否已经生成。
 */
public final class RuinSites extends SavedData {
    private static final String DATA_NAME = "tacz_enhanced_patch_ruins";
    private static final ConcurrentLinkedQueue<BlockPos> PENDING = new ConcurrentLinkedQueue<>();

    private final List<Site> sites = new ArrayList<>();

    public record Site(BlockPos origin, int radius, boolean seeded) {
    }

    /* 世界生成线程调用, 只入队 */
    public static void queue(BlockPos origin) {
        PENDING.add(origin.immutable());
    }

    public static RuinSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RuinSites::load, RuinSites::new, DATA_NAME);
    }

    /* 把队列里的新站点并入存档数据 */
    public void drainPending() {
        BlockPos origin;
        boolean changed = false;
        while ((origin = PENDING.poll()) != null) {
            boolean known = false;
            for (Site site : sites) {
                if (site.origin().equals(origin)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                sites.add(new Site(origin, 28, false));
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
                sites.set(i, new Site(current.origin(), current.radius(), true));
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
            data.sites.add(new Site(BlockPos.of(entry.getLong("origin")),
                    entry.getInt("radius"), entry.getBoolean("seeded")));
        }
        return data;
    }
}
