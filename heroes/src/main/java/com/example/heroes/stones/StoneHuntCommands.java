package com.example.heroes.stones;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/** /stonehunt status | reroll | place | give <stone> [player] (op only; status spoils the hunt). */
final class StoneHuntCommands {
    private StoneHuntCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stonehunt").requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    StoneHuntData data = StoneHuntData.get(ctx.getSource().getServer());
                    if (!data.setupDone) {
                        reply(ctx.getSource(), "The hunt has not been set up yet (it happens when the first player joins).");
                        return 0;
                    }
                    for (InfinityStone stone : InfinityStone.values()) {
                        var p = data.placements.get(stone);
                        reply(ctx.getSource(), stone.displayName() + ": " + (p == null ? "unplaced" : p.type + " " + p.where + " "
                                + p.pos.toShortString() + (p.placed ? " [placed]" : " [not placed yet]")));
                    }
                    return 1;
                }))
                .then(Commands.literal("reroll").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    StoneHunt.setup(server, StoneHuntData.get(server));
                    reply(ctx.getSource(), "Re-rolled. New hidden stones are placed over the next few seconds; shrines are built when a player visits the planet. Stones from the old roll stay where they are.");
                    return 1;
                }))
                .then(Commands.literal("place").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    StoneHunt.placeHiddenNow(server);
                    int shrines = StoneHunt.buildAllShrines(server);
                    reply(ctx.getSource(), "Placed hidden stones and built " + shrines + " shrine(s).");
                    return 1;
                }))
                .then(Commands.literal("give").then(Commands.argument("stone", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(InfinityStone.values()).map(s -> s.name().toLowerCase()), b))
                        .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "stone"), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "stone"), EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int give(CommandSourceStack src, String name, ServerPlayer player) {
        InfinityStone stone = InfinityStone.byName(name);
        if (stone == null) {
            src.sendFailure(Component.literal("Unknown stone '" + name + "'."));
            return 0;
        }
        player.getInventory().add(new ItemStack(StoneItems.get(stone)));
        reply(src, "Gave " + stone.displayName() + " to " + player.getName().getString() + ".");
        return 1;
    }

    private static void reply(CommandSourceStack src, String message) {
        src.sendSuccess(() -> Component.literal(message), false);
    }
}
