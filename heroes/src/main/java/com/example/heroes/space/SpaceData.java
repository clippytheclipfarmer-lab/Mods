package com.example.heroes.space;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Where each player launched from, so flying back into the same planet returns them to that spot. */
public final class SpaceData extends SavedData {
    public record Origin(ResourceLocation dimension, int x, int y, int z) {
    }

    private final Map<UUID, Origin> origins = new HashMap<>();

    public static SpaceData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SpaceData::load, SpaceData::new, "heroes_space");
    }

    public Origin origin(UUID player) {
        return origins.get(player);
    }

    public void setOrigin(UUID player, ResourceLocation dimension, BlockPos pos) {
        origins.put(player, new Origin(dimension, pos.getX(), pos.getY(), pos.getZ()));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        origins.forEach((id, o) -> {
            CompoundTag t = new CompoundTag();
            t.putString("Dimension", o.dimension().toString());
            t.putIntArray("Pos", new int[]{o.x(), o.y(), o.z()});
            all.put(id.toString(), t);
        });
        tag.put("Origins", all);
        return tag;
    }

    public static SpaceData load(CompoundTag tag) {
        SpaceData data = new SpaceData();
        CompoundTag all = tag.getCompound("Origins");
        for (String key : all.getAllKeys()) {
            CompoundTag t = all.getCompound(key);
            int[] p = t.getIntArray("Pos");
            data.origins.put(UUID.fromString(key), new Origin(new ResourceLocation(t.getString("Dimension")), p[0], p[1], p[2]));
        }
        return data;
    }
}
