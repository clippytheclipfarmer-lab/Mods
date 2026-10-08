package com.example.heroes.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A marked part of a map (for example a city) whose destroyed blocks regrow. */
public final class CityRegion {
    public final String name;
    public final ResourceKey<Level> dimension;
    public final BlockPos min;
    public final BlockPos max;

    /** Target time to rebuild a large amount of damage. Small damage rebuilds at a minimum pace. */
    public int rebuildSeconds = 600;
    /** Seconds without new damage before rebuilding starts. */
    public int idleSeconds = 10;
    public int maxBuilders = 6;
    public boolean sounds = true;
    /** The district this zone belongs to (for example "downtown" or "piers"); defaults to the zone's own id. */
    public String district;
    /** Repair speed in blocks per second; 0 = derive it from rebuildSeconds. */
    public int blocksPerSecond = 0;
    /** "bottom_up" (like a construction site) or "random" (scattered repairs). */
    public String order = "bottom_up";
    /** "powers": explosions and hero powers (everything except a player breaking blocks by hand); "all": every real destruction. */
    public String tracking = "powers";
    /** Repair effect: "block" (crumbs of the block), "marker" (a block marker), "cloud" or "none". */
    public String particles = "block";

    public CityRegion(String name, ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        this.name = name;
        this.district = name;
        this.dimension = dimension;
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX()
                && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.putIntArray("Min", new int[]{min.getX(), min.getY(), min.getZ()});
        tag.putIntArray("Max", new int[]{max.getX(), max.getY(), max.getZ()});
        tag.putInt("RebuildSeconds", rebuildSeconds);
        tag.putInt("IdleSeconds", idleSeconds);
        tag.putInt("MaxBuilders", maxBuilders);
        tag.putBoolean("Sounds", sounds);
        tag.putString("District", district);
        tag.putInt("BlocksPerSecond", blocksPerSecond);
        tag.putString("Order", order);
        tag.putString("Tracking", tracking);
        tag.putString("Particles", particles);
        return tag;
    }

    public static CityRegion load(CompoundTag tag) {
        int[] a = tag.getIntArray("Min");
        int[] b = tag.getIntArray("Max");
        CityRegion region = new CityRegion(tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dimension"))),
                new BlockPos(a[0], a[1], a[2]), new BlockPos(b[0], b[1], b[2]));
        region.rebuildSeconds = tag.getInt("RebuildSeconds");
        region.idleSeconds = tag.getInt("IdleSeconds");
        region.maxBuilders = tag.getInt("MaxBuilders");
        region.sounds = tag.getBoolean("Sounds");
        region.district = tag.contains("District") ? tag.getString("District") : region.name;
        region.blocksPerSecond = tag.getInt("BlocksPerSecond");
        region.order = tag.contains("Order") ? tag.getString("Order") : "bottom_up";
        region.tracking = tag.contains("Tracking") ? tag.getString("Tracking") : "all"; // zones saved before this option recorded everything
        region.particles = tag.contains("Particles") ? tag.getString("Particles") : "block";
        return region;
    }
}
