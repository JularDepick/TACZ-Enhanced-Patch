package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 坦克精英怪的注册事件。
 * 属性与生成规则沿用原版怪物, 刷怪蛋进入创造模式的刷怪蛋分类。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TankRegistration {
    private TankRegistration() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TankRegistry.TANK_ELITE.get(), TankElite.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(TankRegistry.TANK_ELITE.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TankRegistry.SPAWN_EGG.get());
        }
    }
}
