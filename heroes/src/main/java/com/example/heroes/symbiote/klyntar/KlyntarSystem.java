package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteBlobEntity;
import com.example.heroes.symbiote.SymbioteCreature;
import com.example.heroes.symbiote.SymbioteEntities;
import com.example.heroes.symbiote.SymbioteHost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Klyntar: a hostile hive world. Players there are worn down by spores unless bonded; blobs, crawlers and brutes keep
 * spawning around them; the structures are built on first arrival; and Knull wakes when someone nears the hive core.
 */
public final class KlyntarSystem {
    public static final ResourceLocation DIMENSION = new ResourceLocation("heroes", "klyntar");
    private static final int MAX_BLOBS = 6;
    private static final int MAX_CRAWLERS = 8;
    private static final int MAX_BRUTES = 2;

    private KlyntarSystem() {
    }

    public static void init() {
        KlyntarEntities.init();
        ServerTickEvents.END_SERVER_TICK.register(KlyntarSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> KlyntarEffects.clear());
    }

    public static boolean onKlyntar(ServerLevel level) {
        return level.dimension().location().equals(DIMENSION);
    }

    private static void tick(MinecraftServer server) {
        KlyntarEffects.tick(server);
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (onKlyntar(l)) {
                level = l;
                break;
            }
        }
        if (level == null || level.players().isEmpty()) {
            return;
        }
        KlyntarData data = KlyntarData.get(server);
        long time = server.getTickCount();

        if (!data.built && time % 40 == 0) {
            KlyntarStructures.build(level, data);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            spores(player, level);
            if (time % 100 == 0) {
                spawnWave(level, player);
            }
            if (!data.knullSpawned && data.hiveCore != null && time % 20 == 0
                    && player.position().distanceToSqr(Vec3.atCenterOf(data.hiveCore)) < 20 * 20) {
                awakenKnull(level, data);
            }
        }
    }

    /** Spores in the air: weakness all the time and bouts of nausea. Bonded hosts and creative players are immune. */
    private static void spores(ServerPlayer player, ServerLevel level) {
        if (SymbioteHostOrImmune(player)) {
            return;
        }
        if (player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.2, player.getZ(), 4, 3.0, 1.5, 3.0, 0.0);
        }
        if (player.tickCount % 80 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0, true, false));
        }
        if (player.tickCount % 700 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false));
            player.displayClientMessage(Component.literal("The spores cloud your head..."), true);
        }
    }

    private static boolean SymbioteHostOrImmune(ServerPlayer player) {
        return player.isCreative() || SymbioteHost.isHost(player);
    }

    private static void spawnWave(ServerLevel level, ServerPlayer player) {
        Vec3 at = player.position();
        if (count(level, at, SymbioteBlobEntity.class) < MAX_BLOBS) {
            spawnAround(level, SymbioteEntities.SYMBIOTE_BLOB, at, 1, 30);
        }
        if (count(level, at, SymbioteCrawlerEntity.class) < MAX_CRAWLERS) {
            spawnAround(level, KlyntarEntities.SYMBIOTE_CRAWLER, at, 2, 28);
        }
        if (level.random.nextInt(3) == 0 && count(level, at, SymbioteBruteEntity.class) < MAX_BRUTES) {
            spawnAround(level, KlyntarEntities.SYMBIOTE_BRUTE, at, 1, 34);
        }
    }

    private static <T extends Mob> int count(ServerLevel level, Vec3 at, Class<T> type) {
        return level.getEntitiesOfClass(type, new AABB(BlockPos.containing(at)).inflate(64)).size();
    }

    /** Symbiote creatures within {@code range} blocks of a point. */
    public static int countNear(ServerLevel level, Vec3 at, double range) {
        return level.getEntitiesOfClass(Mob.class, new AABB(BlockPos.containing(at)).inflate(range), m -> m instanceof SymbioteCreature).size();
    }

    /** Spawns {@code n} of the given creature on the ground around a point, {@code distance} blocks away (or nearer). */
    public static <T extends Mob> void spawnAround(ServerLevel level, EntityType<T> type, Vec3 at, int n, double distance) {
        for (int i = 0; i < n; i++) {
            for (int attempt = 0; attempt < 6; attempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double dist = distance * (0.6 + level.random.nextDouble() * 0.4);
                int x = (int) (at.x + Math.cos(angle) * dist);
                int z = (int) (at.z + Math.sin(angle) * dist);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                T mob = type.create(level);
                if (mob == null) {
                    return;
                }
                mob.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360F, 0F);
                if (level.noCollision(mob)) {
                    mob.setPersistenceRequired();
                    level.addFreshEntity(mob);
                    break;
                }
            }
        }
    }

    /** Builds the hive and altar now (admin command). Returns false if they already exist. */
    public static boolean buildNow(ServerLevel level) {
        KlyntarData data = KlyntarData.get(level.getServer());
        if (data.built) {
            return false;
        }
        KlyntarStructures.build(level, data);
        return true;
    }

    /** Wakes Knull at the hive core now (admin command). */
    public static boolean awakenNow(ServerLevel level) {
        KlyntarData data = KlyntarData.get(level.getServer());
        if (data.hiveCore == null) {
            return false;
        }
        awakenKnull(level, data);
        return true;
    }

    private static void awakenKnull(ServerLevel level, KlyntarData data) {
        KnullEntity knull = KlyntarEntities.KNULL.create(level);
        if (knull == null) {
            return;
        }
        BlockPos core = data.hiveCore;
        knull.moveTo(core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, 0F, 0F);
        level.addFreshEntity(knull);
        data.knullSpawned = true;
        data.setDirty();
        level.playSound(null, core, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 5.0F, 0.5F);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.literal("Something ancient stirs in the hive..."));
        }
    }
}
