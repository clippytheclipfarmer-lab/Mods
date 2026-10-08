package com.example.heroes.symbiote.klyntar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** What has been built on Klyntar and whether Knull has been awakened or beaten. */
public final class KlyntarData extends SavedData {
    public boolean built;
    @Nullable
    public BlockPos hiveCore;
    @Nullable
    public BlockPos altar;
    public boolean knullSpawned;
    public boolean knullDefeated;

    public static KlyntarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KlyntarData::load, KlyntarData::new, "heroes_klyntar");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Built", built);
        tag.putBoolean("KnullSpawned", knullSpawned);
        tag.putBoolean("KnullDefeated", knullDefeated);
        if (hiveCore != null) {
            tag.put("HiveCore", NbtUtils.writeBlockPos(hiveCore));
        }
        if (altar != null) {
            tag.put("Altar", NbtUtils.writeBlockPos(altar));
        }
        return tag;
    }

    public static KlyntarData load(CompoundTag tag) {
        KlyntarData data = new KlyntarData();
        data.built = tag.getBoolean("Built");
        data.knullSpawned = tag.getBoolean("KnullSpawned");
        data.knullDefeated = tag.getBoolean("KnullDefeated");
        if (tag.contains("HiveCore")) {
            data.hiveCore = NbtUtils.readBlockPos(tag.getCompound("HiveCore"));
        }
        if (tag.contains("Altar")) {
            data.altar = NbtUtils.readBlockPos(tag.getCompound("Altar"));
        }
        return data;
    }
}
