package com.example.heroes.client;

import com.example.heroes.pod.SpacePodEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class SpacePodRenderer extends EntityRenderer<SpacePodEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("heroes", "textures/entity/space_pod.png");
    private final SpacePodModel model;

    public SpacePodRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpacePodModel(context.bakeLayer(SpacePodModel.LAYER));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(SpacePodEntity pod, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0, 1.2, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(partialTicks, pod.yRotO, pod.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, pod.xRotO, pod.getXRot())));
        pose.scale(-1.1F, -1.1F, 1.1F);
        VertexConsumer consumer = buffers.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
        super.render(pod, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpacePodEntity pod) {
        return TEXTURE;
    }
}
