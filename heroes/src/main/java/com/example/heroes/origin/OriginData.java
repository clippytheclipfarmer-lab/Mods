package com.example.heroes.origin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every player's character sheet, saved with the world. */
public final class OriginData extends SavedData {
    private final Map<UUID, Sheet> sheets = new HashMap<>();

    public static OriginData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OriginData::load, OriginData::new, "heroes_origin");
    }

    public Sheet sheet(UUID id) {
        return sheets.get(id);
    }

    public void put(UUID id, Sheet sheet) {
        sheets.put(id, sheet);
        setDirty();
    }

    public void remove(UUID id) {
        sheets.remove(id);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        sheets.forEach((id, sheet) -> all.put(id.toString(), sheet.save()));
        tag.put("Sheets", all);
        return tag;
    }

    public static OriginData load(CompoundTag tag) {
        OriginData data = new OriginData();
        CompoundTag all = tag.getCompound("Sheets");
        for (String key : all.getAllKeys()) {
            try {
                data.sheets.put(UUID.fromString(key), Sheet.load(all.getCompound(key)));
            } catch (Exception ignored) {
                // a corrupted entry is dropped
            }
        }
        return data;
    }
}
