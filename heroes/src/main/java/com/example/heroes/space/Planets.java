package com.example.heroes.space;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All loaded planet definitions ({@code data/<ns>/heroes/planets/*.json}); reloads with {@code /reload}. */
public final class Planets {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("heroes", "space"));

    private static volatile Map<ResourceLocation, PlanetDef> planets = Map.of();

    private Planets() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Loader());
    }

    public static Collection<PlanetDef> all() {
        return planets.values();
    }

    @Nullable
    public static PlanetDef get(ResourceLocation id) {
        return planets.get(id);
    }

    /** Planet ids matching a (possibly short) name such as "moon" or "heroes:moon". */
    @Nullable
    public static PlanetDef find(String name) {
        for (PlanetDef def : planets.values()) {
            if (def.id.toString().equals(name) || def.id.getPath().equals(name)) {
                return def;
            }
        }
        return null;
    }

    /** The planet whose own dimension this is (not the station, which lives in space). */
    @Nullable
    public static PlanetDef forDimension(ResourceKey<Level> dimension) {
        for (PlanetDef def : planets.values()) {
            if (!def.station && def.dimension.equals(dimension)) {
                return def;
            }
        }
        return null;
    }

    /** Everything that is drawn into the space dimension. */
    public static List<PlanetDef> inSpace() {
        return new ArrayList<>(planets.values());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();

        Loader() {
            super(GSON, "heroes/planets");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, PlanetDef> loaded = new LinkedHashMap<>();
            files.forEach((id, json) -> {
                try {
                    loaded.put(id, PlanetDef.fromJson(id, json.getAsJsonObject()));
                } catch (Exception e) {
                    com.example.heroes.HeroesMod.LOGGER.error("Could not load planet {}: {}", id, e.getMessage());
                }
            });
            planets = loaded;
            com.example.heroes.HeroesMod.LOGGER.info("Loaded {} planets", loaded.size());
        }

        @Override
        public ResourceLocation getFabricId() {
            return new ResourceLocation("heroes", "planets");
        }
    }
}
