package com.example.heroes.client;

import com.example.heroes.symbiote.SymbioteBlobEntity;
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

/** A lumpy black mass with two white eyes. Texture 64x32. */
public class SymbioteBlobModel extends EntityModel<SymbioteBlobEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("heroes", "symbiote_blob"), "main");
    private final ModelPart root;

    public SymbioteBlobModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartPose ground = PartPose.offset(0F, 24F, 0F);
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -8F, -6F, 12F, 8F, 12F), ground);
        root.addOrReplaceChild("lump", CubeListBuilder.create().texOffs(0, 20).addBox(-4F, -12F, -3F, 8F, 4F, 8F), ground);
        root.addOrReplaceChild("eye_left", CubeListBuilder.create().texOffs(48, 0).addBox(1F, -7F, -6.5F, 2F, 2F, 1F), ground);
        root.addOrReplaceChild("eye_right", CubeListBuilder.create().texOffs(48, 0).addBox(-3F, -7F, -6.5F, 2F, 2F, 1F), ground);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(SymbioteBlobEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // A slow wobble.
        float wobble = (float) Math.sin(ageInTicks * 0.15F) * 0.04F;
        root.yScale = 1.0F + wobble;
        root.xScale = 1.0F - wobble / 2;
        root.zScale = 1.0F - wobble / 2;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buffer, light, overlay, r, g, b, a);
    }
}
