package com.taczenhancedpatch.grenade;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/*
 * 手雷投掷物的实体注册。
 * 分类取杂项, 不参与自然生成, 也不需要刷怪蛋。
 */
public final class GrenadeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TACZEnhancedPatch.MOD_ID);

    public static final RegistryObject<EntityType<GrenadeProjectile>> GRENADE = ENTITY_TYPES.register("grenade",
            () -> EntityType.Builder.<GrenadeProjectile>of(GrenadeProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(TACZEnhancedPatch.MOD_ID + ":grenade"));

    private GrenadeRegistry() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
