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

    public CityRegion(String name, ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        this.name = name;
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
        return region;
    }
}
