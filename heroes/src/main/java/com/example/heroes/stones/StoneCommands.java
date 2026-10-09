package com.example.heroes.stones;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Commands the stones hand out: {@code /space_tp x y z} (Space Stone), {@code /time_set <time>} (Time Stone) and
 * {@code /locateplayer <name>} (Soul Stone). Operators can always use them.
 */
final class StoneCommands {
    private StoneCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean allowed(CommandSourceStack src, InfinityStone stone) {
        return src.hasPermission(2) || (src.getEntity() instanceof LivingEntity living && StoneBoost.has(living, stone));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("space_tp").requires(src -> allowed(src, InfinityStone.SPACE))
                .then(Commands.argument("pos", Vec3Argument.vec3()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Vec3 to = Vec3Argument.getVec3(ctx, "pos");
                    player.teleportTo(player.serverLevel(), to.x, to.y, to.z, player.getYRot(), player.getXRot());
                    player.fallDistance = 0;
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format("The Space Stone folds you to %.1f %.1f %.1f.", to.x, to.y, to.z)), false);
                    return 1;
                })));

        dispatcher.register(Commands.literal("time_set").requires(src -> allowed(src, InfinityStone.TIME))
                .then(Commands.argument("time", StringArgumentType.word()).suggests((c, b) -> {
                    for (String s : new String[]{"day", "noon", "night", "midnight"}) {
                        b.suggest(s);
                    }
                    return b.buildFuture();
                }).executes(ctx -> {
                    String value = StringArgumentType.getString(ctx, "time").toLowerCase(Locale.ROOT);
                    long time;
                    switch (value) {
                        case "day" -> time = 1000;
                        case "noon" -> time = 6000;
                        case "night" -> time = 13000;
                        case "midnight" -> time = 18000;
                        default -> {
                            try {
                                time = Long.parseLong(value);
                            } catch (NumberFormatException e) {
                                throw new SimpleCommandExceptionType(Component.literal("Use day, noon, night, midnight or a number of ticks.")).create();
                            }
                        }
                    }
                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        level.setDayTime(time);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("The Time Stone sets the clock to " + value + "."), false);
                    return 1;
                })));

        dispatcher.register(Commands.literal("locateplayer").requires(src -> allowed(src, InfinityStone.SOUL))
                .then(Commands.argument("name", StringArgumentType.greedyString()).suggests((c, b) -> {
                    c.getSource().getServer().getPlayerList().getPlayers().forEach(p -> b.suggest(p.getGameProfile().getName()));
                    return b.buildFuture();
                }).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name").trim();
                    Entity found = null;
                    ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if (player != null) {
                        found = player;
                    } else {
                        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                            for (Entity e : level.getAllEntities()) {
                                if (e.hasCustomName() && e.getCustomName().getString().equalsIgnoreCase(name)) {
                                    found = e;
                                    break;
                                }
                            }
                            if (found != null) {
                                break;
                            }
                        }
                    }
                    if (found == null) {
                        throw new SimpleCommandExceptionType(Component.literal("The Soul Stone finds no soul called " + name + ".")).create();
                    }
                    Entity target = found;
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s is at %.0f %.0f %.0f in %s.", name, target.getX(), target.getY(), target.getZ(),
                            target.level().dimension().location())), false);
                    return 1;
                })));
    }
}
