package com.example.heroes.client;

import com.example.heroes.pod.SpacePodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/** Placeholder pod: a capsule body with a nose, canopy, two fins and an engine. Texture: 128x64. */
public class SpacePodModel extends EntityModel<SpacePodEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("heroes", "space_pod"), "main");
    private final ModelPart root;

    public SpacePodModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -10F, -10F, 16F, 10F, 20F), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 32).addBox(-5F, -8F, -14F, 10F, 6F, 4F), PartPose.ZERO);
        root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(30, 32).addBox(-4F, -14F, -6F, 8F, 4F, 10F), PartPose.ZERO);
        root.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(72, 0).addBox(8F, -4F, 4F, 4F, 2F, 8F), PartPose.ZERO);
        root.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(72, 0).addBox(-12F, -4F, 4F, 4F, 2F, 8F), PartPose.ZERO);
        root.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(72, 12).addBox(-4F, -7F, 10F, 8F, 4F, 2F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(SpacePodEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buffer, light, overlay, r, g, b, a);
    }
}
