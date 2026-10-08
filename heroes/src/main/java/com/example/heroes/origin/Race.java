package com.example.heroes.origin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A race loaded from {@code data/<ns>/heroes/races/<id>.json}, with its subtypes. */
public final class Race {
    /** An attribute change the race grants. */
    public record AttributeBonus(ResourceLocation attribute, double amount, String operation) {
    }

    /** A variant of a race (for example the Asgardian Warrior). Rolled by weight. */
    public static final class Subtype {
        public final String id;
        public final String name;
        public final int weight;
        public final int[] bonuses = new int[6];
        public final Map<String, Double> traits = new HashMap<>();
        public final Set<String> flags = new HashSet<>();

        Subtype(JsonObject json) {
            id = json.get("id").getAsString();
            name = json.has("name") ? json.get("name").getAsString() : id;
            weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
            readBonuses(json, bonuses);
            readTraits(json, traits);
            if (json.has("flags")) {
                for (JsonElement e : json.getAsJsonArray("flags")) {
                    flags.add(e.getAsString());
                }
            }
        }
    }

    public final ResourceLocation id;
    public final String name;
    public final String description;
    public final int weight;
    /** Height range in blocks (a player is 1.8). 0 = normal size. */
    public final double heightMin;
    public final double heightMax;
    public final int[] bonuses = new int[6];
    public final List<AttributeBonus> attributes = new ArrayList<>();
    public final Map<String, Double> traits = new HashMap<>();
    public final List<Subtype> subtypes = new ArrayList<>();
    /** Flags every member of the race has (for example cold_immune). */
    public final Set<String> flags = new HashSet<>();
    /** gender (male/female) -> Palladium power that wears this race's model; empty = the normal player model. */
    public final Map<String, ResourceLocation> models = new HashMap<>();

    Race(ResourceLocation id, JsonObject json) {
        this.id = id;
        name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        description = json.has("description") ? json.get("description").getAsString() : "";
        weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        if (json.has("height_blocks")) {
            JsonObject h = json.getAsJsonObject("height_blocks");
            heightMin = h.get("min").getAsDouble();
            heightMax = h.get("max").getAsDouble();
        } else {
            heightMin = 0;
            heightMax = 0;
        }
        readBonuses(json, bonuses);
        readTraits(json, traits);
        if (json.has("attributes")) {
            for (JsonElement e : json.getAsJsonArray("attributes")) {
                JsonObject a = e.getAsJsonObject();
                attributes.add(new AttributeBonus(new ResourceLocation(a.get("attribute").getAsString()), a.get("amount").getAsDouble(),
                        a.has("operation") ? a.get("operation").getAsString() : "addition"));
            }
        }
        if (json.has("flags")) {
            for (JsonElement e : json.getAsJsonArray("flags")) {
                flags.add(e.getAsString());
            }
        }
        if (json.has("models")) {
            for (var entry : json.getAsJsonObject("models").entrySet()) {
                models.put(entry.getKey(), new ResourceLocation(entry.getValue().getAsString()));
            }
        }
        JsonArray subs = json.has("subtypes") ? json.getAsJsonArray("subtypes") : new JsonArray();
        for (JsonElement e : subs) {
            subtypes.add(new Subtype(e.getAsJsonObject()));
        }
        if (subtypes.isEmpty()) {
            JsonObject plain = new JsonObject();
            plain.addProperty("id", "common");
            plain.addProperty("name", name);
            subtypes.add(new Subtype(plain));
        }
    }

    public Subtype subtype(String id) {
        for (Subtype s : subtypes) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return subtypes.get(0);
    }

    private static void readBonuses(JsonObject json, int[] out) {
        if (json.has("ability_bonuses")) {
            JsonObject b = json.getAsJsonObject("ability_bonuses");
            for (Ability5e a : Ability5e.values()) {
                if (b.has(a.key)) {
                    out[a.ordinal()] = b.get(a.key).getAsInt();
                }
            }
        }
    }

    private static void readTraits(JsonObject json, Map<String, Double> out) {
        if (json.has("traits")) {
            for (var entry : json.getAsJsonObject("traits").entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
    }
}
