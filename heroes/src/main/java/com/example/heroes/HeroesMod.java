package com.example.heroes;

import com.example.heroes.hulk.HulkHero;
import com.example.heroes.viltrumite.ViltrumiteHero;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;

/**
 * Superhero pack for Palladium. Each hero lives in its own package and Palladium namespace
 * (so data/assets are under {@code data/<hero>/...}); add a hero by writing a class like {@link HulkHero}.
 */
public class HeroesMod implements ModInitializer {
    public static final String MOD_ID = "heroes";
    public static final String HULK = "hulk";
    public static final String VILTRUMITE = "viltrumite";

    public static ResourceLocation hulk(String path) {
        return new ResourceLocation(HULK, path);
    }

    public static ResourceLocation viltrumite(String path) {
        return new ResourceLocation(VILTRUMITE, path);
    }

    @Override
    public void onInitialize() {
        HulkHero.init();
        ViltrumiteHero.init();
    }
}
