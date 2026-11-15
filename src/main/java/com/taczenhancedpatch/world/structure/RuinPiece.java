package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Objects;

/*
 * 遗迹片段。
 * 一段片段对应一整座建筑: 记录蓝图名与最小角, 每个区块在自己的生成阶段只写与自己相交的那一部分,
 * 越出本区块的写入由 placeBlock 按传入的包围盒丢弃, 所以不需要强制加载邻块, 也不会触发方块自己的 onPlace。
 * 蓝图里的空气同样写入, 用来清掉建筑内部的原始地形。
 */
public class RuinPiece extends StructurePiece {
    private static final String TAG_SCHEMATIC = "Schematic";
    private static final String TAG_SEED = "Seed";
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));

    private final String schematic;
    /* 物资箱位置由这个种子决定, 世界生成与内容填充共用同一批坐标 */
    private final long seed;

    public RuinPiece(String schematic, BlockPos min, int width, int height, int depth, long seed) {
        super(RuinStructures.RUIN_PIECE.get(), 0, new BoundingBox(min.getX(), min.getY(), min.getZ(),
                min.getX() + width - 1, min.getY() + height - 1, min.getZ() + depth - 1));
        this.schematic = schematic;
        this.seed = seed;
    }

    public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(RuinStructures.RUIN_PIECE.get(), tag);
        this.schematic = tag.getString(TAG_SCHEMATIC);
        this.seed = tag.getLong(TAG_SEED);
    }

    public String schematic() {
        return this.schematic;
    }

    public long seed() {
        return this.seed;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(TAG_SCHEMATIC, this.schematic);
        tag.putLong(TAG_SEED, this.seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        RuinSchematic blueprint = SchematicCatalog.load(this.schematic);
        if (blueprint == null) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} unavailable at {}", this.schematic, this.boundingBox);
            return;
        }
        int minX = Math.max(this.boundingBox.minX(), box.minX());
        int maxX = Math.min(this.boundingBox.maxX(), box.maxX());
        int minY = Math.max(this.boundingBox.minY(), box.minY());
        int maxY = Math.min(this.boundingBox.maxY(), box.maxY());
        int minZ = Math.max(this.boundingBox.minZ(), box.minZ());
        int maxZ = Math.min(this.boundingBox.maxZ(), box.maxZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            int localY = y - this.boundingBox.minY();
            for (int z = minZ; z <= maxZ; z++) {
                int localZ = z - this.boundingBox.minZ();
                for (int x = minX; x <= maxX; x++) {
                    BlockState state = blueprint.stateAt(x - this.boundingBox.minX(), localY, localZ);
                    if (state == null) {
                        cursor.set(x, y, z);
                        if (level.getBlockState(cursor).isAir()) {
                            continue;
                        }
                        state = Blocks.AIR.defaultBlockState();
                    }
                    this.placeBlock(level, state, x, y, z, box);
                }
            }
        }
        placeLootChest(level, blueprint, box, random);
        placeSupplyChests(level, blueprint, box);
    }

    /* 物资箱: 只放方块, 内容由站点接线在区块加载后填充一次 */
    private void placeSupplyChests(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box) {
        BlockPos min = new BlockPos(this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ());
        for (BlockPos spot : RuinLoot.supplySpots(blueprint, min, this.seed, level.getLevel().getDifficulty())) {
            if (!box.isInside(spot)) {
                continue;
            }
            this.placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH),
                    spot.getX(), spot.getY(), spot.getZ(), box);
        }
    }

    /* 战利品箱: 按蓝图数据找一处内部地面空位, 生成阶段直接带战利品表落地 */
    private void placeLootChest(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box, RandomSource random) {
        BlockPos spot = findChestSpot(blueprint);
        if (spot == null || !box.isInside(spot)) {
            return;
        }
        this.createChest(level, box, random, spot.getX(), spot.getY(), spot.getZ(), LOOT_TABLE);
    }

    /*
     * 从建筑中心向外一圈圈找: 脚下是实心, 当前位置与上方两格是空气, 八格内有天花板。
     * 判定只看蓝图数据, 因此每次调用得到同一个位置, 各区块不会各自放一口箱子。
     */
    private BlockPos findChestSpot(RuinSchematic blueprint) {
        int centerX = blueprint.width() / 2;
        int centerZ = blueprint.depth() / 2;
        for (int ring = 0; ring <= 12; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (x < 1 || z < 1 || x >= blueprint.width() - 1 || z >= blueprint.depth() - 1) {
                        continue;
                    }
                    for (int y = 1; y < blueprint.height() - 2; y++) {
                        if (!solid(blueprint, x, y - 1, z)) {
                            continue;
                        }
                        if (solid(blueprint, x, y, z) || solid(blueprint, x, y + 1, z)) {
                            continue;
                        }
                        if (!ceiling(blueprint, x, y + 2, z)) {
                            continue;
                        }
                        return new BlockPos(this.boundingBox.minX() + x, this.boundingBox.minY() + y,
                                this.boundingBox.minZ() + z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean solid(RuinSchematic blueprint, int x, int y, int z) {
        BlockState state = blueprint.stateAt(x, y, z);
        return state != null && state.isSolid();
    }

    private static boolean ceiling(RuinSchematic blueprint, int x, int fromY, int z) {
        for (int y = fromY; y <= fromY + 6 && y < blueprint.height(); y++) {
            if (solid(blueprint, x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
