package com.taczenhancedpatch.world.ruin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 遗迹布局的装配入口。
 * 把构建期生成的数据类转成运行时可用的布局对象, 首次使用时装配并缓存,
 * 方块与实体都按注册名解析, 因此 JSON 里只需要写原版或模组的完整 ID。
 */
public final class RuinLayouts {
    private static volatile RuinLayout ruinedOutpost;

    private RuinLayouts() {
    }

    public static RuinLayout ruinedOutpost() {
        RuinLayout cached = ruinedOutpost;
        if (cached == null) {
            synchronized (RuinLayouts.class) {
                cached = ruinedOutpost;
                if (cached == null) {
                    cached = build();
                    ruinedOutpost = cached;
                }
            }
        }
        return cached;
    }

    private static RuinLayout build() {
        Map<Character, List<RuinLayout.Entry>> palette = new HashMap<>();
        for (int i = 0; i < RuinedOutpostLayoutData.PALETTE_SYMBOLS.length; i++) {
            char symbol = RuinedOutpostLayoutData.PALETTE_SYMBOLS[i].charAt(0);
            String[] blockIds = RuinedOutpostLayoutData.PALETTE_BLOCKS[i];
            int[] weights = RuinedOutpostLayoutData.PALETTE_WEIGHTS[i];
            List<RuinLayout.Entry> entries = new ArrayList<>(blockIds.length);
            for (int j = 0; j < blockIds.length; j++) {
                entries.add(new RuinLayout.Entry(resolveBlock(blockIds[j]), weights[j]));
            }
            palette.put(symbol, List.copyOf(entries));
        }

        Map<Character, Float> gaps = new HashMap<>();
        for (int i = 0; i < RuinedOutpostLayoutData.GAP_SYMBOLS.length; i++) {
            gaps.put(RuinedOutpostLayoutData.GAP_SYMBOLS[i].charAt(0), RuinedOutpostLayoutData.GAP_CHANCES[i]);
        }

        List<List<String>> layers = new ArrayList<>(RuinedOutpostLayoutData.LAYERS.length);
        for (String[] rows : RuinedOutpostLayoutData.LAYERS) {
            layers.add(List.of(rows));
        }

        return new RuinLayout(
                RuinedOutpostLayoutData.ID,
                RuinedOutpostLayoutData.WIDTH,
                RuinedOutpostLayoutData.DEPTH,
                RuinedOutpostLayoutData.HEIGHT,
                RuinedOutpostLayoutData.CLEAR_HEIGHT,
                RuinedOutpostLayoutData.FLOOR_Y,
                RuinedOutpostLayoutData.FLOOR_SYMBOL.charAt(0),
                List.copyOf(layers),
                Map.copyOf(palette),
                Map.copyOf(gaps),
                marker(RuinedOutpostLayoutData.CHEST),
                RuinedOutpostLayoutData.CHEST_FACING,
                marker(RuinedOutpostLayoutData.SPAWNER),
                RuinedOutpostLayoutData.SPAWNER_ENTITY,
                new RuinLayout.Debris(
                        RuinedOutpostLayoutData.DEBRIS_MIN,
                        RuinedOutpostLayoutData.DEBRIS_MAX,
                        RuinedOutpostLayoutData.DEBRIS_SYMBOL.charAt(0),
                        RuinedOutpostLayoutData.DEBRIS_MAX_OFFSET,
                        RuinedOutpostLayoutData.DEBRIS_INNER_X,
                        RuinedOutpostLayoutData.DEBRIS_INNER_Z));
    }

    private static RuinLayout.Marker marker(int[] coordinates) {
        return new RuinLayout.Marker(coordinates[0], coordinates[1], coordinates[2]);
    }

    private static Block resolveBlock(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? BuiltInRegistries.BLOCK.byId(0) : BuiltInRegistries.BLOCK.get(location);
    }
}
