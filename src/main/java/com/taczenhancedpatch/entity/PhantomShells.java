package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.faction.FactionData;
import com.taczenhancedpatch.faction.RuinSites;
import com.taczenhancedpatch.item.PatchItems;
import com.taczenhancedpatch.shell.ShellColor;
import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.shell.ShellSettings;
import com.taczenhancedpatch.shell.ShellTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/*
 * 幻机的背包手雷。
 * 数量区间在遗迹落地时从配置区间里取一个子区间, 之后这座遗迹的幻机都在子区间内随机;
 * 子区间按遗迹坐标确定, 因此重启后仍然一致, 不需要额外存档字段。
 * 背包记在实体的持久化数据里, 投一发少一发, 打空后幻机只巡逻与报警。
 */
public final class PhantomShells {
    private static final String KEY = "PhantomShells";
    private static final String FILLED = "PhantomShellsFilled";

    private PhantomShells() {
    }

    /* 遗迹生成时从配置区间取子区间; 同一坐标永远得到同一对上下限 */
    public static int[] rangeFor(BlockPos origin) {
        int[] configured = ServerConfig.phantomShellRange();
        int low = configured[0];
        int high = configured[1];
        if (high <= low) {
            return new int[]{low, high};
        }
        long seed = origin.asLong() * 0x9E3779B97F4A7C15L;
        RandomSource random = RandomSource.create(seed);
        int min = low + random.nextInt(high - low + 1);
        int max = min + random.nextInt(high - min + 1);
        return new int[]{min, max};
    }

    private static int[] rangeOf(Mob mob) {
        BlockPos home = FactionData.homeOf(mob);
        if (home != null && mob.level() instanceof ServerLevel level && RuinSites.find(level, home) != null) {
            return rangeFor(home);
        }
        return ServerConfig.phantomShellRange();
    }

    /* 首次使用时按区间生成背包 */
    public static void ensure(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(FILLED)) {
            return;
        }
        data.putBoolean(FILLED, true);
        int[] range = rangeOf(mob);
        int count = range[0] + mob.getRandom().nextInt(Math.max(1, range[1] - range[0] + 1));
        ListTag list = new ListTag();
        for (int i = 0; i < count; i++) {
            ShellSettings settings = new ShellSettings(ShellSettings.FUSE_IMPACT, true,
                    1 + mob.getRandom().nextInt(ShellSettings.POWER_MAX), ShellTarget.NO_TERRAIN,
                    ShellColor.values()[mob.getRandom().nextInt(ShellColor.values().length)], true);
            ItemStack stack = new ItemStack(PatchItems.GRENADE.get());
            if (stack.getItem() instanceof ShellItem item) {
                item.setSettings(stack, settings);
            }
            list.add(stack.save(new CompoundTag()));
        }
        data.put(KEY, list);
    }

    /* 取一发, 空了返回 null */
    @Nullable
    public static ShellSettings take(Mob mob) {
        ensure(mob);
        ListTag list = mob.getPersistentData().getList(KEY, 10);
        if (list.isEmpty()) {
            return null;
        }
        CompoundTag entry = list.getCompound(0);
        list.remove(0);
        ItemStack stack = ItemStack.of(entry);
        return stack.getItem() instanceof ShellItem item ? item.settingsOf(stack) : null;
    }

    /* 击杀时把剩余手雷掉出来 */
    public static void dropAll(Mob mob) {
        ListTag list = mob.getPersistentData().getList(KEY, 10);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                mob.spawnAtLocation(stack);
            }
        }
        list.clear();
    }
}
