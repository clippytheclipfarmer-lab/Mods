package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tendril traps dragging players towards the hive, and the willing bond at the altar. */
public final class KlyntarEffects {
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.0F, 0.04F), 1.5F);

    private static final class Drag {
        final BlockPos from;
        final Vec3 target;
        int ticks = 120;

        Drag(BlockPos from, Vec3 target) {
            this.from = from;
            this.target = target;
        }
    }

    private static final class AltarBond {
        final BlockPos altar;
        int ticks;

        AltarBond(BlockPos altar) {
            this.altar = altar;
        }
    }

    private static final Map<UUID, Drag> DRAGS = new HashMap<>();
    private static final Map<UUID, AltarBond> BONDS = new HashMap<>();
    private static final Map<UUID, Long> LAST_BOND = new HashMap<>();
    private static final int BOND_COOLDOWN = 12000;

    private KlyntarEffects() {
    }

    public static void startDrag(ServerPlayer player, ServerLevel level, BlockPos trap) {
        KlyntarData data = KlyntarData.get(level.getServer());
        Vec3 target = data.hiveCore != null ? Vec3.atCenterOf(data.hiveCore) : Vec3.atCenterOf(trap);
        DRAGS.put(player.getUUID(), new Drag(trap, target));
        player.hurt(level.damageSources().magic(), 2.0F);
        level.playSound(null, trap, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.2F, 0.6F);
        player.displayClientMessage(Component.literal("A tendril lashes around your leg and drags you away!"), true);
    }

    public static void startAltarBond(ServerPlayer player, BlockPos altar) {
        long now = player.level().getGameTime();
        Long last = LAST_BOND.get(player.getUUID());
        if (last != null && now - last < BOND_COOLDOWN) {
            player.displayClientMessage(Component.literal("The altar is spent. Wait a while."), true);
            return;
        }
        if (BONDS.containsKey(player.getUUID())) {
            return;
        }
        BONDS.put(player.getUUID(), new AltarBond(altar));
        player.displayClientMessage(Component.literal("The altar reaches for you..."), true);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Drag>> dit = DRAGS.entrySet().iterator();
        while (dit.hasNext()) {
            var e = dit.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Drag drag = e.getValue();
            if (player == null || !player.isAlive() || --drag.ticks <= 0) {
                dit.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            Vec3 to = drag.target.subtract(player.position());
            if (to.length() > 3) {
                Vec3 pull = to.normalize().scale(0.3);
                player.setDeltaMovement(pull.x, Math.max(0.08, pull.y), pull.z);
                player.hurtMarked = true;
            }
            Vec3 from = Vec3.atCenterOf(drag.from);
            int steps = (int) Math.max(4, from.distanceTo(player.position()) * 2);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.lerp(player.position().add(0, 0.5, 0), i / (double) steps);
                level.sendParticles(BLACK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }

        Iterator<Map.Entry<UUID, AltarBond>> bit = BONDS.entrySet().iterator();
        while (bit.hasNext()) {
            var e = bit.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            AltarBond bond = e.getValue();
            if (player == null || !player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(bond.altar)) > 100) {
                bit.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            bond.ticks++;
            Vec3 hold = Vec3.atCenterOf(bond.altar).add(0, 1.6 + Math.min(bond.ticks, 25) * 0.03, 0);
            player.connection.teleport(hold.x, hold.y, hold.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            Vec3 from = Vec3.atCenterOf(bond.altar).add(0, 0.5, 0);
            for (int i = 0; i <= 12; i++) {
                Vec3 p = from.lerp(hold, i / 12.0);
                level.sendParticles(BLACK, p.x + level.random.nextGaussian() * 0.15, p.y, p.z + level.random.nextGaussian() * 0.15, 1, 0, 0, 0, 0);
            }
            if (bond.ticks % 12 == 0) {
                level.playSound(null, bond.altar, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.7F, 0.5F + bond.ticks / 140F);
            }
            if (bond.ticks >= 70) {
                bit.remove();
                LAST_BOND.put(player.getUUID(), level.getGameTime());
                int roll = level.random.nextInt(100);
                if (roll < 5) {
                    player.sendSystemMessage(Component.literal("The altar rejects you."));
                    player.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                } else {
                    level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 1.2F, 0.7F);
                    level.sendParticles(BLACK, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 0.9, 0.5, 0.1);
                    SymbioteHost.bond(player, roll >= 70);
                }
            }
        }
    }

    public static void clear() {
        DRAGS.clear();
        BONDS.clear();
        LAST_BOND.clear();
    }
}
