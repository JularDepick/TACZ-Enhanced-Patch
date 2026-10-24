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
     * 模型坐标系的 Y 轴向下, 0 为车顶高度: 履带贴地, 车体在上, 炮塔再上。
     * 车体与炮塔都用多层小方块叠出斜面与倒角, 并补上侧裙板, 挡泥板, 负重轮,
     * 炮口制退器与抽烟装置, 避免只有几个大方块时的棱角感。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        /* 履带与负重轮 */
        root.addOrReplaceChild("tracks", CubeListBuilder.create()
                .texOffs(0, 148)
                .addBox(-27.0F, 14.0F, -26.0F, 10.0F, 10.0F, 52.0F)
                .addBox(17.0F, 14.0F, -26.0F, 10.0F, 10.0F, 52.0F)
                .texOffs(0, 148)
                .addBox(-26.0F, 16.0F, -21.0F, 8.0F, 6.0F, 6.0F)
                .addBox(-26.0F, 16.0F, -9.0F, 8.0F, 6.0F, 6.0F)
                .addBox(-26.0F, 16.0F, 3.0F, 8.0F, 6.0F, 6.0F)
                .addBox(-26.0F, 16.0F, 15.0F, 8.0F, 6.0F, 6.0F)
                .addBox(18.0F, 16.0F, -21.0F, 8.0F, 6.0F, 6.0F)
                .addBox(18.0F, 16.0F, -9.0F, 8.0F, 6.0F, 6.0F)
                .addBox(18.0F, 16.0F, 3.0F, 8.0F, 6.0F, 6.0F)
                .addBox(18.0F, 16.0F, 15.0F, 8.0F, 6.0F, 6.0F), PartPose.ZERO);

        /* 车体: 主装甲 + 前部斜面 + 侧裙板 + 挡泥板 + 发动机舱 */
        root.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-22.0F, 0.0F, -24.0F, 44.0F, 14.0F, 48.0F)
                .texOffs(0, 0)
                .addBox(-20.0F, 2.0F, -30.0F, 40.0F, 3.0F, 6.0F)
                .addBox(-18.0F, 5.0F, -28.0F, 36.0F, 3.0F, 4.0F)
                .addBox(-16.0F, 8.0F, -26.0F, 32.0F, 3.0F, 3.0F)
                .texOffs(0, 0)
                .addBox(-24.0F, 8.0F, -26.0F, 2.0F, 3.0F, 52.0F)
                .addBox(22.0F, 8.0F, -26.0F, 2.0F, 3.0F, 52.0F)
                .addBox(-24.0F, 11.0F, -20.0F, 1.0F, 5.0F, 40.0F)
                .addBox(23.0F, 11.0F, -20.0F, 1.0F, 5.0F, 40.0F)
                .texOffs(0, 0)
                .addBox(-18.0F, -3.0F, 12.0F, 36.0F, 4.0F, 10.0F)
                .addBox(-12.0F, -5.0F, 16.0F, 24.0F, 3.0F, 6.0F), PartPose.ZERO);

        /* 炮塔: 主体 + 前部切角 + 尾舱 + 舱盖 + 炮盾 */
        PartDefinition turret = root.addOrReplaceChild("turret", CubeListBuilder.create()
                .texOffs(0, 64)
                .addBox(-12.0F, -10.0F, -12.0F, 24.0F, 10.0F, 24.0F)
                .texOffs(0, 64)
                .addBox(-10.0F, -12.0F, -10.0F, 20.0F, 2.0F, 20.0F)
                .addBox(-8.0F, -14.0F, -8.0F, 16.0F, 2.0F, 16.0F)
                .texOffs(0, 64)
                .addBox(-10.0F, -8.0F, 12.0F, 20.0F, 6.0F, 8.0F)
                .addBox(-6.0F, -11.0F, 14.0F, 12.0F, 3.0F, 4.0F)
                .texOffs(0, 64)
                .addBox(-4.0F, -13.0F, -2.0F, 8.0F, 1.0F, 6.0F)
                .addBox(-6.0F, -9.0F, -15.0F, 12.0F, 8.0F, 3.0F), PartPose.offset(0.0F, 0.0F, 0.0F));

        /* 炮管: 身管 + 抽烟装置 + 炮口制退器 */
        turret.addOrReplaceChild("barrel", CubeListBuilder.create()
                .texOffs(0, 100)
                .addBox(-3.0F, -8.0F, -52.0F, 6.0F, 6.0F, 40.0F)
                .texOffs(0, 100)
                .addBox(-4.0F, -9.0F, -34.0F, 8.0F, 8.0F, 6.0F)
                .addBox(-4.0F, -9.0F, -56.0F, 8.0F, 8.0F, 6.0F), PartPose.ZERO);

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