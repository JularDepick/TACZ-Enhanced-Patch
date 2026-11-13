package com.taczenhancedpatch.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.integration.TaczApi;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 非人形生物的人形持械图层。
 * 父渲染器没有手持物品图层时, 由本图层补一具人形骨架, 再把主手物品画在骨架的手上;
 * 骨架按碰撞箱高度等比缩放, 缩放基准点是脚底, 保证骨架始终站在地面。
 */
public class ArmedHumanoidLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /* 人形骨架贴图, 64x64 标准人形展开图 */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":textures/entity/armed_humanoid.png");
    /* 图层坐标系里人形模型的脚底高度, 人形模型总高 32 像素 */
    private static final float FOOT_LEVEL = 1.5F;
    /* 骨架缩放区间, 下限保证微型生物可见, 上限避免巨型生物把骨架拉变形 */
    private static final float MIN_SCALE = 0.45F;
    private static final float MAX_SCALE = 1.15F;
    /* 标准成人碰撞箱高度 */
    private static final float ADULT_HEIGHT = 1.8F;
    /* 低于该高度的生物不参与, 骨架会比枪械还小 */
    private static final float MIN_ENTITY_HEIGHT = 0.6F;

    private final HumanoidModel<LivingEntity> skeleton;
    private final ItemInHandRenderer itemInHand;

    public ArmedHumanoidLayer(RenderLayerParent<T, M> parent, HumanoidModel<LivingEntity> skeleton,
                              ItemInHandRenderer itemInHand) {
        super(parent);
        this.skeleton = skeleton;
        this.itemInHand = itemInHand;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack held = entity.getMainHandItem();
        if (entity.isInvisible() || entity.getBbHeight() < MIN_ENTITY_HEIGHT || !TaczApi.isGun(held)) {
            return;
        }
        HumanoidModel<LivingEntity> model = this.skeleton;
        model.young = entity.isBaby();
        model.crouching = entity.isCrouching();
        model.riding = entity.isPassenger();
        model.attackTime = entity.getAttackAnim(partialTick);
        /* 主手臂前伸持械, 副手保持自然下垂 */
        model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float scale = scaleFor(entity);
        poseStack.pushPose();
        /* 图层坐标系 y 轴朝下, 脚底位于 1.5, 先把原点移到脚底再缩放, 保证缩放不抬脚 */
        poseStack.translate(0.0F, FOOT_LEVEL, 0.0F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, -FOOT_LEVEL, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        model.renderToBuffer(poseStack, consumer, packedLight, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        renderHeldItem(entity, held, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /* 复用原版手持物品图层的挂点与朝向, 让枪械落在骨架的右手上 */
    private void renderHeldItem(T entity, ItemStack held, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        this.skeleton.translateToHand(HumanoidArm.RIGHT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(0.0625F, 0.125F, -0.625F);
        this.itemInHand.renderItem(entity, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
                poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /* 按碰撞箱高度换算骨架缩放, 幼年个体再折半 */
    private static float scaleFor(LivingEntity entity) {
        float scale = entity.getBbHeight() / ADULT_HEIGHT;
        if (entity.isBaby()) {
            scale *= 0.5F;
        }
        return Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
    }
}
