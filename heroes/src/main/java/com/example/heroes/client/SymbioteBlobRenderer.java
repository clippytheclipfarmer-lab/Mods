package com.example.heroes.client;

import com.example.heroes.symbiote.SymbioteBlobEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SymbioteBlobRenderer extends MobRenderer<SymbioteBlobEntity, SymbioteBlobModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("heroes", "textures/entity/symbiote_blob.png");

    public SymbioteBlobRenderer(EntityRendererProvider.Context context) {
        super(context, new SymbioteBlobModel(context.bakeLayer(SymbioteBlobModel.LAYER)), 0.6F);
    }

    @Override
    protected void scale(SymbioteBlobEntity blob, PoseStack pose, float partialTick) {
        float s = 0.9F + 0.15F * blob.getBlobSize();
        pose.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(SymbioteBlobEntity blob) {
        return TEXTURE;
    }
}
