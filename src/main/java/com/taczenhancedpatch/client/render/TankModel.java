package com.taczenhancedpatch.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taczenhancedpatch.TACZEnhancedPatch;
import com.taczenhancedpatch.entity.TankElite;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 坦克精英怪模型。
 * 由履带, 车体, 炮塔与炮管四个部分拼成, 炮塔跟随头部偏航旋转, 炮管跟随俯仰抬高或压低。
 */
public class TankModel extends EntityModel<TankElite> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TACZEnhancedPatch.MOD_ID + ":tank_elite"), "main");

    private final ModelPart root;
    private final ModelPart turret;
    private final ModelPart barrel;

    public TankModel(ModelPart root) {
        this.root = root;
        this.turret = root.getChild("turret");
        this.barrel = this.turret.getChild("barrel");
    }

    /*
     * 模型坐标系的 Y 轴向下, 0 为车顶高度: 履带贴地, 车体压在履带上, 炮塔再叠在车体顶部。
     * 各立方体的尺寸与 UV 偏移保持不变, 只调整纵向位置, 贴图无需改动。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("tracks", CubeListBuilder.create()
                .texOffs(0, 148)
                .addBox(-27.0F, 14.0F, -26.0F, 10.0F, 10.0F, 52.0F)
                .addBox(17.0F, 14.0F, -26.0F, 10.0F, 10.0F, 52.0F), PartPose.ZERO);
        root.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-22.0F, 0.0F, -24.0F, 44.0F, 14.0F, 48.0F), PartPose.ZERO);
        PartDefinition turret = root.addOrReplaceChild("turret", CubeListBuilder.create()
                .texOffs(0, 64)
                .addBox(-12.0F, -10.0F, -12.0F, 24.0F, 10.0F, 24.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        turret.addOrReplaceChild("barrel", CubeListBuilder.create()
                .texOffs(0, 100)
                .addBox(-3.0F, -8.0F, -52.0F, 6.0F, 6.0F, 40.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(TankElite entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.turret.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.barrel.xRot = Mth.clamp(headPitch, -20.0F, 12.0F) * Mth.DEG_TO_RAD;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
