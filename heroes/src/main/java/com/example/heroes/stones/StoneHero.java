package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import com.example.heroes.stones.ability.StoneAbilities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladiumcore.registry.DeferredRegister;

/** Registers the stone abilities and runs the worn-stone sync and the Time Stone's passive effects. */
public final class StoneHero {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(HeroesMod.STONES, Ability.REGISTRY);

    static {
        ABILITIES.register("blink", StoneAbilities.Blink::new);
        ABILITIES.register("telekinesis", StoneAbilities.Telekinesis::new);
        ABILITIES.register("reality_shift", StoneAbilities.RealityShift::new);
        ABILITIES.register("power_blast", StoneAbilities.PowerBlast::new);
        ABILITIES.register("rewind", StoneAbilities.Rewind::new);
        ABILITIES.register("soul_drain", StoneAbilities.SoulDrain::new);
        ABILITIES.register("snap", StoneAbilities.Snap::new);
    }

    private StoneHero() {
    }

    public static void init() {
        ABILITIES.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StoneWear.tick(server);
            PowerStonePulse.tickWaves(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PowerStonePulse.tick(player);
                if (StoneBoost.has(player, InfinityStone.TIME)) {
                    StoneAbilities.recordHistory(player);
                    // Time flows faster for the wearer: every ability cooldown ticks down twice as fast.
                    for (AbilityInstance instance : AbilityUtil.getInstances(player)) {
                        if (instance.cooldown > 0) {
                            instance.cooldown--;
                        }
                    }
                } else {
                    StoneAbilities.forgetHistory(player.getUUID());
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StoneWear.forget(handler.getPlayer().getUUID());
            StoneAbilities.forgetHistory(handler.getPlayer().getUUID());
            PowerStonePulse.forget(handler.getPlayer().getUUID());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> PowerStonePulse.clear());
    }
}
