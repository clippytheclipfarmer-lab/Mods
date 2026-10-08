package com.example.heroes.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Empty space with the planets (block spheres) and the station from {@link Planets} placed in it. */
public class SpaceChunkGenerator extends ChunkGenerator {
    public static final Codec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(SpaceChunkGenerator::new)));

    private final Map<String, SimplexNoise> noises = new HashMap<>();

    public SpaceChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int minX = cp.getMinBlockX();
        int minZ = cp.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (PlanetDef p : Planets.inSpace()) {
            int r = p.extent();
            double cx = p.position.x, cy = p.position.y, cz = p.position.z;
            if (cx + r < minX || cx - r > minX + 15 || cz + r < minZ || cz - r > minZ + 15) {
                continue;
            }
            int y0 = (int) Math.max(minY, Math.floor(cy - r));
            int y1 = (int) Math.min(maxY, Math.ceil(cy + r));
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int y = y0; y <= y1; y++) {
                        BlockState state = p.station ? stationBlock(p, x - (int) cx, y - (int) cy, z - (int) cz) : sphereBlock(p, x, y, z);
                        if (state != null) {
                            chunk.setBlockState(pos.set(x, y, z), state, false);
                        }
                    }
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private BlockState sphereBlock(PlanetDef p, int x, int y, int z) {
        double dx = x + 0.5 - p.position.x, dy = y + 0.5 - p.position.y, dz = z + 0.5 - p.position.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > p.radius) {
            return null;
        }
        double depth = p.radius - d;
        if (depth < 1.5) {
            if (p.accent != null && noise(p).getValue(x / p.accentScale, y / p.accentScale, z / p.accentScale) > 0.2) {
                return p.accent;
            }
            return p.surface;
        }
        return depth < 5 ? p.subsurface : p.core;
    }

    /** A hollow walled box: iron frame, glass walls, stone floor, lamps in the ceiling. Coordinates are relative to the center. */
    private static BlockState stationBlock(PlanetDef p, int dx, int dy, int dz) {
        int h = p.radius;
        if (Math.abs(dx) > h || Math.abs(dy) > h / 2 || Math.abs(dz) > h) {
            return null;
        }
        int top = h / 2;
        boolean wallX = Math.abs(dx) == h, wallZ = Math.abs(dz) == h, floor = dy == -top, ceiling = dy == top;
        int edges = (wallX ? 1 : 0) + (wallZ ? 1 : 0) + (floor || ceiling ? 1 : 0);
        if (edges >= 2) {
            return p.surface; // frame
        }
        if (floor) {
            return p.core;
        }
        if (ceiling) {
            return (dx % 4 == 0 && dz % 4 == 0) ? Blocks.SEA_LANTERN.defaultBlockState() : p.surface;
        }
        if (wallX || wallZ) {
            return p.subsurface; // windows
        }
        return null;
    }

    private SimplexNoise noise(PlanetDef p) {
        return noises.computeIfAbsent(p.id.toString(), k -> new SimplexNoise(new WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(k.hashCode()))));
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 768;
    }

    @Override
    public int getSeaLevel() {
        return -128;
    }

    @Override
    public int getMinY() {
        return -128;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}
