package com.example.heroes.client;

import com.example.heroes.symbiote.SymbioteVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A villager skinned black with white eyes. */
public class SymbioteVillagerRenderer extends MobRenderer<SymbioteVillagerEntity, VillagerModel<SymbioteVillagerEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("heroes", "textures/entity/symbiote_villager.png");

    public SymbioteVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SymbioteVillagerEntity villager) {
        return TEXTURE;
    }
}
