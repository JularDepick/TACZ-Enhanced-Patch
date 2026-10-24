package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 持枪小兵注册事件。
 * 负责属性, 生成规则与创造模式物品页里的刷怪蛋, 生成规则与羊一致: 地表, 亮度足够即可。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GruntRegistration {
    private GruntRegistration() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GruntRegistry.GUNNER_GRUNT.get(), GunnerGrunt.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(GruntRegistry.GUNNER_GRUNT.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(GruntRegistry.SPAWN_EGG.get());
        }
    }
}
