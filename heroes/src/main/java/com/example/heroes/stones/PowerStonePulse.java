package com.example.heroes.stones;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Power Stone is too much to carry loose: while it sits in your inventory it hurts you and sends out pulsing purple
 * shockwaves that hurt everything near you. Wear it (ring slot), socket it in a holder, or keep it in the Orb to tame it.
 */
public final class PowerStonePulse {
    /** Ticks between pulses. */
    private static final int INTERVAL = 40;
    /** Damage to the carrier per pulse (it never kills: the carrier is left on at least half a heart). */
    private static final float SELF_DAMAGE = 2.0F;
    /** Damage to each other creature the shockwave passes through. */
    private static final float WAVE_DAMAGE = 5.0F;
    private static final double MAX_RADIUS = 9.0;
    private static final double SPEED = 0.55;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62F, 0.2F, 1.0F), 1.7F);

    private static final class Wave {
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final UUID owner;
        final Set<UUID> hit = new HashSet<>();
        double radius;

        Wave(ResourceKey<Level> dimension, Vec3 center, UUID owner) {
            this.dimension = dimension;
            this.center = center;
            this.owner = owner;
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<UUID, Integer> TIMERS = new HashMap<>();

    private PowerStonePulse() {
    }

    public static boolean carriesLoosePowerStone(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (var list : List.of(inv.items, inv.offhand, inv.armor)) {
            for (ItemStack stack : list) {
                if (stack.getItem() == StoneItems.get(InfinityStone.POWER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Called every server tick for every player. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) {
            return; // the inventory is only checked every five ticks
        }
        UUID id = player.getUUID();
        if (player.isCreative() || player.isSpectator() || !carriesLoosePowerStone(player)) {
            TIMERS.remove(id);
            return;
        }
        int timer = TIMERS.merge(id, 1, Integer::sum);
        // The stone hums before it pulses.
        ServerLevel level = player.serverLevel();
        level.sendParticles(PURPLE, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.6, 0.4, 0.0);
        if (timer * 5 >= INTERVAL) {
            TIMERS.put(id, 0);
            pulse(player, level);
        }
    }

    private static void pulse(ServerPlayer player, ServerLevel level) {
        WAVES.add(new Wave(level.dimension(), player.position().add(0, 0.2, 0), player.getUUID()));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 1.6F);
        // It burns the one carrying it, but never to death.
        if (player.getHealth() > SELF_DAMAGE + 1.0F) {
            player.hurt(level.damageSources().magic(), SELF_DAMAGE);
        }
        player.displayClientMessage(Component.literal("The Power Stone pulses..."), true);
    }

    /** Advances all shockwaves; call once per server tick. */
    public static void tickWaves(net.minecraft.server.MinecraftServer server) {
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().equals(wave.dimension)) {
                    level = l;
                    break;
                }
            }
            if (level == null || wave.radius > MAX_RADIUS) {
                it.remove();
                continue;
            }
            double previous = wave.radius;
            wave.radius += SPEED;
            ring(level, wave.center, wave.radius);

            // Everything the expanding ring has just passed through is hit once.
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(wave.center, wave.center).inflate(wave.radius + 1.5),
                    e -> e.isAlive() && !e.getUUID().equals(wave.owner) && !wave.hit.contains(e.getUUID()))) {
                double d = target.position().add(0, target.getBbHeight() / 2, 0).distanceTo(wave.center);
                if (d >= previous - 0.5 && d <= wave.radius + 0.5) {
                    wave.hit.add(target.getUUID());
                    target.hurt(level.damageSources().magic(), WAVE_DAMAGE);
                    Vec3 away = target.position().subtract(wave.center);
                    away = new Vec3(away.x, 0, away.z);
                    away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.9);
                    target.setDeltaMovement(away.x, 0.35, away.z);
                    target.hurtMarked = true;
                }
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 center, double radius) {
        int points = (int) Math.max(12, radius * 10);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            level.sendParticles(PURPLE, x, center.y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, center.y + 0.5, z, 1, 0.05, 0.25, 0.05, 0.0);
            }
        }
    }

    public static void forget(UUID player) {
        TIMERS.remove(player);
    }

    public static void clear() {
        WAVES.clear();
        TIMERS.clear();
    }
}
