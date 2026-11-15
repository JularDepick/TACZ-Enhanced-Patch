package com.taczenhancedpatch.world.structure;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/*
 * 遗迹结构的注册表。
 * 结构类型与片段类型都进原版注册表, 数据包里的 JSON 按资源位置引用它们。
 */
public final class RuinStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<StructureType<RuinStructure>> RUIN =
            STRUCTURE_TYPES.register("ruined_outpost", () -> () -> RuinStructure.CODEC);
    public static final RegistryObject<StructurePieceType> RUIN_PIECE =
            PIECE_TYPES.register("ruin_piece", () -> RuinPiece::new);

    private RuinStructures() {
    }
}
