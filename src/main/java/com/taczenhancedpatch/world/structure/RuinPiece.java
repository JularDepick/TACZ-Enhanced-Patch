package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.faction.RuinBanner;
import com.taczenhancedpatch.faction.RuinLoot;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import com.taczenhancedpatch.world.schematic.SchematicChunkIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;
import java.util.Objects;

/*
 * 遗迹片段。
 * 一段片段对应一整座建筑: 记录蓝图名与最小角, 每个区块在自己的生成阶段只写与自己相交的那一部分。
 * 写入走蓝图解析期建好的分区块非空气格索引, 只遍历该区块真正有方块的格子,
 * 不再遍历整柱, 也不再为空气格读世界方块, 因此单区块写入量从整柱体积降到该区块实际方块数。
 * 越出本区块的写入由 placeBlock 按传入的包围盒丢弃, 所以不需要强制加载邻块, 也不会触发方块自己的 onPlace。
 * 旗帜与箱子同样在生成期落地: 旗帜坐标与颜色由蓝图与片段种子决定, 箱子只放方块, 内容由站点物化时补填。
 */
public class RuinPiece extends StructurePiece {
    private static final String TAG_SCHEMATIC = "Schematic";
    private static final String TAG_SEED = "Seed";
    private static final ResourceLocation LOOT_TABLE =
            Objects.requireNonNull(ResourceLocation.tryParse("tacz_enhanced_patch:chests/ruined_outpost"));

    private final String schematic;
    /* 物资箱与旗帜位置由这个种子决定, 世界生成与站点物化共用同一批坐标 */
    private final long seed;
    /* 物资箱与战利品箱的位置只由蓝图与种子决定, 一座建筑横跨多个区块时只算一次 */
    private transient List<BlockPos> supplySpots;
    private transient BlockPos lootSpot;
    private transient boolean lootSpotReady;

    public RuinPiece(String schematic, BlockPos min, int width, int height, int depth, long seed) {
        super(RuinStructures.RUIN_PIECE.get(), 0, new BoundingBox(min.getX(), min.getY(), min.getZ(),
                min.getX() + width - 1, min.getY() + height - 1, min.getZ() + depth - 1));
        /* 片段按世界坐标写入, 朝向取空值即恒等映射; 不设朝向时基类的镜像与旋转字段为 null, 写墙类方块会崩在镜像变换 */
        this.setOrientation((Direction) null);
        this.schematic = schematic;
        this.seed = seed;
    }

    public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(RuinStructures.RUIN_PIECE.get(), tag);
        /* 存档回读同样按恒等映射写入, 与生成期保持一致 */
        this.setOrientation((Direction) null);
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
        /* 世界生成在区块自己的生成阶段取蓝图, 不能等后台线程, 未命中时同步解析一次 */
        RuinSchematic blueprint = SchematicCatalog.loadSync(this.schematic);
        if (blueprint == null) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} unavailable at {}", this.schematic, this.boundingBox);
            return;
        }
        if (blueprint.width() != this.boundingBox.getXSpan() || blueprint.height() != this.boundingBox.getYSpan()
                || blueprint.depth() != this.boundingBox.getZSpan()) {
            TACZEnhancedPatch.LOGGER.warn("[ruin] blueprint {} size changed, skip {}", this.schematic, this.boundingBox);
            return;
        }
        int minX = Math.max(this.boundingBox.minX(), box.minX());
        int maxX = Math.min(this.boundingBox.maxX(), box.maxX());
        int minY = Math.max(this.boundingBox.minY(), box.minY());
        int maxY = Math.min(this.boundingBox.maxY(), box.maxY());
        int minZ = Math.max(this.boundingBox.minZ(), box.minZ());
        int maxZ = Math.min(this.boundingBox.maxZ(), box.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        writeCells(level, blueprint, box, minX, maxX, minY, maxY, minZ, maxZ);
        placeBanner(level, blueprint, box);
        placeLootChest(level, blueprint, box, random);
        placeSupplyChests(level, blueprint, box);
    }

    /*
     * 按区块索引写方块。
     * 只遍历与本区块相交的那几个局部区块的紧凑数组, 元素已带区块内坐标与调色板下标,
     * 因此没有整柱遍历, 也没有空气格读取; 高度方向按包围盒裁剪后再写。
     */
    private void writeCells(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box,
                            int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        SchematicChunkIndex index = blueprint.chunks();
        BlockState[] palette = blueprint.palette();
        int baseX = this.boundingBox.minX();
        int baseY = this.boundingBox.minY();
        int baseZ = this.boundingBox.minZ();
        int localMinX = minX - baseX;
        int localMaxX = maxX - baseX;
        int localMinZ = minZ - baseZ;
        int localMaxZ = maxZ - baseZ;
        for (int chunkZ = localMinZ >> 4; chunkZ <= (localMaxZ >> 4); chunkZ++) {
            for (int chunkX = localMinX >> 4; chunkX <= (localMaxX >> 4); chunkX++) {
                int[] cells = index.cellsOf(chunkX, chunkZ);
                if (cells == null) {
                    continue;
                }
                for (int packed : cells) {
                    int y = baseY + SchematicChunkIndex.y(packed);
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    int slot = SchematicChunkIndex.palette(packed);
                    if (slot >= palette.length) {
                        continue;
                    }
                    BlockState state = palette[slot];
                    if (state == null) {
                        continue;
                    }
                    this.placeBlock(level, state, baseX + SchematicChunkIndex.x(packed, chunkX), y,
                            baseZ + SchematicChunkIndex.z(packed, chunkZ), box);
                }
            }
        }
    }

    /* 旗帜在生成期落地: 位置与颜色只由蓝图与片段种子决定, 加载期不再放旗 */
    private void placeBanner(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box) {
        RuinBannerPlan.Plan plan = RuinBannerPlan.plan(blueprint, min(), this.seed);
        if (plan == null) {
            return;
        }
        BlockPos support = plan.support();
        if (support != null && box.isInside(support)) {
            this.placeBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                    support.getX(), support.getY(), support.getZ(), box);
        }
        BlockPos banner = plan.banner();
        if (box.isInside(banner)) {
            this.placeBlock(level, RuinBanner.blockOf(plan.color()).defaultBlockState(),
                    banner.getX(), banner.getY(), banner.getZ(), box);
        }
    }

    private BlockPos min() {
        return new BlockPos(this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ());
    }

    /* 物资箱: 只放方块, 内容由站点物化与玩家靠近时按需补填 */
    private void placeSupplyChests(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box) {
        for (BlockPos spot : supplySpots(blueprint, level)) {
            if (!box.isInside(spot)) {
                continue;
            }
            this.placeBlock(level, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH),
                    spot.getX(), spot.getY(), spot.getZ(), box);
        }
    }

    /* 位置只与蓝图, 种子与难度有关: 同一座建筑的所有区块共用一次计算 */
    private synchronized List<BlockPos> supplySpots(RuinSchematic blueprint, WorldGenLevel level) {
        if (this.supplySpots == null) {
            this.supplySpots = RuinLoot.supplySpots(blueprint, min(), this.seed, level.getLevel().getDifficulty());
        }
        return this.supplySpots;
    }

    /* 战利品箱: 按蓝图数据找一处内部地面空位, 生成阶段直接带战利品表落地 */
    private void placeLootChest(WorldGenLevel level, RuinSchematic blueprint, BoundingBox box, RandomSource random) {
        BlockPos spot = lootSpot(blueprint);
        if (spot == null || !box.isInside(spot)) {
            return;
        }
        this.createChest(level, box, random, spot.getX(), spot.getY(), spot.getZ(), LOOT_TABLE);
    }

    private synchronized BlockPos lootSpot(RuinSchematic blueprint) {
        if (!this.lootSpotReady) {
            this.lootSpot = findChestSpot(blueprint);
            this.lootSpotReady = true;
        }
        return this.lootSpot;
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
