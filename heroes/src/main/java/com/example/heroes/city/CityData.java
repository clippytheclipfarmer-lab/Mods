package com.example.heroes.city;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent city regions and their recorded damage, saved with the world. */
public final class CityData extends SavedData {
    private static final String ID = "heroes_cities";

    public final Map<String, CityRegion> regions = new LinkedHashMap<>();
    public final Map<String, CityRecords> records = new LinkedHashMap<>();
    /** Points of interest and residential zones registered through the API or commands. */
    public final Map<String, CityPoint> pois = new LinkedHashMap<>();
    public final Map<String, CityPoint> residentials = new LinkedHashMap<>();

    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIds = new Object2IntOpenHashMap<>();

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CityData::load, CityData::new, ID);
    }

    public int paletteId(BlockState state) {
        if (paletteIds.containsKey(state)) {
            return paletteIds.getInt(state);
        }
        int id = palette.size();
        palette.add(state);
        paletteIds.put(state, id);
        return id;
    }

    public BlockState paletteState(int id) {
        return palette.get(id);
    }

    public CityRecords records(String region) {
        return records.computeIfAbsent(region, k -> new CityRecords());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag regionList = new ListTag();
        regions.values().forEach(r -> regionList.add(r.save()));
        tag.put("Regions", regionList);

        ListTag paletteTag = new ListTag();
        palette.forEach(s -> paletteTag.add(NbtUtils.writeBlockState(s)));
        tag.put("Palette", paletteTag);

        CompoundTag recordsTag = new CompoundTag();
        for (Map.Entry<String, CityRecords> e : records.entrySet()) {
            CityRecords r = e.getValue();
            if (r.isEmpty()) {
                continue;
            }
            long[] keys = r.states.keySet().toLongArray();
            int[] values = new int[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = r.states.get(keys[i]);
            }
            CompoundTag rt = new CompoundTag();
            rt.putLongArray("Keys", keys);
            rt.putIntArray("States", values);
            ListTag bes = new ListTag();
            r.blockEntities.forEach((k, v) -> {
                CompoundTag b = new CompoundTag();
                b.putLong("Key", k);
                b.put("Tag", v);
                bes.add(b);
            });
            rt.put("BlockEntities", bes);
            recordsTag.put(e.getKey(), rt);
        }
        tag.put("Records", recordsTag);
        tag.put("Pois", savePoints(pois));
        tag.put("Residentials", savePoints(residentials));
        return tag;
    }

    private static ListTag savePoints(Map<String, CityPoint> points) {
        ListTag list = new ListTag();
        points.values().forEach(p -> list.add(p.save()));
        return list;
    }

    public static CityData load(CompoundTag tag) {
        CityData data = new CityData();
        for (Tag t : tag.getList("Regions", Tag.TAG_COMPOUND)) {
            CityRegion region = CityRegion.load((CompoundTag) t);
            data.regions.put(region.name, region);
        }
        for (Tag t : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) t);
            data.paletteIds.put(state, data.palette.size());
            data.palette.add(state);
        }
        for (Tag t : tag.getList("Pois", Tag.TAG_COMPOUND)) {
            CityPoint point = CityPoint.load((CompoundTag) t);
            data.pois.put(point.id(), point);
        }
        for (Tag t : tag.getList("Residentials", Tag.TAG_COMPOUND)) {
            CityPoint point = CityPoint.load((CompoundTag) t);
            data.residentials.put(point.id(), point);
        }
        CompoundTag recordsTag = tag.getCompound("Records");
        for (String name : recordsTag.getAllKeys()) {
            CompoundTag rt = recordsTag.getCompound(name);
            long[] keys = rt.getLongArray("Keys");
            int[] values = rt.getIntArray("States");
            CityRecords r = data.records(name);
            for (int i = 0; i < keys.length; i++) {
                r.states.put(keys[i], values[i]);
            }
            for (Tag t : rt.getList("BlockEntities", Tag.TAG_COMPOUND)) {
                CompoundTag b = (CompoundTag) t;
                r.blockEntities.put(b.getLong("Key"), b.getCompound("Tag"));
            }
        }
        return data;
    }
}
