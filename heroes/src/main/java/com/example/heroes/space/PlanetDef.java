package com.example.heroes.space;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A destination, defined in {@code data/<namespace>/heroes/planets/<name>.json}. It places a sphere (or station)
 * in the space dimension and describes the conditions on the planet's own dimension.
 */
public final class PlanetDef {
    public enum Hazard { NONE, HEAT, COLD }

    /** What the star map shows and what you can do there: only planets can be landed on. */
    public enum Kind { PLANET, STATION, STAR, BLACK_HOLE }

    public final ResourceLocation id;
    public final String name;
    public final Kind kind;
    public final boolean station;
    public final int color;
    /** Optional flat ring around the body (accretion disc), or null. */
    @Nullable
    public final Ring ring;
    public final ResourceKey<Level> dimension;
    public final Vec3 position;
    public final int radius;
    public final BlockState surface;
    public final BlockState subsurface;
    public final BlockState core;
    @Nullable
    public final BlockState accent;
    public final double accentScale;
    public final float gravity;
    public final boolean oxygen;
    public final Hazard hazard;
    public final int landingX;
    public final int landingZ;
    public final boolean canLaunch;
    /** Altitude at which flying up launches into space; -1 = a few blocks under the build limit. */
    public final int launchAltitude;

    private PlanetDef(ResourceLocation id, JsonObject json) {
        this.id = id;
        this.name = GsonHelper.getAsString(json, "name", id.getPath());
        String kindName = GsonHelper.getAsString(json, "kind", "station".equals(GsonHelper.getAsString(json, "shape", "")) ? "station" : "planet");
        try {
            this.kind = Kind.valueOf(kindName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown kind '" + kindName + "' (planet, station, star or black_hole)");
        }
        this.station = this.kind == Kind.STATION;
        int defaultColor = switch (this.kind) {
            case PLANET -> 0x5fa8ff;
            case STATION -> 0xcccccc;
            case STAR -> 0xffd84a;
            case BLACK_HOLE -> 0x8a3dff;
        };
        String colorText = GsonHelper.getAsString(json, "color", "");
        this.color = colorText.isEmpty() ? defaultColor : Integer.parseInt(colorText.replace("#", ""), 16);
        if (json.has("ring")) {
            JsonObject r = GsonHelper.getAsJsonObject(json, "ring");
            this.ring = new Ring(GsonHelper.getAsInt(r, "inner_radius"), GsonHelper.getAsInt(r, "outer_radius"),
                    GsonHelper.getAsInt(r, "thickness", 2), block(r, "block", "minecraft:orange_concrete"));
        } else {
            this.ring = null;
        }
        this.dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(GsonHelper.getAsString(json, "dimension", "heroes:space")));
        JsonArray pos = GsonHelper.getAsJsonArray(json, "position");
        this.position = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
        this.radius = GsonHelper.getAsInt(json, "radius", 30);
        JsonObject blocks = GsonHelper.getAsJsonObject(json, "blocks", new JsonObject());
        this.surface = block(blocks, "surface", "minecraft:stone");
        this.subsurface = block(blocks, "subsurface", "minecraft:stone");
        this.core = block(blocks, "core", "minecraft:stone");
        this.accent = blocks.has("accent") ? block(blocks, "accent", "minecraft:stone") : null;
        this.accentScale = GsonHelper.getAsDouble(blocks, "accent_scale", 16.0);
        this.gravity = GsonHelper.getAsFloat(json, "gravity", 1.0F);
        this.oxygen = GsonHelper.getAsBoolean(json, "oxygen", true);
        String hz = GsonHelper.getAsString(json, "hazard", "none").toUpperCase(java.util.Locale.ROOT);
        try {
            this.hazard = Hazard.valueOf(hz);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown hazard '" + hz + "' (use none, heat or cold)");
        }
        if (json.has("landing")) {
            JsonArray l = GsonHelper.getAsJsonArray(json, "landing");
            this.landingX = l.get(0).getAsInt();
            this.landingZ = l.get(1).getAsInt();
        } else {
            this.landingX = 0;
            this.landingZ = 0;
        }
        this.canLaunch = GsonHelper.getAsBoolean(json, "can_launch", true);
        this.launchAltitude = GsonHelper.getAsInt(json, "launch_altitude", -1);
    }

    public static PlanetDef fromJson(ResourceLocation id, JsonObject json) {
        return new PlanetDef(id, json);
    }

    private static BlockState block(JsonObject blocks, String key, String fallback) {
        ResourceLocation rl = new ResourceLocation(GsonHelper.getAsString(blocks, key, fallback));
        if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
            throw new JsonParseException("Unknown block '" + rl + "' in planet blocks." + key);
        }
        return BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
    }

    public record Ring(int inner, int outer, int thickness, BlockState block) {
    }

    public boolean landable() {
        return kind == Kind.PLANET;
    }

    /** Planet radius or half-size of the station: the outermost distance from the center that is part of it. */
    public int extent() {
        return ring != null ? Math.max(radius, ring.outer()) : radius;
    }
}
