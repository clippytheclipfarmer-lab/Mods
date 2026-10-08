package com.example.heroes.city;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records blocks destroyed inside marked regions and slowly rebuilds them bottom-up, with builder villagers
 * working on site. All state that must survive a restart lives in {@link CityData}.
 */
public final class CityManager {
    private static final class Session {
        long[] order = new long[0];
        int index;
        double budget;
        double rate;
        long lastDamage;
        long nextRetry;
        final List<Long> retry = new ArrayList<>();
        BlockPos front;
        int particlesThisTick;
    }

    private static MinecraftServer server;
    private static CityData data;
    private static final Map<String, Session> SESSIONS = new HashMap<>();
    /** True while we ourselves place blocks, so rebuilding is not recorded as damage. */
    private static boolean rebuilding;
    /** Per-dimension region cache so the hot setBlock hook stays cheap. */
    private static volatile Map<ResourceKey<Level>, List<CityRegion>> byDimension = new HashMap<>();
    /** API calls made before the world has loaded (for example from a startup script); applied when the server starts. */
    private static final List<Runnable> PENDING = Collections.synchronizedList(new ArrayList<>());
    /** True while a player is breaking a block by hand, so zones that only track powers can ignore it. */
    private static boolean playerBreaking;

    private CityManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            data = CityData.get(s);
            refreshCache();
            synchronized (PENDING) {
                PENDING.forEach(Runnable::run);
                PENDING.clear();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            data = null;
            SESSIONS.clear();
            byDimension = new HashMap<>();
            CityBuilders.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(CityManager::tick);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            playerBreaking = true;
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> playerBreaking = false);
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, be) -> playerBreaking = false);
        ServerEntityEvents.ENTITY_LOAD.register(CityBuilders::onEntityLoad);
        CityWand.init();
        CityCommands.init();
    }

    public static MinecraftServer server() {
        return server;
    }

    public static CityData data() {
        return data;
    }

    public static void refreshCache() {
        Map<ResourceKey<Level>, List<CityRegion>> map = new HashMap<>();
        if (data != null) {
            for (CityRegion r : data.regions.values()) {
                map.computeIfAbsent(r.dimension, k -> new ArrayList<>()).add(r);
            }
        }
        byDimension = map;
    }

    public static void removeRegion(String name) {
        SESSIONS.remove(name);
        CityBuilders.despawn(name);
    }

    public static int pending(String region) {
        return data == null ? 0 : data.records(region).size();
    }

    /** Called before every live block change; remembers the original block if it is inside a region. */
    public static void onSetBlock(ServerLevel level, BlockPos pos, BlockState newState) {
        if (rebuilding || data == null) {
            return;
        }
        List<CityRegion> regions = byDimension.get(level.dimension());
        if (regions == null) {
            return;
        }
        for (CityRegion region : regions) {
            if (!region.contains(pos)) {
                continue;
            }
            if (playerBreaking && !region.tracking.equals("all")) {
                return; // this zone only tracks explosions and powers
            }
            BlockState old = level.getBlockState(pos);
            // Only real destruction/replacement: not air, not state-only changes (doors, crops), not fluids/fire.
            if (old.isAir() || old.getBlock() == newState.getBlock() || !old.getFluidState().isEmpty()
                    || old.getBlock() instanceof BaseFireBlock) {
                return;
            }
            CityRecords records = data.records(region.name);
            long key = CityRecords.key(pos);
            if (key == Long.MIN_VALUE || records.states.containsKey(key) || records.size() >= CityRecords.MAX_RECORDS) {
                return;
            }
            records.states.put(key, data.paletteId(old));
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                records.blockEntities.put(key, be.saveWithFullMetadata());
            }
            session(region.name).lastDamage = level.getGameTime();
            data.setDirty();
            return;
        }
    }

    private static Session session(String name) {
        return SESSIONS.computeIfAbsent(name, k -> new Session());
    }

    /** Rebuild the given region quickly (about 5 seconds), ignoring its configured pace. */
    public static void finishFast(String name) {
        Session s = session(name);
        s.lastDamage = Long.MIN_VALUE / 2;
        s.budget = Math.max(s.budget, 0);
        FAST.put(name, true);
    }

    private static final Map<String, Boolean> FAST = new HashMap<>();

    private static void tick(MinecraftServer server) {
        if (data == null || data.regions.isEmpty()) {
            return;
        }
        for (CityRegion region : data.regions.values()) {
            ServerLevel level = server.getLevel(region.dimension);
            CityRecords records = data.records.get(region.name);
            if (level == null || records == null || records.isEmpty()) {
                if (CityBuilders.count(region.name) > 0) {
                    CityBuilders.despawn(region.name);
                }
                SESSIONS.remove(region.name);
                continue;
            }
            Session s = session(region.name);
            long now = level.getGameTime();
            if (s.lastDamage == 0) {
                s.lastDamage = now;
            }
            // Wait for the destruction to settle, and pause while a fight is going on in the region.
            if (now - s.lastDamage < region.idleSeconds * 20L || fightingIn(level, region, now)) {
                continue;
            }
            rebuildTick(level, region, records, s, now);
            CityBuilders.tick(level, region, s.front, records.size());
        }
    }

    private static boolean fightingIn(ServerLevel level, CityRegion region, long now) {
        for (ServerPlayer p : level.players()) {
            if (region.contains(p.blockPosition())
                    && (p.tickCount - p.getLastHurtByMobTimestamp() < 100 || p.tickCount - p.getLastHurtMobTimestamp() < 100)) {
                return true;
            }
        }
        return false;
    }

    private static void rebuildTick(ServerLevel level, CityRegion region, CityRecords records, Session s, long now) {
        if (s.index >= s.order.length) {
            if (!s.retry.isEmpty() && now >= s.nextRetry) {
                s.order = s.retry.stream().mapToLong(Long::longValue).toArray();
                Arrays.sort(s.order);
                s.retry.clear();
                s.index = 0;
            } else if (s.retry.isEmpty()) {
                s.order = records.states.keySet().toLongArray();
                if (region.order.equals("random")) {
                    shuffle(s.order, level.random);
                } else {
                    Arrays.sort(s.order); // height is the high bits -> bottom-up
                }
                s.index = 0;
                // Fixed pace for this batch so the whole job takes about rebuildSeconds (small jobs get a minimum pace).
                s.rate = region.blocksPerSecond > 0 ? region.blocksPerSecond / 20.0
                        : Math.max(s.order.length / (region.rebuildSeconds * 20.0), 0.25);
            } else {
                return;
            }
        }

        boolean fast = FAST.getOrDefault(region.name, false);
        double rate = fast ? Math.max(1, Math.max(records.size(), 1) / 100.0) : (region.blocksPerSecond > 0 ? s.rate : Math.max(s.rate, 0.25));
        s.budget += rate;
        int n = (int) s.budget;
        s.budget -= n;
        s.particlesThisTick = 0;
        int sounds = 0;

        rebuilding = true;
        try {
            for (int i = 0; i < n && s.index < s.order.length; i++) {
                long key = s.order[s.index++];
                if (!records.states.containsKey(key)) {
                    continue;
                }
                BlockPos pos = CityRecords.pos(key);
                if (!level.isLoaded(pos)) {
                    s.retry.add(key);
                    continue;
                }
                BlockState state = data.paletteState(records.states.get(key));
                if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
                    s.retry.add(key);
                    s.nextRetry = now + 40;
                    continue;
                }
                level.setBlock(pos, state, 2 | 16);
                var beTag = records.blockEntities.remove(key);
                if (beTag != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        be.load(beTag);
                        be.setChanged();
                    }
                }
                records.states.remove(key);
                s.front = pos;
                effects(level, region, pos, state, s, sounds);
                if (level.random.nextInt(8) == 0) {
                    sounds++;
                }
            }
        } finally {
            rebuilding = false;
        }
        data.setDirty();
        if (records.isEmpty()) {
            FAST.remove(region.name);
            if (region.sounds && s.front != null) {
                level.playSound(null, s.front, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
        }
    }

    private static void effects(ServerLevel level, CityRegion region, BlockPos pos, BlockState state, Session s, int sounds) {
        if (s.particlesThisTick < 6 && level.random.nextInt(4) == 0) {
            s.particlesThisTick++;
            switch (region.particles) {
                case "none" -> {
                }
                case "cloud" -> level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
                case "marker" -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.0, 0.0, 0.0, 0.0);
                default -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        6, 0.4, 0.4, 0.4, 0.05);
            }
        }
        if (region.sounds && sounds < 2 && level.random.nextInt(8) == 0) {
            level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.2F, 0.8F + level.random.nextFloat() * 0.4F);
        }
    }

    private static void shuffle(long[] keys, net.minecraft.util.RandomSource random) {
        for (int i = keys.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            long t = keys[i];
            keys[i] = keys[j];
            keys[j] = t;
        }
    }

    // ------------------------------------------------------------------ public API (scripts, other mods)

    /** Runs on the server thread: right away when we are on it, queued for the server start when the world is not loaded yet. */
    private static void onServerThread(Runnable action) {
        MinecraftServer s = server;
        if (s == null) {
            PENDING.add(action);
        } else if (s.isSameThread()) {
            action.run();
        } else {
            s.execute(action);
        }
    }

    /** Registers (or updates) a city zone in the overworld; its district is the zone's own id. */
    public static void registerCityZone(String id, BlockPos min, BlockPos max) {
        registerCityZone(id, id, Level.OVERWORLD, min, max);
    }

    /** Registers (or updates) a city zone in the overworld that belongs to a district such as "downtown" or "piers". */
    public static void registerCityZone(String id, String districtId, BlockPos min, BlockPos max) {
        registerCityZone(id, districtId, Level.OVERWORLD, min, max);
    }

    public static void registerCityZone(String id, String districtId, ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        BlockPos lo = min.immutable(), hi = max.immutable();
        onServerThread(() -> {
            CityRegion old = data.regions.get(id);
            CityRegion region = new CityRegion(id, dimension, lo, hi);
            region.district = districtId == null || districtId.isEmpty() ? id : districtId;
            if (old != null) { // keep the zone's settings when it is registered again (for example on every start)
                region.rebuildSeconds = old.rebuildSeconds;
                region.idleSeconds = old.idleSeconds;
                region.maxBuilders = old.maxBuilders;
                region.sounds = old.sounds;
                region.blocksPerSecond = old.blocksPerSecond;
                region.order = old.order;
                region.tracking = old.tracking;
                region.particles = old.particles;
            }
            data.regions.put(id, region);
            data.setDirty();
            refreshCache();
        });
    }

    /** The district of the zone the player is standing in, or null when they are not in a city zone. */
    public static String getDistrict(Player player) {
        CityRegion region = zoneAt(player.level().dimension(), player.blockPosition());
        return region == null ? null : region.district;
    }

    /** The id of the zone the player is standing in, or null. */
    public static String getZone(Player player) {
        CityRegion region = zoneAt(player.level().dimension(), player.blockPosition());
        return region == null ? null : region.name;
    }

    public static CityRegion zoneAt(ResourceKey<Level> dimension, BlockPos pos) {
        List<CityRegion> regions = byDimension.get(dimension);
        if (regions != null) {
            for (CityRegion region : regions) {
                if (region.contains(pos)) {
                    return region;
                }
            }
        }
        return null;
    }

    /** Stores a point of interest of the city (overworld). Registering the same id again replaces it. */
    public static void registerPOI(String id, int x, int y, int z, String name) {
        onServerThread(() -> {
            data.pois.put(id, new CityPoint(id, x, y, z, name, "minecraft:overworld"));
            data.setDirty();
        });
    }

    /** Stores a residential zone (overworld) with a tier such as "low", "middle" or "high". */
    public static void registerResidential(String id, int x, int y, int z, String tier) {
        onServerThread(() -> {
            data.residentials.put(id, new CityPoint(id, x, y, z, tier, "minecraft:overworld"));
            data.setDirty();
        });
    }

    public static List<CityPoint> getPOIs() {
        return data == null ? List.of() : new ArrayList<>(data.pois.values());
    }

    public static List<CityPoint> getResidentials() {
        return data == null ? List.of() : new ArrayList<>(data.residentials.values());
    }
}
