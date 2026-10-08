package com.example.heroes.symbiote.klyntar;

import com.example.heroes.HeroesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** The hive (a tar-floored dome with the core at its centre) and the bonding altar, built once when someone first lands. */
final class KlyntarStructures {
    private static final int HIVE_X = 120;
    private static final int HIVE_Z = 70;
    private static final int ALTAR_X = -70;
    private static final int ALTAR_Z = -50;

    private KlyntarStructures() {
    }

    static void build(ServerLevel level, KlyntarData data) {
        buildHive(level, data);
        buildAltar(level, data);
        data.built = true;
        data.setDirty();
        HeroesMod.LOGGER.info("Klyntar: hive core at {} and altar at {} built", data.hiveCore, data.altar);
    }

    private static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private static void buildHive(ServerLevel level, KlyntarData data) {
        RandomSource random = level.random;
        int groundY = ground(level, HIVE_X, HIVE_Z);
        BlockPos center = new BlockPos(HIVE_X, groundY + 8, HIVE_Z);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState tar = KlyntarEntities.TAR.defaultBlockState();

        for (BlockPos p : BlockPos.betweenClosed(center.offset(-16, -13, -16), center.offset(16, 13, 16))) {
            double d = Math.sqrt(p.distSqr(center));
            if (d <= 12) {
                level.setBlock(p, air, 2);
            } else if (d <= 15) {
                int r = random.nextInt(10);
                level.setBlock(p, r < 8 ? Blocks.BLACKSTONE.defaultBlockState() : r < 9 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
            }
        }
        // Tar pool filling the bowl below ground level.
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-9, -13, -9), center.offset(9, 0, 9))) {
            if (p.getY() <= groundY - 1 && Math.sqrt(p.distSqr(center)) <= 12 && Math.hypot(p.getX() - center.getX(), p.getZ() - center.getZ()) <= 9) {
                level.setBlock(p, tar, 2);
            }
        }
        // Doorway on the side facing the landing site.
        for (int x = HIVE_X - 16; x <= HIVE_X - 9; x++) {
            for (int z = HIVE_Z - 2; z <= HIVE_Z + 2; z++) {
                for (int y = groundY; y <= groundY + 4; y++) {
                    level.setBlock(new BlockPos(x, y, z), air, 2);
                }
            }
        }
        // The core: an island of obsidian with a crown of crying obsidian.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = groundY - 4; y <= groundY; y++) {
                    level.setBlock(new BlockPos(HIVE_X + dx, y, HIVE_Z + dz), Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
        BlockPos core = new BlockPos(HIVE_X, groundY + 1, HIVE_Z);
        for (int y = 0; y < 3; y++) {
            level.setBlock(core.above(y), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        }
        for (BlockPos rod : new BlockPos[]{core.offset(1, 0, 0), core.offset(-1, 0, 0), core.offset(0, 0, 1), core.offset(0, 0, -1)}) {
            level.setBlock(rod, Blocks.END_ROD.defaultBlockState(), 2);
        }
        // Spines rising around the outside.
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI * 2 / 10 + random.nextDouble() * 0.3;
            int sx = HIVE_X + (int) (Math.cos(angle) * (19 + random.nextInt(8)));
            int sz = HIVE_Z + (int) (Math.sin(angle) * (19 + random.nextInt(8)));
            int sy = ground(level, sx, sz);
            int height = 9 + random.nextInt(8);
            for (int y = 0; y < height; y++) {
                int w = Math.max(0, 2 - y / 4);
                for (int dx = -w; dx <= w; dx++) {
                    for (int dz = -w; dz <= w; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= w) {
                            level.setBlock(new BlockPos(sx + dx, sy + y, sz + dz),
                                    y > height - 3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
        data.hiveCore = core.above(1);
    }

    private static void buildAltar(ServerLevel level, KlyntarData data) {
        int y = ground(level, ALTAR_X, ALTAR_Z);
        BlockPos base = new BlockPos(ALTAR_X, y, ALTAR_Z);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 6; dy++) {
                    level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
                boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == 4;
                level.setBlock(base.offset(dx, 0, dz), edge ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int cx : new int[]{-3, 3}) {
            for (int cz : new int[]{-3, 3}) {
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(base.offset(cx, dy, cz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                }
                level.setBlock(base.offset(cx, 5, cz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            }
        }
        BlockPos altar = base.above();
        level.setBlock(altar, KlyntarEntities.ALTAR.defaultBlockState(), 3);
        data.altar = altar;
    }
}
