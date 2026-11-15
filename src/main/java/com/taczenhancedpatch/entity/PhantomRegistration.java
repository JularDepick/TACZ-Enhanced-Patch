package com.taczenhancedpatch.entity;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * 幻机注册事件。
 * 只登记属性与刷怪蛋: 幻机不进自然生成, 生成入口在遗迹补兵处。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhantomRegistration {
    private PhantomRegistration() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(PhantomRegistry.PHANTOM_ELITE.get(), PhantomElite.createAttributes().build());
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(PhantomRegistry.SPAWN_EGG.get());
        }
    }
}
