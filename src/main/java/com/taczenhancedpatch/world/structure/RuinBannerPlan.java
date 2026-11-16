package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.world.schematic.RuinSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/*
 * 遗迹旗帜的选址。
 * 只读蓝图与片段种子: 中心列自下而上第一格空气即旗位, 颜色由种子决定,
 * 因此世界生成与站点物化两次调用得到同一结果, 期间不读任何方块, 也不加载任何区块。
 */
public final class RuinBannerPlan {
    /* 颜色抽取的盐值, 与守军参数的盐值分开, 改动其一不影响另一 */
    private static final long SALT_COLOR = 0x5DEECE66DL;
    private static final int COLORS = 16;

    /* support 只在旗位下方不是实心时需要, 为空表示蓝图本身已经托住旗帜 */
    public record Plan(BlockPos banner, @Nullable BlockPos support, DyeColor color) {
    }

    private RuinBannerPlan() {
    }

    @Nullable
    public static Plan plan(RuinSchematic blueprint, BlockPos min, long seed) {
        if (blueprint == null || blueprint.width() <= 0 || blueprint.depth() <= 0 || blueprint.height() <= 0) {
            return null;
        }
        int x = blueprint.width() / 2;
        int z = blueprint.depth() / 2;
        int top = blueprint.height() - 1;
        int y = 0;
        for (int slot = 0; slot <= top; slot++) {
            if (isAir(blueprint.stateAt(x, slot, z))) {
                y = slot;
                break;
            }
        }
        BlockPos banner = new BlockPos(min.getX() + x, min.getY() + y, min.getZ() + z);
        BlockPos support = null;
        if (y > 0 && !solid(blueprint.stateAt(x, y - 1, z))) {
            support = banner.below();
        }
        DyeColor color = DyeColor.byId(RandomSource.create(seed ^ SALT_COLOR).nextInt(COLORS));
        return new Plan(banner, support, color);
    }

    private static boolean isAir(@Nullable BlockState state) {
        return state == null || state.isAir();
    }

    private static boolean solid(@Nullable BlockState state) {
        return state != null && state.isSolid();
    }
}
