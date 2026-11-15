package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.world.schematic.RuinSchematic;
import com.taczenhancedpatch.world.schematic.SchematicCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/*
 * 遗迹片段。
 * 一段片段对应一整座建筑: 记录蓝图名与最小角, 每个区块在自己的生成阶段只写与自己相交的那一部分,
 * 越出本区块的写入由 placeBlock 按传入的包围盒丢弃, 所以不需要强制加载邻块, 也不会触发方块自己的 onPlace。
 * 蓝图里的空气同样写入, 用来清掉建筑内部的原始地形。
 */
public class RuinPiece extends StructurePiece {
    private static final String TAG_SCHEMATIC = "Schematic";

    private final String schematic;

    public RuinPiece(String schematic, BlockPos min, int width, int height, int depth) {
        super(RuinStructures.RUIN_PIECE.get(), 0, new BoundingBox(min.getX(), min.getY(), min.getZ(),
                min.getX() + width - 1, min.getY() + height - 1, min.getZ() + depth - 1));
        this.schematic = schematic;
    }

    public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(RuinStructures.RUIN_PIECE.get(), tag);
        this.schematic = tag.getString(TAG_SCHEMATIC);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(TAG_SCHEMATIC, this.schematic);
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
    }
}
