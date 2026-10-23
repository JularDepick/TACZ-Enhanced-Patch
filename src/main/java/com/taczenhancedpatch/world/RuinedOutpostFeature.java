package com.taczenhancedpatch.world;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Objects;

/**
 * 废弃前哨遗迹。
 * 生成一座坍塌的方形据点: 石砖地基与残墙, 中央宝箱与刷怪笼,
 * 刷怪笼产出的守卫由枪手系统接管, 宝箱由数据包战利品表填充。
 */
public class RuinedOutpostFeature extends Feature<NoneFeatureConfiguration> {
    private static final int RADIUS = 5;
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));

    public RuinedOutpostFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!CommonConfig.ruinsEnabled()) {
            return false;
        }
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int baseY = origin.getY() - 1;
        if (baseY <= level.getSeaLevel() || !prepareGround(level, origin, baseY)) {
            return false;
        }
        buildFloor(level, origin, random);
        clearSpace(level, origin);
        buildWalls(level, origin, random);
        buildInterior(level, origin, random);
        return true;
    }

    private boolean prepareGround(WorldGenLevel level, BlockPos origin, int baseY) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (Math.abs(surface - baseY) > 1) {
                    return false;
                }
                BlockPos ground = new BlockPos(x, surface, z);
                if (!level.getBlockState(ground).isSolidRender(level, ground)) {
                    return false;
                }
                if (surface < baseY) {
                    for (int y = surface + 1; y < baseY; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                    }
                } else if (surface > baseY) {
                    level.setBlock(new BlockPos(x, baseY, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    private void buildFloor(WorldGenLevel level, BlockPos origin, RandomSource random) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), floorState(random), 2);
            }
        }
    }

    private void clearSpace(WorldGenLevel level, BlockPos origin) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private void buildWalls(WorldGenLevel level, BlockPos origin, RandomSource random) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                boolean border = Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS;
                if (!border) {
                    continue;
                }
                boolean corner = Math.abs(dx) == RADIUS && Math.abs(dz) == RADIUS;
                boolean gate = Math.abs(dz) == RADIUS && Math.abs(dx) <= 1;
                int wallHeight = corner ? 4 : 3;
                for (int dy = 1; dy <= wallHeight; dy++) {
                    if (gate && dy <= 2) {
                        continue;
                    }
                    if (!corner && random.nextFloat() < 0.16F) {
                        continue;
                    }
                    level.setBlock(origin.offset(dx, dy, dz), wallState(random), 2);
                }
            }
        }
    }

    private void buildInterior(WorldGenLevel level, BlockPos origin, RandomSource random) {
        BlockPos chestPos = origin.offset(0, 0, 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
        if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(LOOT_TABLE, random.nextLong());
        }

        BlockPos spawnerPos = origin.offset(0, 0, -2);
        level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(EntityType.ZOMBIE, random);
        }

        int debris = 4 + random.nextInt(4);
        for (int i = 0; i < debris; i++) {
            int dx = random.nextInt(RADIUS * 2 - 3) - (RADIUS - 2);
            int dz = random.nextInt(RADIUS * 2 - 3) - (RADIUS - 2);
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 2) {
                continue;
            }
            level.setBlock(origin.offset(dx, 0, dz), wallState(random), 2);
        }
    }

    private BlockState floorState(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.5F) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (roll < 0.7F) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (roll < 0.85F) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private BlockState wallState(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.45F) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (roll < 0.65F) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        if (roll < 0.8F) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }
}
