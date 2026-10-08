package com.example.heroes.symbiote;

import com.example.heroes.HeroesMod;
import com.example.heroes.symbiote.klyntar.KlyntarSystem;
import com.example.heroes.symbiote.ability.ConsumeAbility;
import com.example.heroes.symbiote.ability.HungerAbility;
import com.example.heroes.symbiote.ability.RegenAbility;
import com.example.heroes.symbiote.ability.SenseAbility;
import com.example.heroes.symbiote.ability.SuitUpkeepAbility;
import com.example.heroes.symbiote.ability.TendrilAbility;
import com.example.heroes.symbiote.ability.WallClingAbility;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladiumcore.registry.DeferredRegister;

/** The symbiote: meteor event, blob mob, bonded villagers, and the host powers {@code symbiote:symbiote} and {@code symbiote:apex}. */
public final class SymbioteHero {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(HeroesMod.SYMBIOTE, Ability.REGISTRY);

    static {
        ABILITIES.register("tendril", TendrilAbility::new);
        ABILITIES.register("consume", ConsumeAbility::new);
        ABILITIES.register("wall_cling", WallClingAbility::new);
        ABILITIES.register("hunger", HungerAbility::new);
        ABILITIES.register("sense", SenseAbility::new);
        ABILITIES.register("regen", RegenAbility::new);
        ABILITIES.register("upkeep", SuitUpkeepAbility::new);
    }

    private SymbioteHero() {
    }

    public static void init() {
        ABILITIES.register();
        SymbioteEntities.init();
        MeteorEvents.init();
        KlyntarSystem.init();

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(SymbioteHost::tickServer);

        // Damage model of a host: the suit's armor soaks up ordinary damage first (it equals the host's health, then the
        // host takes the rest). Only fire and sonic attacks reach the symbiote itself, which has 5 hearts.
        net.threetag.palladiumcore.event.LivingEntityEvents.HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide && SymbioteHost.isHost(entity)) {
                float hit = amount.get();
                boolean sonic = source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM);
                boolean fire = source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
                boolean suited = SymbioteHost.isSuited(entity);
                SymbioteHost.markHit(entity);
                if (sonic || fire) {
                    SymbioteHost.damageCore(entity, hit, sonic);
                    if (suited) {
                        amount.set(0.0F); // the suit takes it; the host is shielded while the symbiote is wrapped around them
                    }
                } else if (suited) {
                    amount.set(SymbioteHost.absorb(entity, hit));
                } else if (hit >= 6.0F && entity instanceof ServerPlayer) {
                    SymbioteHost.requestSuit(entity); // a hard hit makes the suit spread by itself
                }
            }
            return net.threetag.palladiumcore.event.EventResult.pass();
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            // A host's kills feed the symbiote.
            if (source.getEntity() instanceof ServerPlayer killer && SymbioteHost.isHost(killer) && entity != killer) {
                SymbioteHost.addHunger(killer, -12);
            }
            // When the host dies the symbiote leaves as a blob again.
            if (entity instanceof ServerPlayer host && SymbioteHost.isHost(host) && host.level() instanceof ServerLevel level) {
                SymbioteHost.release(host);
                SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(level);
                if (blob != null) {
                    blob.moveTo(host.getX(), host.getY(), host.getZ(), host.getYRot(), 0F);
                    level.addFreshEntity(blob);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("symbiote").requires(src -> src.hasPermission(2))
                .then(Commands.literal("meteor").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean ok = MeteorEvents.start(player.serverLevel(), player.position());
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("No landing spot found near you."));
                    }
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("blob").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(player.serverLevel());
                    blob.moveTo(player.getX() + 4, player.getY(), player.getZ(), 0F, 0F);
                    blob.setPersistenceRequired();
                    player.serverLevel().addFreshEntity(blob);
                    return 1;
                }))
                .then(Commands.literal("bond").executes(ctx -> {
                    SymbioteHost.bond(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }).then(Commands.literal("apex").executes(ctx -> {
                    SymbioteHost.bond(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                })))
                .then(Commands.literal("klyntar")
                        .then(Commands.literal("build").executes(ctx -> {
                            var level = ctx.getSource().getLevel();
                            boolean ok = KlyntarSystem.onKlyntar(level) && KlyntarSystem.buildNow(level);
                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Built the hive and altar." : "Run this on Klyntar before they are built."), false);
                            return ok ? 1 : 0;
                        }))
                        .then(Commands.literal("knull").executes(ctx -> {
                            var level = ctx.getSource().getLevel();
                            boolean ok = KlyntarSystem.onKlyntar(level) && KlyntarSystem.awakenNow(level);
                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Knull awakens." : "Build the hive first (on Klyntar)."), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("release").executes(ctx -> {
                    SymbioteHost.release(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
