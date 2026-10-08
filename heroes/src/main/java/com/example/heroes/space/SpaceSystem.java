package com.example.heroes.space;

import com.example.heroes.HeroesMod;
import com.example.heroes.viltrumite.ViltrumiteHero;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Space travel: fly up past the build limit to enter the space dimension, fly into a planet to land on it.
 * Also per-planet hazards (oxygen, heat, cold) and gravity.
 */
public final class SpaceSystem {
    public static final ResourceLocation GRAVITY_PACKET = new ResourceLocation("heroes", "gravity");
    private static final TagKey<Item> OXYGEN_HELMETS = TagKey.create(Registries.ITEM, new ResourceLocation("heroes", "oxygen_helmets"));
    private static final String TAG_ZERO_G = "heroes_zero_g";
    private static final String TAG_FLIGHT = "heroes_space_flight";

    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Double> LAST_Y = new HashMap<>();
    private static final Map<UUID, Float> LAST_FALL = new HashMap<>();
    private static final Set<UUID> ANNOUNCED_O2 = new HashSet<>();

    private SpaceSystem() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, new ResourceLocation("heroes", "space"), SpaceChunkGenerator.CODEC);
        Planets.init();
        SpaceCommands.init();

        ServerTickEvents.END_SERVER_TICK.register(SpaceSystem::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncGravity(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> syncGravity(player));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> syncGravity(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            COOLDOWN.remove(id);
            LAST_Y.remove(id);
            LAST_FALL.remove(id);
        });
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            boolean inSpace = level.dimension().equals(Planets.SPACE);
            COOLDOWN.computeIfPresent(player.getUUID(), (id, c) -> c > 0 ? c - 1 : null);
            boolean cooling = COOLDOWN.containsKey(player.getUUID());

            zeroGravity(player, inSpace);
            if (inSpace) {
                if (!cooling) {
                    checkLanding(server, player, level);
                }
                spaceEnvironment(player);
            } else {
                if (!cooling) {
                    checkLaunch(server, player, level);
                }
                planetEnvironment(player, level);
            }
            LAST_Y.put(player.getUUID(), player.getY());
        }
    }

    // ------------------------------------------------------------------ travel

    private static void checkLaunch(MinecraftServer server, ServerPlayer player, ServerLevel level) {
        PlanetDef here = Planets.forDimension(level.dimension());
        if (here == null || !here.canLaunch) {
            return;
        }
        int altitude = here.launchAltitude >= 0 ? here.launchAltitude : level.getMaxBuildHeight() - 8;
        Double last = LAST_Y.get(player.getUUID());
        boolean rising = last != null && player.getY() > last + 0.05;
        if (player.getY() < altitude || !rising || player.isSpectator()) {
            return;
        }
        ServerLevel space = server.getLevel(Planets.SPACE);
        if (space == null) {
            return;
        }
        SpaceData.get(server).setOrigin(player.getUUID(), level.dimension().location(), player.blockPosition());
        Vec3 arrive = here.position.add(0, here.radius + 22, 0);
        player.displayClientMessage(Component.literal("Leaving " + here.name + "..."), true);
        player.teleportTo(space, arrive.x, arrive.y, arrive.z, player.getYRot(), -10F);
        COOLDOWN.put(player.getUUID(), 100);
    }

    private static void checkLanding(MinecraftServer server, ServerPlayer player, ServerLevel space) {
        Vec3 p = player.position();
        for (PlanetDef def : Planets.inSpace()) {
            if (def.station || def.dimension.equals(Planets.SPACE)) {
                continue;
            }
            if (p.distanceTo(def.position) <= def.radius + 3.5) {
                land(server, player, def);
                return;
            }
        }
    }

    /** Teleports the player to a planet's surface: back to where they launched from if it is the same world. */
    public static boolean land(MinecraftServer server, ServerPlayer player, PlanetDef def) {
        ServerLevel dest = server.getLevel(def.dimension);
        if (dest == null) {
            player.displayClientMessage(Component.literal("The dimension of " + def.name + " is not loaded."), true);
            return false;
        }
        int x = def.landingX;
        int z = def.landingZ;
        SpaceData.Origin origin = SpaceData.get(server).origin(player.getUUID());
        if (origin != null && origin.dimension().equals(def.dimension.location())) {
            x = origin.x();
            z = origin.z();
        }
        dest.getChunk(x >> 4, z >> 4); // generates the chunk
        int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= dest.getMinBuildHeight() + 1) {
            y = dest.getSeaLevel() + 40;
        }
        player.displayClientMessage(Component.literal("Entering the atmosphere of " + def.name + "..."), true);
        player.teleportTo(dest, x + 0.5, y + 0.2, z + 0.5, player.getYRot(), 0F);
        player.fallDistance = 0;
        COOLDOWN.put(player.getUUID(), 100);
        return true;
    }

    /** Sends the player to a spot in space just above the planet (or to the station). */
    public static boolean orbit(MinecraftServer server, ServerPlayer player, PlanetDef def) {
        ServerLevel space = server.getLevel(Planets.SPACE);
        if (space == null) {
            return false;
        }
        Vec3 at = def.station ? def.position.add(0, -def.radius / 2 + 2, 0) : def.position.add(0, def.radius + 22, 0);
        player.teleportTo(space, at.x, at.y, at.z, player.getYRot(), 0F);
        COOLDOWN.put(player.getUUID(), 100);
        return true;
    }

    // ------------------------------------------------------------------ environment

    /** In space players float freely: no gravity, and survival players may fly. Reverted on leaving. */
    private static void zeroGravity(ServerPlayer player, boolean inSpace) {
        boolean tagged = player.getTags().contains(TAG_ZERO_G);
        if (inSpace && !player.isCreative() && !player.isSpectator()) {
            if (!tagged) {
                player.addTag(TAG_ZERO_G);
                player.setNoGravity(true);
            }
            if (!player.getTags().contains(TAG_FLIGHT) && !player.getAbilities().mayfly) {
                player.addTag(TAG_FLIGHT);
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        } else if (tagged || player.getTags().contains(TAG_FLIGHT)) {
            if (tagged) {
                player.removeTag(TAG_ZERO_G);
                player.setNoGravity(false);
            }
            if (player.getTags().contains(TAG_FLIGHT)) {
                player.removeTag(TAG_FLIGHT);
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
        }
    }

    private static void spaceEnvironment(ServerPlayer player) {
        drainOxygen(player);
    }

    private static void planetEnvironment(ServerPlayer player, ServerLevel level) {
        PlanetDef def = Planets.forDimension(level.dimension());
        if (def == null) {
            return;
        }
        if (!def.oxygen) {
            drainOxygen(player);
        }
        if (!isProtected(player)) {
            if (def.hazard == PlanetDef.Hazard.HEAT && player.tickCount % 40 == 0 && !player.isInWaterRainOrBubble()) {
                player.hurt(player.damageSources().onFire(), 1.0F);
            } else if (def.hazard == PlanetDef.Hazard.COLD && player.canFreeze()) {
                player.setTicksFrozen(Math.min(player.getTicksFrozen() + 2, player.getTicksRequiredToFreeze() + 20));
            }
        }
        // Low gravity also softens falls: only a share of the new fall distance counts.
        float g = def.gravity;
        if (g < 1F) {
            float last = LAST_FALL.getOrDefault(player.getUUID(), 0F);
            float now = player.fallDistance;
            if (now > last) {
                player.fallDistance = last + (now - last) * g;
            }
            LAST_FALL.put(player.getUUID(), player.fallDistance);
        }
    }

    /** Called after vanilla's own tick has refilled air, so a net loss of 1 per tick remains. */
    private static void drainOxygen(ServerPlayer player) {
        if (isProtected(player) || hasOxygenHelmet(player)) {
            ANNOUNCED_O2.remove(player.getUUID());
            return;
        }
        if (ANNOUNCED_O2.add(player.getUUID())) {
            player.displayClientMessage(Component.literal("There is no air here!"), true);
        }
        int air = player.getAirSupply() - 5;
        if (air <= -20) {
            air = 0;
            player.hurt(player.damageSources().drown(), 2.0F);
        }
        player.setAirSupply(air);
    }

    private static boolean hasOxygenHelmet(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(OXYGEN_HELMETS);
    }

    /** Creative/spectator, and heroes whose powers cover space survival. */
    public static boolean isProtected(ServerPlayer player) {
        return player.isCreative() || player.isSpectator()
                || (ViltrumiteHero.isViltrumite(player) && !AbilityUtil.getEnabledEntries(player, ViltrumiteHero.SPACE_SURVIVAL.get()).isEmpty());
    }

    // ------------------------------------------------------------------ gravity sync

    public static void syncGravity(ServerPlayer player) {
        float g = 1F;
        PlanetDef def = Planets.forDimension(player.serverLevel().dimension());
        if (def != null) {
            g = def.gravity;
        }
        var buf = PacketByteBufs.create();
        buf.writeFloat(g);
        ServerPlayNetworking.send(player, GRAVITY_PACKET, buf);
    }
}
