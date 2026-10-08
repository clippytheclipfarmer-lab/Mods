package com.example.heroes.origin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /origin ...} (op): assign races, roll, add XP, inspect. The real character generator calls {@link OriginApi}. */
public final class OriginCommands {
    private OriginCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var raceArg = Commands.argument("race", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Races.all().stream().map(r -> r.id.getPath()), builder));
        dispatcher.register(Commands.literal("origin").requires(src -> src.hasPermission(2))
                .then(Commands.literal("assign").then(raceArg
                        .executes(ctx -> assign(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "race")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> assign(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "race")))
                                .then(Commands.argument("gender", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.List.of("male", "female"), builder))
                                        .executes(ctx -> assign(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "race"),
                                                StringArgumentType.getString(ctx, "gender")))))))
                .then(Commands.literal("random")
                        .executes(ctx -> random(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> random(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reroll")
                        .executes(ctx -> reroll(ctx.getSource(), ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> xp(ctx.getSource(), ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            OriginApi.clear(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.literal("Character cleared."), false);
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int assign(CommandSourceStack src, ServerPlayer player, String name) {
        return assign(src, player, name, player.getRandom().nextBoolean() ? "male" : "female");
    }

    private static int assign(CommandSourceStack src, ServerPlayer player, String name, String gender) {
        Race race = Races.find(name);
        if (race == null) {
            src.sendFailure(Component.literal("Unknown race '" + name + "'."));
            return 0;
        }
        Sheet sheet = OriginApi.assign(player, race, gender);
        src.sendSuccess(() -> Component.literal(player.getName().getString() + " is now a " + sheet.gender + " " + sheet.subtypeDef().name + "."), true);
        return 1;
    }

    private static int random(CommandSourceStack src, ServerPlayer player) {
        Sheet sheet = OriginApi.assignRandom(player);
        if (sheet == null) {
            src.sendFailure(Component.literal("No races are loaded."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(player.getName().getString() + " rolled " + sheet.subtypeDef().name + "."), true);
        return 1;
    }

    private static int reroll(CommandSourceStack src, ServerPlayer player) {
        if (OriginApi.get(player) == null) {
            src.sendFailure(Component.literal("No character yet."));
            return 0;
        }
        OriginApi.rerollScores(player);
        return info(src, player);
    }

    private static int xp(CommandSourceStack src, ServerPlayer player, int amount) {
        if (OriginApi.get(player) == null) {
            src.sendFailure(Component.literal("No character yet."));
            return 0;
        }
        OriginApi.addXp(player, amount, false);
        return info(src, player);
    }

    private static int info(CommandSourceStack src, ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null || sheet.raceDef() == null) {
            src.sendFailure(Component.literal(player.getName().getString() + " has no character."));
            return 0;
        }
        StringBuilder sb = new StringBuilder(sheet.subtypeDef().name + ", level " + sheet.level() + " (" + sheet.xp + " XP)  ");
        for (Ability5e a : Ability5e.values()) {
            int score = sheet.score(a);
            int mod = Ability5e.modifier(score);
            sb.append(a.name()).append(' ').append(score).append(" (").append(mod >= 0 ? "+" : "").append(mod).append(")  ");
        }
        src.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }
}
