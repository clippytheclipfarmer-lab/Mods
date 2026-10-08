package com.example.heroes.space;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /space list | land <planet> [players] | orbit <planet> [players] */
final class SpaceCommands {
    private static final SuggestionProvider<CommandSourceStack> PLANETS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Planets.all().stream().map(p -> p.id.getPath()), builder);

    private SpaceCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("space").requires(src -> src.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    for (PlanetDef p : Planets.all()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(p.id + ": " + p.name + " (" + (p.station ? "station" : p.dimension.location())
                                + "), gravity " + p.gravity + ", oxygen " + p.oxygen + ", hazard " + p.hazard.name().toLowerCase()), false);
                    }
                    return Planets.all().size();
                }))
                .then(Commands.literal("land").then(Commands.argument("planet", StringArgumentType.word()).suggests(PLANETS)
                        .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "planet"), ctx.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                                        n += run(ctx.getSource(), StringArgumentType.getString(ctx, "planet"), p, true);
                                    }
                                    return n;
                                }))))
                .then(Commands.literal("orbit").then(Commands.argument("planet", StringArgumentType.word()).suggests(PLANETS)
                        .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "planet"), ctx.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                                        n += run(ctx.getSource(), StringArgumentType.getString(ctx, "planet"), p, false);
                                    }
                                    return n;
                                })))));
    }

    private static int run(CommandSourceStack src, String name, ServerPlayer player, boolean land) {
        PlanetDef def = Planets.find(name);
        if (def == null) {
            src.sendFailure(Component.literal("Unknown planet '" + name + "'. Try /space list."));
            return 0;
        }
        boolean ok = land && def.landable() ? SpaceSystem.land(src.getServer(), player, def) : SpaceSystem.orbit(src.getServer(), player, def);
        return ok ? 1 : 0;
    }
}
