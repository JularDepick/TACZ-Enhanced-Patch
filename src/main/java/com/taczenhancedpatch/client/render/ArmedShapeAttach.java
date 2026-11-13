package com.taczenhancedpatch.client.render;

import com.taczenhancedpatch.TACZEnhancedPatch;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 非人形持械建模的挂载入口。
 * 注册人形骨架的模型层, 并在渲染器登记完成后遍历全部实体类型,
 * 给模型不是人形的生物渲染器补一层人形持械图层。
 */
@Mod.EventBusSubscriber(modid = TACZEnhancedPatch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArmedShapeAttach {
    /* 自持的人形骨架模型层, 不复用玩家模型层, 避免与玩家渲染器共用同一批部件 */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":armed_humanoid"), "main");
    /* 已挂过图层的渲染器, 资源重载会再次触发事件, 按实例身份记录保证只挂一次 */
    private static final Set<EntityRenderer<?>> ATTACHED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private ArmedShapeAttach() {
    }

    /* 人形骨架的模型层定义, 与玩家模型同构, 但不带外层扩张 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
    }

    /* 渲染器登记完成时给非人形渲染器挂图层 */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        ItemInHandRenderer itemInHand = event.getContext().getItemInHandRenderer();
        HumanoidModel<LivingEntity> skeleton = new HumanoidModel<>(event.getEntityModels().bakeLayer(LAYER));
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            EntityRenderer<?> renderer = event.getEntityRenderer(type);
            if (renderer == null || !ATTACHED.add(renderer)) {
                continue;
            }
            /* 非生物实体没有生物渲染器, 人形模型自带手持物品图层, 两类都跳过 */
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
                continue;
            }
            if (living.getModel() instanceof HumanoidModel) {
                continue;
            }
            attach(living, skeleton, itemInHand);
        }
    }

    /* 渲染器的泛型参数在运行期已擦除, 统一收窄成人形骨架所需的类型后再挂载 */
    @SuppressWarnings("unchecked")
    private static void attach(LivingEntityRenderer<?, ?> renderer, HumanoidModel<LivingEntity> skeleton,
                               ItemInHandRenderer itemInHand) {
        LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> target =
                (LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>>) renderer;
        target.addLayer(new ArmedHumanoidLayer<>(target, skeleton, itemInHand));
    }
}
