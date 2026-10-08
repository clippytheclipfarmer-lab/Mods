package com.example.heroes.hulk;

import com.example.heroes.common.HeroEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The gamma detonation. Entities are hit instantly; terrain is erased as an expanding wave (nearest columns first)
 * spread over many ticks so a 100-block sphere does not freeze the server.
 */
public final class NukeSystem {
    /** Columns cleared per tick. 31k columns for a 100-block radius -> about 10 seconds. */
    private static final int COLUMNS_PER_TICK = 150;

    private static final class Nuke {
        final ServerLevel level;
        final BlockPos center;
        final int radius;
        final long[] columns; // packed (dx, dz) sorted by distance from the center
        int next;

        Nuke(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.columns = buildColumns(radius);
        }
    }

    private static final List<Nuke> ACTIVE = new ArrayList<>();

    private NukeSystem() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(NukeSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    public static void detonate(ServerLevel level, LivingEntity source, int radius, float damage, boolean blockDamage) {
        Vec3 origin = source.position();

        // Instant shockwave on everything alive in range (Hulk himself is spared).
        HeroEffects.blast(level, source, origin, radius, damage, 6.0, 2.5);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y + 1, origin.z, 20, 4, 2, 4, 0);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 20.0F, 0.3F);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 20.0F, 0.4F);

        if (blockDamage) {
            ACTIVE.add(new Nuke(level, source.blockPosition(), radius));
        }
    }

    private static void tick(ServerLevel level) {
        Iterator<Nuke> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Nuke nuke = it.next();
            if (nuke.level != level) {
                continue;
            }
            int end = Math.min(nuke.columns.length, nuke.next + COLUMNS_PER_TICK);
            double wave = 0;
            for (; nuke.next < end; nuke.next++) {
                long packed = nuke.columns[nuke.next];
                int dx = (int) (packed >> 32);
                int dz = (int) packed;
                wave = Math.sqrt((double) dx * dx + (double) dz * dz);
                clearColumn(level, nuke, dx, dz);
            }
            // Visible wavefront ring + rumble.
            Vec3 c = Vec3.atCenterOf(nuke.center);
            HeroEffects.ring(level, new Vec3(c.x, c.y - 0.5, c.z), Math.max(1, wave));
            if (level.getGameTime() % 10 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0F, 0.5F);
            }
            if (nuke.next >= nuke.columns.length) {
                it.remove();
            }
        }
    }

    private static void clearColumn(ServerLevel level, Nuke nuke, int dx, int dz) {
        int x = nuke.center.getX() + dx;
        int z = nuke.center.getZ() + dz;
        if (!level.hasChunkAt(new BlockPos(x, nuke.center.getY(), z))) {
            return;
        }
        int rem = nuke.radius * nuke.radius - dx * dx - dz * dz;
        if (rem < 0) {
            return;
        }
        int dy = (int) Math.sqrt(rem);
        int minY = Math.max(level.getMinBuildHeight(), nuke.center.getY() - dy);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, nuke.center.getY() + dy);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = minY; y <= maxY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            // Skip air and unbreakable blocks (bedrock, barriers, ...). Flags: no neighbour updates, no drops.
            if (state.isAir() || state.getBlock().defaultDestroyTime() < 0) {
                continue;
            }
            level.setBlock(pos, air, 2 | 16);
        }
    }

    private static long[] buildColumns(int radius) {
        List<Long> list = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) {
                    list.add(((long) dx << 32) | (dz & 0xFFFFFFFFL));
                }
            }
        }
        long[] arr = list.stream().mapToLong(Long::longValue).toArray();
        // Sort nearest-first so the destruction expands outward from the center.
        Long[] boxed = Arrays.stream(arr).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (a, b) -> Long.compare(dist2(a), dist2(b)));
        return Arrays.stream(boxed).mapToLong(Long::longValue).toArray();
    }

    private static long dist2(long packed) {
        long dx = (int) (packed >> 32);
        long dz = (int) packed;
        return dx * dx + dz * dz;
    }
}
