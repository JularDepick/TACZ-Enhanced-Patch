package com.taczenhancedpatch.world;

import com.mojang.serialization.Codec;
import com.taczenhancedpatch.config.ServerConfig;
import com.taczenhancedpatch.world.ruin.RuinLayout;
import com.taczenhancedpatch.world.ruin.RuinLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
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
 * 布局本身写在 src/main/ruins/ruined_outpost.json, 构建期编译成数据类, 运行期按符号矩阵摆放;
 * 本类只负责地形平整, 清空, 逐层摆放, 标记点与地面碎块这些通用步骤,
 * 因此改造型, 换材质, 加房间都只需要修改 JSON 后重新构建。
 */
public class RuinedOutpostFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));

    public RuinedOutpostFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ServerConfig.ruinsEnabled()) {
            return false;
        }
        RuinLayout layout = RuinLayouts.ruinedOutpost();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int baseY = origin.getY() - 1;
        int halfWidth = layout.width() / 2;
        int halfDepth = layout.depth() / 2;
        if (baseY <= level.getSeaLevel() || !prepareGround(level, origin, baseY, halfWidth, halfDepth)) {
            return false;
        }
        buildFloor(level, origin, random, layout, halfWidth, halfDepth);
        clearSpace(level, origin, layout, halfWidth, halfDepth);
        placeLayers(level, origin, random, layout, halfWidth, halfDepth);
        placeMarkers(level, origin, random, layout);
        scatterDebris(level, origin, random, layout);
        return true;
    }

    /* 地形平整: 范围内每列高差不超过一格, 低洼补土, 高处削平 */
    private boolean prepareGround(WorldGenLevel level, BlockPos origin, int baseY, int halfWidth, int halfDepth) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfDepth; dz <= halfDepth; dz++) {
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

    /* 地基: 按布局声明的地板符号逐格抽取材质 */
    private void buildFloor(WorldGenLevel level, BlockPos origin, RandomSource random, RuinLayout layout,
                            int halfWidth, int halfDepth) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfDepth; dz <= halfDepth; dz++) {
                BlockState state = layout.pick(random, layout.floorSymbol());
                if (state == null) {
                    continue;
                }
                level.setBlock(origin.offset(dx, layout.floorY(), dz), state, 2);
            }
        }
    }

    private void clearSpace(WorldGenLevel level, BlockPos origin, RuinLayout layout, int halfWidth, int halfDepth) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfDepth; dz <= halfDepth; dz++) {
                for (int dy = 0; dy < layout.clearHeight(); dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /* 逐层摆放: 按符号查调色板, 带缺口概率的符号按概率跳过 */
    private void placeLayers(WorldGenLevel level, BlockPos origin, RandomSource random, RuinLayout layout,
                             int halfWidth, int halfDepth) {
        for (int y = 0; y < layout.height(); y++) {
            for (int z = 0; z < layout.depth(); z++) {
                String row = layout.layers().get(y).get(z);
                for (int x = 0; x < layout.width(); x++) {
                    char symbol = row.charAt(x);
                    float gap = layout.gapChanceOf(symbol);
                    if (gap > 0.0F && random.nextFloat() < gap) {
                        continue;
                    }
                    BlockState state = layout.pick(random, symbol);
                    if (state == null) {
                        continue;
                    }
                    level.setBlock(origin.offset(x - halfWidth, y, z - halfDepth), state, 2);
                }
            }
        }
    }

    /* 标记点: 宝箱写入战利品表, 刷怪笼写入生物 ID */
    private void placeMarkers(WorldGenLevel level, BlockPos origin, RandomSource random, RuinLayout layout) {
        RuinLayout.Marker chest = layout.chest();
        Direction facing = Direction.byName(layout.chestFacing());
        BlockPos chestPos = origin.offset(chest.x() - layout.width() / 2, chest.y(), chest.z() - layout.depth() / 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing == null ? Direction.NORTH : facing), 2);
        if (level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(LOOT_TABLE, random.nextLong());
        }

        RuinLayout.Marker spawnerMarker = layout.spawner();
        BlockPos spawnerPos = origin.offset(spawnerMarker.x() - layout.width() / 2, spawnerMarker.y(),
                spawnerMarker.z() - layout.depth() / 2);
        level.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner) {
            EntityType<?> type = resolveEntity(layout.spawnerEntity());
            if (type != null) {
                spawner.setEntityId(type, random);
            }
        }
    }

    /* 地面碎块: 在布局声明的范围内随机散落残骸, 避开物品生成点 */
    private void scatterDebris(WorldGenLevel level, BlockPos origin, RandomSource random, RuinLayout layout) {
        RuinLayout.Debris debris = layout.debris();
        if (debris.max() <= 0 || debris.maxOffset() <= 0) {
            return;
        }
        int count = debris.min() + random.nextInt(debris.max() - debris.min() + 1);
        int span = debris.maxOffset() * 2 + 1;
        for (int i = 0; i < count; i++) {
            int dx = random.nextInt(span) - debris.maxOffset();
            int dz = random.nextInt(span) - debris.maxOffset();
            if (Math.abs(dx) <= debris.innerHalfX() && Math.abs(dz) <= debris.innerHalfZ()) {
                continue;
            }
            BlockState state = layout.pick(random, debris.symbol());
            if (state == null) {
                continue;
            }
            level.setBlock(origin.offset(dx, 0, dz), state, 2);
        }
    }

    private static EntityType<?> resolveEntity(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.ENTITY_TYPE.get(location);
    }
}
