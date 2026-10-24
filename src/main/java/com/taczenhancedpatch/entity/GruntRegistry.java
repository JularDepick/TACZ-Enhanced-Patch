package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 持枪小兵的实体与刷怪蛋注册。
 * 生物分类取生物, 与羊同属地表被动生成类别; 体型按小村民的观感设置。
 */
public final class GruntRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<EntityType<GunnerGrunt>> GUNNER_GRUNT = ENTITY_TYPES.register("gunner_grunt",
            () -> EntityType.Builder.of(GunnerGrunt::new, MobCategory.CREATURE)
                    .sized(0.45F, 0.95F)
                    .clientTrackingRange(10)
                    .build(TACZEnhancedPatch.MOD_ID + ":gunner_grunt"));

    public static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("gunner_grunt_spawn_egg",
            () -> new ForgeSpawnEggItem(GUNNER_GRUNT, 0x5A6B33, 0x23261A, new Item.Properties()));

    private GruntRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        ITEMS.register(bus);
    }
}
