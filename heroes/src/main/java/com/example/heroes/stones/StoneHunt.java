package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import com.example.heroes.space.PlanetDef;
import com.example.heroes.space.Planets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Infinity Stone hunt. When the world first gets a player, the six stones are shuffled: two go into secret
 * spots from the map's location list (config/heroes/stone_hunt_locations.json), the other four each get a shrine
 * on one of the generated planets (built the first time a player is on that planet).
 */
public final class StoneHunt {
    public record Hidden(String id, BlockPos pos, String hint) {
    }

    private static final int SHRINE_OFFSET = 36;
    private static final int HIDDEN_STONES = 2;
    private static final List<Runnable> QUEUE = new ArrayList<>();

    private StoneHunt() {
    }

    public static void init() {
        StoneItems.init();
        StoneHuntCommands.init();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StoneHuntData data = StoneHuntData.get(server);
            if (!data.setupDone) {
                setup(server, data);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(StoneHunt::tick);
    }

    // ------------------------------------------------------------------ setup

    public static Path locationsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("heroes").resolve("stone_hunt_locations.json");
    }

    /** Reads the hidden-spot list; creates an empty example file the first time. */
    public static List<Hidden> loadLocations() {
        Path file = locationsFile();
        List<Hidden> list = new ArrayList<>();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "{\n  \"locations\": [\n    {\"id\": \"example\", \"x\": 0, \"y\": 0, \"z\": 0, \"hint\": \"delete this and paste the output of tools/find_hidden_spots.py\"}\n  ]\n}\n");
                return list;
            }
            JsonObject root = new Gson().fromJson(Files.readString(file), JsonObject.class);
            JsonArray arr = root.getAsJsonArray("locations");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                if (id.equals("example")) {
                    continue;
                }
                list.add(new Hidden(id, new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()),
                        o.has("hint") ? o.get("hint").getAsString() : ""));
            }
        } catch (IOException | RuntimeException e) {
            HeroesMod.LOGGER.error("Could not read {}: {}", file, e.getMessage());
        }
        return list;
    }

    /** (Re)rolls where every stone goes. Stones already placed in the world are not removed. */
    public static void setup(MinecraftServer server, StoneHuntData data) {
        RandomSource random = RandomSource.create(server.overworld().getSeed() ^ 0x5EED5701L);
        List<InfinityStone> stones = new ArrayList<>(List.of(InfinityStone.values()));
        shuffle(stones, random);

        List<Hidden> locations = new ArrayList<>(loadLocations());
        shuffle(locations, random);
        List<PlanetDef> planets = new ArrayList<>();
        for (PlanetDef def : Planets.all()) {
            if (def.landable() && !def.dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) {
                planets.add(def);
            }
        }
        planets.sort(java.util.Comparator.comparing(d -> d.id.toString()));
        shuffle(planets, random);

        data.placements.clear();
        // The first two stones of the shuffle go into secret spots in the map, the other four to planet shrines.
        for (int i = 0; i < stones.size(); i++) {
            StoneHuntData.Placement placement = new StoneHuntData.Placement();
            if (i < HIDDEN_STONES) {
                if (i < locations.size()) {
                    Hidden loc = locations.get(i);
                    placement.type = "hidden";
                    placement.where = loc.id();
                    placement.pos = loc.pos();
                }
            } else if (i - HIDDEN_STONES < planets.size()) {
                PlanetDef def = planets.get(i - HIDDEN_STONES);
                placement.type = "shrine";
                placement.where = def.id.toString();
                placement.pos = new BlockPos(def.landingX + SHRINE_OFFSET, 0, def.landingZ + SHRINE_OFFSET);
            }
            data.placements.put(stones.get(i), placement);
        }
        data.setupDone = true;
        data.setDirty();

        long unplaced = data.placements.values().stream().filter(x -> x.type.equals("unplaced")).count();
        if (locations.size() < HIDDEN_STONES) {
            HeroesMod.LOGGER.warn("Stone hunt: only {} hidden locations in {} (need at least {}). {} stone(s) are unplaced; add locations and run /stonehunt reroll.",
                    locations.size(), locationsFile(), HIDDEN_STONES, unplaced);
        }
        // Hidden stones are put into the map a little at a time so loading far chunks does not stall the server.
        for (var entry : data.placements.entrySet()) {
            if (entry.getValue().type.equals("hidden")) {
                QUEUE.add(() -> placeHidden(server, data, entry.getKey(), entry.getValue()));
            }
        }
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    // ------------------------------------------------------------------ hidden spots

    private static void placeHidden(MinecraftServer server, StoneHuntData data, InfinityStone stone, StoneHuntData.Placement placement) {
        if (placement.placed) {
            return; // already done (for example by /stonehunt place before the queued job ran)
        }
        ServerLevel level = server.overworld();
        BlockPos pos = placement.pos;
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // loads (or generates) the chunk
        ItemStack item = new ItemStack(StoneItems.get(stone));

        // Prefer an existing container at or next to the spot: the stone goes into a chest the map already hides.
        Container container = findContainer(level, pos);
        if (container == null) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
            container = findContainer(level, pos);
        }
        if (container != null && insert(container, item, level.random)) {
            placement.placed = true;
            data.setDirty();
            HeroesMod.LOGGER.info("Stone hunt: {} hidden at {} ({})", stone.displayName(), pos.toShortString(), placement.where);
        } else {
            HeroesMod.LOGGER.error("Stone hunt: could not hide {} at {}", stone.displayName(), pos.toShortString());
        }
    }

    private static Container findContainer(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c && c.getContainerSize() >= 9) {
                return c;
            }
        }
        return null;
    }

    private static boolean insert(Container container, ItemStack stack, RandomSource random) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                empty.add(i);
            }
        }
        if (empty.isEmpty()) {
            return false;
        }
        container.setItem(empty.get(random.nextInt(empty.size())), stack);
        container.setChanged();
        return true;
    }

    // ------------------------------------------------------------------ shrines

    private static void tick(MinecraftServer server) {
        if (!QUEUE.isEmpty() && server.getTickCount() % 10 == 0) {
            QUEUE.remove(0).run();
        }
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        StoneHuntData data = StoneHuntData.get(server);
        if (!data.setupDone) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlanetDef here = Planets.forDimension(player.level().dimension());
            if (here == null) {
                continue;
            }
            for (var entry : data.placements.entrySet()) {
                StoneHuntData.Placement placement = entry.getValue();
                if (placement.type.equals("shrine") && !placement.placed && placement.where.equals(here.id.toString())) {
                    buildShrine(player.serverLevel(), data, entry.getKey(), placement);
                }
            }
        }
    }

    private static void buildShrine(ServerLevel level, StoneHuntData data, InfinityStone stone, StoneHuntData.Placement placement) {
        int x = placement.pos.getX();
        int z = placement.pos.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos base = new BlockPos(x, y, z);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState colored = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(new net.minecraft.resources.ResourceLocation(stone.shrineBlock)).defaultBlockState();

        // Clear a 9x9 area up to 6 blocks high, lay the floor, and put colored accents round the centre.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 6; dy++) {
                    level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
                boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                level.setBlock(base.offset(dx, 0, dz), ring ? colored : bricks, 2);
                level.setBlock(base.offset(dx, -1, dz), bricks, 2);
            }
        }
        for (int cx : new int[]{-4, 4}) {
            for (int cz : new int[]{-4, 4}) {
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(base.offset(cx, dy, cz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
                }
                level.setBlock(base.offset(cx, 5, cz), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        }
        // Pedestal with the chest on top.
        level.setBlock(base.offset(0, 1, 0), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
        BlockPos chest = base.offset(0, 2, 0);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(chest) instanceof Container container && insert(container, new ItemStack(StoneItems.get(stone)), level.random)) {
            placement.placed = true;
            placement.pos = chest;
            data.setDirty();
            HeroesMod.LOGGER.info("Stone hunt: {} shrine built at {} on {}", stone.displayName(), chest.toShortString(), placement.where);
        }
    }

    /** Test helper: builds every unbuilt shrine immediately in the right world. */
    public static int buildAllShrines(MinecraftServer server) {
        StoneHuntData data = StoneHuntData.get(server);
        int n = 0;
        for (var entry : data.placements.entrySet()) {
            StoneHuntData.Placement placement = entry.getValue();
            if (placement.type.equals("shrine") && !placement.placed) {
                PlanetDef def = Planets.get(new net.minecraft.resources.ResourceLocation(placement.where));
                ServerLevel level = def == null ? null : server.getLevel(def.dimension);
                if (level != null) {
                    buildShrine(level, data, entry.getKey(), placement);
                    n++;
                }
            }
        }
        return n;
    }

    public static void placeHiddenNow(MinecraftServer server) {
        StoneHuntData data = StoneHuntData.get(server);
        data.placements.forEach((stone, placement) -> {
            if (placement.type.equals("hidden") && !placement.placed) {
                placeHidden(server, data, stone, placement);
            }
        });
    }
}
