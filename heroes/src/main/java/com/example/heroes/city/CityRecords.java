package com.example.heroes.city;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Original blocks of a region that have been destroyed and are waiting to be rebuilt. Keys sort by height first
 * (so a sorted key array is a bottom-up build order); values are indices into the shared block-state palette.
 */
public final class CityRecords {
    public static final int MAX_RECORDS = 6_000_000;
    private static final int Y_OFFSET = 512;

    public final Long2IntOpenHashMap states = new Long2IntOpenHashMap();
    public final Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();

    /** Returns Long.MIN_VALUE if the position is outside the supported range. */
    public static long key(BlockPos p) {
        int y = p.getY() + Y_OFFSET;
        if (y < 0 || y >= 2048) {
            return Long.MIN_VALUE;
        }
        return ((long) y << 52) | ((long) (p.getX() & 0x3FFFFFF) << 26) | (p.getZ() & 0x3FFFFFFL);
    }

    public static BlockPos pos(long key) {
        int z = (int) ((key & 0x3FFFFFFL) << 38 >> 38);
        int x = (int) (((key >> 26) & 0x3FFFFFFL) << 38 >> 38);
        int y = (int) (key >>> 52) - Y_OFFSET;
        return new BlockPos(x, y, z);
    }

    public int size() {
        return states.size();
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }
}
