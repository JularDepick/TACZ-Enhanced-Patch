package com.taczenhancedpatch.mortar;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/*
 * 迫击炮相关注册: 方块, 方块实体, 炮弹投射物, 方块物品, 炮弹物品与遥控物品。
 */
public final class MortarRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<Block> MORTAR = BLOCKS.register("mortar",
            () -> new MortarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(6.0F, 12.0F).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Item> MORTAR_ITEM = ITEMS.register("mortar",
            () -> new BlockItem(MORTAR.get(), new Item.Properties()));

    public static final RegistryObject<Item> MORTAR_SHELL = ITEMS.register("mortar_shell",
            () -> new MortarShellItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> MORTAR_REMOTE = ITEMS.register("mortar_remote",
            () -> new MortarRemoteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<MortarBlockEntity>> MORTAR_ENTITY =
            BLOCK_ENTITIES.register("mortar", () -> BlockEntityType.Builder
                    .of(MortarBlockEntity::new, MORTAR.get()).build(null));

    public static final RegistryObject<EntityType<MortarShellProjectile>> MORTAR_SHELL_ENTITY =
            ENTITY_TYPES.register("mortar_shell", () -> EntityType.Builder
                    .<MortarShellProjectile>of(MortarShellProjectile::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(10).updateInterval(1)
                    .build(TACZEnhancedPatch.MOD_ID + ":mortar_shell"));

    private MortarRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITY_TYPES.register(bus);
    }
}
