package com.example.heroes.city;

import net.minecraft.nbt.CompoundTag;

/** A point of interest or a residential zone: an id, a position, a label (the display name, or the tier for residential zones) and a dimension. */
public record CityPoint(String id, int x, int y, int z, String label, String dimension) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putIntArray("Pos", new int[]{x, y, z});
        tag.putString("Label", label);
        tag.putString("Dimension", dimension);
        return tag;
    }

    public static CityPoint load(CompoundTag tag) {
        int[] p = tag.getIntArray("Pos");
        return new CityPoint(tag.getString("Id"), p[0], p[1], p[2], tag.getString("Label"),
                tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld");
    }
}
