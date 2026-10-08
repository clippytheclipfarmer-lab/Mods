package com.example.heroes.symbiote;

import com.example.heroes.HeroesMod;
import com.example.heroes.space.PlanetDef;
import com.example.heroes.space.Planets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Random symbiote meteors. Every ten seconds each planet with players has a small chance (about one in 90, so one
 * every ~15 minutes) of a meteor falling near a player. It never happens on Earth or on planets with
 * {@code "symbiote_meteors": false} (such as Klyntar). The meteor carries a blob.
 */
public final class MeteorEvents {
    private static final int CHECK_INTERVAL = 200;
    private static final int ONE_IN = 90;
    private static final int MAX_BLOBS_PER_WORLD = 3;
    private static final double FALL_SPEED = 1.6;
    private static final DustParticleOptions FIRE_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.1F), 4.0F);

    private static final class Meteor {
        final ServerLevel level;
        final Vec3 target;
        Vec3 pos;
        final Vec3 velocity;
        int age;

        Meteor(ServerLevel level, Vec3 target, Vec3 start) {
            this.level = level;
            this.target = target;
            this.pos = start;
            this.velocity = target.subtract(start).normalize().scale(FALL_SPEED);
        }
    }

    private static final List<Meteor> FALLING = new ArrayList<>();

    private MeteorEvents() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MeteorEvents::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> FALLING.clear());
    }

    private static void tick(MinecraftServer server) {
        Iterator<Meteor> it = FALLING.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            if (fall(m)) {
                it.remove();
                impact(m.level, m.target);
            }
        }
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            PlanetDef planet = Planets.forDimension(level.dimension());
            if (planet == null || !planet.symbioteMeteors || level.players().isEmpty()) {
                continue;
            }
            if (level.random.nextInt(ONE_IN) != 0 || countBlobs(level) >= MAX_BLOBS_PER_WORLD) {
                continue;
            }
            ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));
            if (!player.isSpectator()) {
                start(level, player.position());
            }
        }
    }

    private static int countBlobs(ServerLevel level) {
        int n = 0;
        for (var e : level.getAllEntities()) {
            if (e instanceof SymbioteBlobEntity) {
                n++;
            }
        }
        return n;
    }

    /** Starts a meteor aimed 45-90 blocks from the given position. Returns false if no landing spot was found. */
    public static boolean start(ServerLevel level, Vec3 near) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 45 + level.random.nextDouble() * 45;
            int x = (int) (near.x + Math.cos(angle) * dist);
            int z = (int) (near.z + Math.sin(angle) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 target = new Vec3(x + 0.5, y, z + 0.5);
            // It comes in at a slant from a random side, high above.
            double side = level.random.nextDouble() * Math.PI * 2;
            Vec3 start = target.add(Math.cos(side) * 70, 120, Math.sin(side) * 70);
            FALLING.add(new Meteor(level, target, start));
            for (Player p : level.players()) {
                Vec3 to = target.subtract(p.position());
                String dir = Math.abs(to.x) > Math.abs(to.z) ? (to.x > 0 ? "east" : "west") : (to.z > 0 ? "south" : "north");
                p.displayClientMessage(Component.literal("A burning meteor streaks across the sky to the " + dir + "!"), false);
            }
            HeroesMod.LOGGER.info("Symbiote meteor falling at {} {} {} in {}", x, y, z, level.dimension().location());
            return true;
        }
        return false;
    }

    /** Moves the meteor one step; returns true when it has reached the ground. */
    private static boolean fall(Meteor m) {
        m.age++;
        m.pos = m.pos.add(m.velocity);
        ServerLevel level = m.level;
        level.sendParticles(FIRE_DUST, m.pos.x, m.pos.y, m.pos.z, 6, 0.8, 0.8, 0.8, 0.0);
        level.sendParticles(ParticleTypes.FLAME, m.pos.x, m.pos.y, m.pos.z, 8, 1.0, 1.0, 1.0, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, m.pos.x - m.velocity.x * 2, m.pos.y - m.velocity.y * 2, m.pos.z - m.velocity.z * 2, 6, 1.2, 1.2, 1.2, 0.02);
        if (m.age % 8 == 0) {
            level.playSound(null, m.pos.x, m.pos.y, m.pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 6.0F, 0.5F);
        }
        return m.pos.y <= m.target.y + 1 || m.age > 400;
    }

    /** Crater, a hollow shell of blackened rock, and the blob inside. */
    public static void impact(ServerLevel level, Vec3 target) {
        BlockPos center = BlockPos.containing(target).below(2);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 8.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1, target.z, 3, 1.5, 1, 1.5, 0);
        level.sendParticles(ParticleTypes.LAVA, target.x, target.y + 1, target.z, 40, 2, 1, 2, 0);

        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 6, 6))) {
            double d = Math.sqrt(p.distSqr(center));
            double lumpy = d + (level.random.nextDouble() - 0.5) * 1.2;
            if (lumpy <= 2.1) {
                level.setBlock(p, air, 2);
            } else if (lumpy <= 3.4) {
                int r = level.random.nextInt(10);
                level.setBlock(p, r < 6 ? Blocks.BLACKSTONE.defaultBlockState() : r < 9 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
            } else if (lumpy <= 6.0 && p.getY() > center.getY() + 1) {
                level.setBlock(p, air, 2); // crater walls
            }
        }
        SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(level);
        if (blob != null) {
            blob.moveTo(center.getX() + 0.5, center.getY() - 1, center.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            blob.setPersistenceRequired();
            level.addFreshEntity(blob);
        }
    }
}
