package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.client.render.GruntRenderer;
import com.taczenhancedpatch.client.render.TankModel;
import com.taczenhancedpatch.client.render.TankRenderer;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端入口。
 * 仅在物理客户端加载, 用于注册依赖客户端环境的补丁内容, 原版按键绑定与实体渲染。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TACZEnhancedPatchClient {
    private TACZEnhancedPatchClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(RuinCompassAngle::register);
        TACZEnhancedPatch.LOGGER.debug("{} client setup finished", TACZEnhancedPatch.MOD_NAME);
    }

    /* 把配置入口按键注册到原版按键设置 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ConfigShortcutHandler.OPEN_CONFIG);
    }

    /* 持枪小兵与坦克精英怪的渲染器 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GruntRegistry.GUNNER_GRUNT.get(), GruntRenderer::new);
        event.registerEntityRenderer(TankRegistry.TANK_ELITE.get(), TankRenderer::new);
    }

    /* 坦克精英怪的模型层 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TankModel.LAYER, TankModel::createBodyLayer);
    }
}