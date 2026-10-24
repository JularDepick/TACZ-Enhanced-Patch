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
 * 坦克精英怪的实体与刷怪蛋注册。
 * 分类取怪物, 生成位置与亮度规则沿用原版怪物, 免疫火焰以体现装甲载具。
 */
public final class TankRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<EntityType<TankElite>> TANK_ELITE = ENTITY_TYPES.register("tank_elite",
            () -> EntityType.Builder.of(TankElite::new, MobCategory.MONSTER)
                    .sized(3.4F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(TACZEnhancedPatch.MOD_ID + ":tank_elite"));

    public static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("tank_elite_spawn_egg",
            () -> new ForgeSpawnEggItem(TANK_ELITE, 0x3E4A2A, 0x1B1F14, new Item.Properties()));

    private TankRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
        ITEMS.register(bus);
    }
}
