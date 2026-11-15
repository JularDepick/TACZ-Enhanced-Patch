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

/*
 * 幻机的实体与刷怪蛋注册。
 * 分类取怪物, 但只由遗迹补兵生成, 不注册自然生成规则; 模型沿用原版幻翼, 因此不新增模型层。
 */
public final class PhantomRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<EntityType<PhantomElite>> PHANTOM_ELITE = ENTITY_TYPES.register("phantom_elite",
            () -> EntityType.Builder.<PhantomElite>of(PhantomElite::new, MobCategory.MONSTER)
                    .sized(1.2F, 1.2F)
                    .clientTrackingRange(10)
                    .build(TACZEnhancedPatch.MOD_ID + ":phantom_elite"));

    public static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("phantom_elite_spawn_egg",
            () -> new ForgeSpawnEggItem(PHANTOM_ELITE, 0x2E4A4A, 0x9BE3D8, new Item.Properties()));

    private PhantomRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        ITEMS.register(bus);
    }
}
