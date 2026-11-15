package com.taczenhancedpatch.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/*
 * 遗迹片段。
 * 一段片段对应一整座建筑, 每个区块在自己的生成阶段只写与自己相交的那一部分,
 * 越出本区块的写入由 placeBlock 按传入的包围盒自动丢弃, 因此不需要强制加载邻块。
 * 第一阶段只放一个石方块标记。
 */
public class RuinPiece extends StructurePiece {
    public RuinPiece(BlockPos origin, int width, int height, int depth) {
        super(RuinStructures.RUIN_PIECE.get(), 0,
                makeBoundingBox(origin.getX(), origin.getY(), origin.getZ(), Direction.NORTH, width, height, depth));
    }

    public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(RuinStructures.RUIN_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
            for (int y = this.boundingBox.minY(); y <= this.boundingBox.maxY(); y++) {
                for (int z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                    this.placeBlock(level, Blocks.STONE.defaultBlockState(), x, y, z, box);
                }
            }
        }
    }
}
