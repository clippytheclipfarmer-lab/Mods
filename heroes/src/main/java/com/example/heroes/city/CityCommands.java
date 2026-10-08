package com.example.heroes.city;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** /city create|remove|list|info|finish|forget|set */
final class CityCommands {
    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CityManager.data().regions.keySet(), builder);

    private CityCommands() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> city = Commands.literal("city").requires(src -> src.hasPermission(2));

        city.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            CityWand.Selection sel = CityWand.selection(player);
                            if (sel == null) {
                                ctx.getSource().sendFailure(Component.literal("Select two corners with the City Wand first (left-click and right-click blocks)."));
                                return 0;
                            }
                            return create(ctx.getSource(), StringArgumentType.getString(ctx, "name"), sel.pos1(), sel.pos2(), sel.dimension());
                        })
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                                ctx.getSource().getLevel().dimension()))))));

        city.then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                    CityManager.data().regions.remove(r.name);
                    CityManager.data().records.remove(r.name);
                    CityManager.data().setDirty();
                    CityManager.removeRegion(r.name);
                    CityManager.refreshCache();
                    reply(ctx.getSource(), "Removed city '" + r.name + "'.");
                }))));

        city.then(Commands.literal("list").executes(ctx -> {
            var regions = CityManager.data().regions.values();
            if (regions.isEmpty()) {
                reply(ctx.getSource(), "No city regions yet.");
            }
            for (CityRegion r : regions) {
                reply(ctx.getSource(), r.name + ": " + r.min.toShortString() + " to " + r.max.toShortString()
                        + " (" + r.dimension.location() + "), " + CityManager.pending(r.name) + " blocks waiting to be rebuilt");
            }
            return regions.size();
        }));

        city.then(Commands.literal("info").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> reply(ctx.getSource(),
                        r.name + ": " + CityManager.pending(r.name) + " blocks pending, rebuild " + r.rebuildSeconds + "s, idle "
                                + r.idleSeconds + "s, builders " + r.maxBuilders + ", sounds " + r.sounds + ", district " + r.district
                                + ", " + (r.blocksPerSecond > 0 ? r.blocksPerSecond + " blocks/s" : "paced by rebuild_seconds") + ", order " + r.order
                                + ", tracking " + r.tracking + ", particles " + r.particles)))));

        city.then(Commands.literal("finish").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                    CityManager.finishFast(r.name);
                    reply(ctx.getSource(), "Rebuilding '" + r.name + "' now (about 5 seconds).");
                }))));

        city.then(Commands.literal("forget").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                    CityManager.data().records(r.name).states.clear();
                    CityManager.data().records(r.name).blockEntities.clear();
                    CityManager.data().setDirty();
                    reply(ctx.getSource(), "Forgot all recorded damage in '" + r.name + "' (the current state is now the baseline).");
                }))));

        var set = Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .then(Commands.literal("rebuild_seconds").then(Commands.argument("value", IntegerArgumentType.integer(1, 86400))
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.rebuildSeconds = IntegerArgumentType.getInteger(ctx, "value");
                            saved(ctx.getSource());
                        }))))
                .then(Commands.literal("idle_seconds").then(Commands.argument("value", IntegerArgumentType.integer(0, 3600))
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.idleSeconds = IntegerArgumentType.getInteger(ctx, "value");
                            saved(ctx.getSource());
                        }))))
                .then(Commands.literal("builders").then(Commands.argument("value", IntegerArgumentType.integer(0, 30))
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.maxBuilders = IntegerArgumentType.getInteger(ctx, "value");
                            saved(ctx.getSource());
                        }))))
                .then(Commands.literal("sounds").then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.sounds = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "value");
                            saved(ctx.getSource());
                        })))));
        city.then(set);

        // More per-zone settings. (Brigadier merges these with the "set <name>" nodes above.)
        city.then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                .then(Commands.literal("district").then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.district = StringArgumentType.getString(ctx, "value");
                            saved(ctx.getSource());
                            CityManager.refreshCache();
                        }))))
                .then(Commands.literal("blocks_per_second").then(Commands.argument("value", IntegerArgumentType.integer(0, 100000))
                        .executes(ctx -> withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
                            r.blocksPerSecond = IntegerArgumentType.getInteger(ctx, "value");
                            saved(ctx.getSource());
                        }))))
                .then(Commands.literal("order").then(Commands.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("bottom_up", "random"), b))
                        .executes(ctx -> choice(ctx, "order", java.util.List.of("bottom_up", "random")))))
                .then(Commands.literal("tracking").then(Commands.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("powers", "all"), b))
                        .executes(ctx -> choice(ctx, "tracking", java.util.List.of("powers", "all")))))
                .then(Commands.literal("particles").then(Commands.argument("value", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("block", "marker", "cloud", "none"), b))
                        .executes(ctx -> choice(ctx, "particles", java.util.List.of("block", "marker", "cloud", "none")))))));

        city.then(Commands.literal("where").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            String district = CityManager.getDistrict(player);
            reply(ctx.getSource(), district == null ? "You are not in a city zone." : "You are in zone '" + CityManager.getZone(player) + "', district '" + district + "'.");
            return 1;
        }));

        city.then(Commands.literal("poi")
                .then(Commands.literal("add").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                                    BlockPos p = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    CityManager.registerPOI(StringArgumentType.getString(ctx, "id"), p.getX(), p.getY(), p.getZ(), StringArgumentType.getString(ctx, "name"));
                                    reply(ctx.getSource(), "Registered point of interest '" + StringArgumentType.getString(ctx, "id") + "'.");
                                    return 1;
                                })))))
                .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    boolean removed = CityManager.data().pois.remove(StringArgumentType.getString(ctx, "id")) != null;
                    CityManager.data().setDirty();
                    reply(ctx.getSource(), removed ? "Removed." : "No such point of interest.");
                    return removed ? 1 : 0;
                })))
                .then(Commands.literal("list").executes(ctx -> listPoints(ctx.getSource(), CityManager.getPOIs(), "points of interest"))));

        city.then(Commands.literal("residential")
                .then(Commands.literal("add").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("tier", StringArgumentType.word()).executes(ctx -> {
                                    BlockPos p = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    CityManager.registerResidential(StringArgumentType.getString(ctx, "id"), p.getX(), p.getY(), p.getZ(), StringArgumentType.getString(ctx, "tier"));
                                    reply(ctx.getSource(), "Registered residential zone '" + StringArgumentType.getString(ctx, "id") + "'.");
                                    return 1;
                                })))))
                .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    boolean removed = CityManager.data().residentials.remove(StringArgumentType.getString(ctx, "id")) != null;
                    CityManager.data().setDirty();
                    reply(ctx.getSource(), removed ? "Removed." : "No such residential zone.");
                    return removed ? 1 : 0;
                })))
                .then(Commands.literal("list").executes(ctx -> listPoints(ctx.getSource(), CityManager.getResidentials(), "residential zones"))));

        dispatcher.register(city);
    }

    private static int create(CommandSourceStack src, String name, BlockPos a, BlockPos b, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (CityManager.data().regions.containsKey(name)) {
            src.sendFailure(Component.literal("A city called '" + name + "' already exists."));
            return 0;
        }
        CityRegion region = new CityRegion(name, dim, a, b);
        CityManager.data().regions.put(name, region);
        CityManager.data().setDirty();
        CityManager.refreshCache();
        long volume = (long) (region.max.getX() - region.min.getX() + 1) * (region.max.getY() - region.min.getY() + 1) * (region.max.getZ() - region.min.getZ() + 1);
        reply(src, "Created city '" + name + "' (" + volume + " blocks). Destroyed blocks inside it will now be rebuilt automatically.");
        return 1;
    }

    private static int choice(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String field, java.util.List<String> allowed) {
        String value = StringArgumentType.getString(ctx, "value");
        if (!allowed.contains(value)) {
            ctx.getSource().sendFailure(Component.literal("Choose one of " + allowed + "."));
            return 0;
        }
        return withRegion(ctx.getSource(), StringArgumentType.getString(ctx, "name"), r -> {
            switch (field) {
                case "order" -> r.order = value;
                case "tracking" -> r.tracking = value;
                default -> r.particles = value;
            }
            saved(ctx.getSource());
        });
    }

    private static int listPoints(CommandSourceStack src, java.util.List<CityPoint> points, String what) {
        if (points.isEmpty()) {
            reply(src, "No " + what + " registered.");
        }
        for (CityPoint p : points) {
            reply(src, p.id() + ": " + p.label() + " at " + p.x() + " " + p.y() + " " + p.z() + " (" + p.dimension() + ")");
        }
        return points.size();
    }

    private static int withRegion(CommandSourceStack src, String name, Consumer<CityRegion> action) {
        CityRegion region = CityManager.data().regions.get(name);
        if (region == null) {
            src.sendFailure(Component.literal("No city called '" + name + "'."));
            return 0;
        }
        action.accept(region);
        return 1;
    }

    private static void saved(CommandSourceStack src) {
        CityManager.data().setDirty();
        reply(src, "Updated.");
    }

    private static void reply(CommandSourceStack src, String message) {
        src.sendSuccess(() -> Component.literal(message), false);
    }
}
