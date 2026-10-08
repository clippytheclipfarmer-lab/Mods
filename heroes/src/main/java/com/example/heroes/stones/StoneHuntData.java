package com.example.heroes.stones;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;

/** Where each stone was put when the world's hunt was set up, and whether it has actually been placed yet. */
public final class StoneHuntData extends SavedData {
    public static final class Placement {
        /** "hidden" (a secret spot in the map), "shrine" (on a planet), or "unplaced". */
        public String type = "unplaced";
        /** Hidden location id, or the planet id for a shrine. */
        public String where = "";
        public BlockPos pos = BlockPos.ZERO;
        public boolean placed;
    }

    public boolean setupDone;
    public final Map<InfinityStone, Placement> placements = new EnumMap<>(InfinityStone.class);

    public static StoneHuntData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StoneHuntData::load, StoneHuntData::new, "heroes_stone_hunt");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("SetupDone", setupDone);
        CompoundTag all = new CompoundTag();
        placements.forEach((stone, p) -> {
            CompoundTag t = new CompoundTag();
            t.putString("Type", p.type);
            t.putString("Where", p.where);
            t.putIntArray("Pos", new int[]{p.pos.getX(), p.pos.getY(), p.pos.getZ()});
            t.putBoolean("Placed", p.placed);
            all.put(stone.name(), t);
        });
        tag.put("Placements", all);
        return tag;
    }

    public static StoneHuntData load(CompoundTag tag) {
        StoneHuntData data = new StoneHuntData();
        data.setupDone = tag.getBoolean("SetupDone");
        CompoundTag all = tag.getCompound("Placements");
        for (String key : all.getAllKeys()) {
            InfinityStone stone = InfinityStone.byName(key);
            if (stone == null) {
                continue;
            }
            CompoundTag t = all.getCompound(key);
            Placement p = new Placement();
            p.type = t.getString("Type");
            p.where = t.getString("Where");
            int[] pos = t.getIntArray("Pos");
            p.pos = pos.length == 3 ? new BlockPos(pos[0], pos[1], pos[2]) : BlockPos.ZERO;
            p.placed = t.getBoolean("Placed");
            data.placements.put(stone, p);
        }
        return data;
    }
}
