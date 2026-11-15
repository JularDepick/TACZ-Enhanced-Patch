package com.taczenhancedpatch.config;

import net.minecraftforge.common.ForgeConfigSpec;

/*
 * 世界生成配置。
 * 独立成一份服务端配置文件, 绑定在存档上, 只能局外修改, 不进配置界面。
 * 两项都是单座遗迹的规模上限, 用来挡住会拖慢区块生成的超大蓝图。
 */
public final class WorldgenConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue MAX_CHUNKS;
    private static final ForgeConfigSpec.IntValue MAX_SOLID_BLOCKS;
    private static final int MAX_CHUNKS_DEFAULT = 25;
    private static final int MAX_SOLID_BLOCKS_DEFAULT = 50000;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("遗迹结构的世界生成上限, 只能改这份文件, 游戏内不可调").push("ruin");
        MAX_CHUNKS = builder.comment("单座遗迹允许覆盖的区块数上限")
                .defineInRange("maxChunks", MAX_CHUNKS_DEFAULT, 9, 25);
        MAX_SOLID_BLOCKS = builder.comment("单座遗迹允许写入的实心方块数上限")
                .defineInRange("maxSolidBlocks", MAX_SOLID_BLOCKS_DEFAULT, 30000, 250000);
        builder.pop();
        SPEC = builder.build();
    }

    private WorldgenConfig() {
    }

    public static int maxChunks() {
        return value(MAX_CHUNKS, MAX_CHUNKS_DEFAULT);
    }

    public static int maxSolidBlocks() {
        return value(MAX_SOLID_BLOCKS, MAX_SOLID_BLOCKS_DEFAULT);
    }

    private static int value(ForgeConfigSpec.IntValue entry, int fallback) {
        try {
            return entry.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }
}
