package com.example.heroes.origin;

import com.example.heroes.HeroesMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** All loaded races ({@code data/<ns>/heroes/races/*.json}); reloads with {@code /reload}. */
public final class Races {
    private static volatile Map<ResourceLocation, Race> races = Map.of();

    private Races() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Loader());
    }

    public static Collection<Race> all() {
        return races.values();
    }

    @Nullable
    public static Race get(ResourceLocation id) {
        return races.get(id);
    }

    /** Accepts a full id or a short name such as "asgardian". */
    @Nullable
    public static Race find(String name) {
        for (Race race : races.values()) {
            if (race.id.toString().equals(name) || race.id.getPath().equals(name)) {
                return race;
            }
        }
        return null;
    }

    /** A race picked by weight. */
    @Nullable
    public static Race randomRace(RandomSource random) {
        int total = races.values().stream().mapToInt(r -> Math.max(0, r.weight)).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Race race : races.values()) {
            roll -= Math.max(0, race.weight);
            if (roll < 0) {
                return race;
            }
        }
        return null;
    }

    public static Race.Subtype randomSubtype(Race race, RandomSource random) {
        int total = race.subtypes.stream().mapToInt(s -> Math.max(0, s.weight)).sum();
        if (total <= 0) {
            return race.subtypes.get(0);
        }
        int roll = random.nextInt(total);
        for (Race.Subtype s : race.subtypes) {
            roll -= Math.max(0, s.weight);
            if (roll < 0) {
                return s;
            }
        }
        return race.subtypes.get(0);
    }

    private static final class Loader extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
        Loader() {
            super(new GsonBuilder().create(), "heroes/races");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Race> loaded = new LinkedHashMap<>();
            files.forEach((id, json) -> {
                try {
                    loaded.put(id, new Race(id, json.getAsJsonObject()));
                } catch (Exception e) {
                    HeroesMod.LOGGER.error("Could not load race {}: {}", id, e.getMessage());
                }
            });
            races = loaded;
            HeroesMod.LOGGER.info("Loaded {} races", loaded.size());
        }

        @Override
        public ResourceLocation getFabricId() {
            return new ResourceLocation("heroes", "races");
        }
    }
}
