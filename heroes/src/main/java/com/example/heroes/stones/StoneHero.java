package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import com.example.heroes.stones.ability.CommonAbilities;
import com.example.heroes.stones.ability.MindAbilities;
import com.example.heroes.stones.ability.PowerAbilities;
import com.example.heroes.stones.ability.RealityAbilities;
import com.example.heroes.stones.ability.SoulAbilities;
import com.example.heroes.stones.ability.SpaceAbilities;
import com.example.heroes.stones.ability.StoneAbilities;
import com.example.heroes.stones.ability.TimeAbilities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladiumcore.registry.DeferredRegister;

/**
 * Registers the stone abilities and runs the worn-stone sync and the passive rules of the stones.
 * The kits follow Pugmeowla's Infinity Stone Core (see README): Space - teleport, telekinesis, force field, black hole;
 * Mind - flight, intangibility, pacify, mind beam; Power - punch strength, beam, explosions, meteors; Reality - resize,
 * block copy, invisibility, weather, bubbles; Time - freeze, time rate, day cycle; Soul - army, immortality, glow.
 */
public final class StoneHero {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(HeroesMod.STONES, Ability.REGISTRY);

    static {
        // Older abilities (some are no longer used by the stone powers but stay registered for saved data and addons).
        ABILITIES.register("blink", StoneAbilities.Blink::new);
        ABILITIES.register("telekinesis", StoneAbilities.Telekinesis::new);
        ABILITIES.register("reality_shift", StoneAbilities.RealityShift::new);
        ABILITIES.register("power_blast", StoneAbilities.PowerBlast::new);
        ABILITIES.register("rewind", StoneAbilities.Rewind::new);
        ABILITIES.register("soul_drain", StoneAbilities.SoulDrain::new);
        ABILITIES.register("snap", StoneAbilities.Snap::new);

        ABILITIES.register("effect", CommonAbilities.Effect::new);
        ABILITIES.register("cleanse", CommonAbilities.Cleanse::new);

        ABILITIES.register("step_assist", SpaceAbilities.StepAssist::new);
        ABILITIES.register("force_field", SpaceAbilities.ForceField::new);
        ABILITIES.register("black_hole", SpaceAbilities.BlackHole::new);

        ABILITIES.register("pacify", MindAbilities.Pacify::new);
        ABILITIES.register("unlock_recipes", MindAbilities.UnlockRecipes::new);

        ABILITIES.register("power_punch", PowerAbilities.PowerPunch::new);
        ABILITIES.register("empower", PowerAbilities.Empower::new);
        ABILITIES.register("controlled_explosion", PowerAbilities.ControlledExplosion::new);
        ABILITIES.register("power_beam", PowerAbilities.PowerBeam::new);
        ABILITIES.register("beam_level", PowerAbilities.BeamLevel::new);
        ABILITIES.register("meteor_storm", PowerAbilities.MeteorStorm::new);

        ABILITIES.register("resize", RealityAbilities.Resize::new);
        ABILITIES.register("resize_step", RealityAbilities.ResizeStep::new);
        ABILITIES.register("block_copy", RealityAbilities.BlockCopy::new);
        ABILITIES.register("reality_effects", RealityAbilities.RealityEffects::new);
        ABILITIES.register("weather", RealityAbilities.Weather::new);
        ABILITIES.register("bubble", RealityAbilities.Bubble::new);

        ABILITIES.register("time_freeze", TimeAbilities.TimeFreeze::new);
        ABILITIES.register("fast_forward", TimeAbilities.FastForward::new);
        ABILITIES.register("daylight_toggle", TimeAbilities.DaylightToggle::new);
        ABILITIES.register("time_rate", TimeAbilities.TimeRate::new);
        ABILITIES.register("time_rate_step", TimeAbilities.RateStep::new);

        ABILITIES.register("summon_army", SoulAbilities.SummonArmy::new);
    }

    private StoneHero() {
    }

    public static void init() {
        ABILITIES.register();
        StoneScale.init();
        StoneSystems.init();
        StoneCommands.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StoneSystems.tick(server);
            StoneWear.tick(server);
            PowerStonePulse.tickWaves(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PowerStonePulse.tick(player);
                LooseStones.tick(player);
                if (StoneBoost.has(player, InfinityStone.TIME)) {
                    StoneAbilities.recordHistory(player);
                    // Time flows faster for the wearer: every ability cooldown ticks down twice as fast, plus the time-rate field.
                    int extra = 1 + TimeAbilities.extraCooldownTicks(player);
                    for (AbilityInstance instance : AbilityUtil.getInstances(player)) {
                        if (instance.cooldown > 0) {
                            instance.cooldown = Math.max(0, instance.cooldown - extra);
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
            LooseStones.forget(handler.getPlayer().getUUID());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PowerStonePulse.clear();
            LooseStones.clear();
            StoneUtil.clearTasks();
        });
    }
}
