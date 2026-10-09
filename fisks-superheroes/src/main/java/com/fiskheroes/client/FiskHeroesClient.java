package com.fiskheroes.client;

import com.fiskheroes.FiskHeroes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class FiskHeroesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(FiskHeroes.GADGET, context -> new ThrownItemRenderer<>(context, 1.7F, true));
    }
}
