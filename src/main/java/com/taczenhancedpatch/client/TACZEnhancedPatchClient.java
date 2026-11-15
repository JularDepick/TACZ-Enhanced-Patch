package com.taczenhancedpatch.client;

import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.client.render.GrenadeRenderer;
import com.taczenhancedpatch.client.render.GruntRenderer;
import com.taczenhancedpatch.client.render.PhantomEliteRenderer;
import com.taczenhancedpatch.entity.PhantomRegistry;
import com.taczenhancedpatch.grenade.GrenadeRegistry;
import com.taczenhancedpatch.shell.ShellItem;
import com.taczenhancedpatch.item.PatchItems;
import com.taczenhancedpatch.client.render.TankModel;
import com.taczenhancedpatch.client.render.TankRenderer;
import com.taczenhancedpatch.entity.GruntRegistry;
import com.taczenhancedpatch.entity.TankRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
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
        /* 迫击炮: 基座与炮筒分开渲染, 炮筒随角度旋转 */
        event.registerBlockEntityRenderer(com.taczenhancedpatch.mortar.MortarRegistry.MORTAR_ENTITY.get(),
                com.taczenhancedpatch.client.render.MortarBlockEntityRenderer::new);
        /* 手雷投掷物直接按物品渲染 */
        event.registerEntityRenderer(GrenadeRegistry.GRENADE.get(), GrenadeRenderer::new);
        /* 幻机沿用原版幻翼的模型, 只换贴图与实体类型 */
        event.registerEntityRenderer(PhantomRegistry.PHANTOM_ELITE.get(), PhantomEliteRenderer::new);
    }

    /* 手雷的配色层: 第 0 层是本体, 第 1 层按当前配色染色 */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof ShellItem shell)) {
                return -1;
            }
            int rgb = shell.settingsOf(stack).color().rgb();
            return 0xFF000000 | rgb;
        }, PatchItems.GRENADE.get());
    }

    /* 坦克精英怪的模型层 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TankModel.LAYER, TankModel::createBodyLayer);
    }
}