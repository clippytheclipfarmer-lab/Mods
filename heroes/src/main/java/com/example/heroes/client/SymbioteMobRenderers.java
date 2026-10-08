package com.example.heroes.client;

import com.example.heroes.symbiote.klyntar.KnullEntity;
import com.example.heroes.symbiote.klyntar.SymbioteBruteEntity;
import com.example.heroes.symbiote.klyntar.SymbioteCrawlerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Renderers for Klyntar's creatures: vanilla models with black skins. */
public final class SymbioteMobRenderers {
    private SymbioteMobRenderers() {
    }

    /** Spider model, black skin. */
    public static class Crawler extends SpiderRenderer<SymbioteCrawlerEntity> {
        private static final ResourceLocation TEXTURE = new ResourceLocation("heroes", "textures/entity/symbiote_crawler.png");

        public Crawler(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(SymbioteCrawlerEntity entity) {
            return TEXTURE;
        }
    }

    /** Zombie model, enlarged, for the brute and for Knull. */
    public static class Giant extends ZombieRenderer {
        private static final ResourceLocation BRUTE = new ResourceLocation("heroes", "textures/entity/symbiote_brute.png");
        private static final ResourceLocation KNULL = new ResourceLocation("heroes", "textures/entity/knull.png");

        public Giant(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(Zombie zombie, PoseStack pose, float partialTick) {
            float s = zombie instanceof KnullEntity ? KnullEntity.SCALE : zombie instanceof SymbioteBruteEntity ? SymbioteBruteEntity.SCALE : 1F;
            pose.scale(s, s, s);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie zombie) {
            return zombie instanceof KnullEntity ? KNULL : BRUTE;
        }
    }
}
