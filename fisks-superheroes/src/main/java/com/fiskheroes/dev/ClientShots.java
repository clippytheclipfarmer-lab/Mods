package com.fiskheroes.dev;

import com.fiskheroes.FiskHeroes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.threetag.palladium.network.AbilityKeyPressedMessage;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityReference;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Development aid, off unless the system property {@code fiskheroes.shots} names an output directory (the
 * {@code runShots} Gradle task sets it): creates a flat creative world, puts every hero's suit on the player, photographs it
 * from the front, the back and three quarters, shows the creative tab, and quits. Meant to run under Xvfb with software GL.
 */
public class ClientShots implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("fiskheroes-shots");
    private static final String[] HEROES = {"iron_man", "captain_america", "thor", "spider_man", "black_panther", "superman", "batman", "flash"};
    private static final String[] SLOTS = {"head", "chest", "legs", "feet"};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private final Deque<Step> steps = new ArrayDeque<>();
    private int phase;
    private int wait;
    private int titleTicks;
    private int worldTicks;
    private int total;
    private File gameDir;

    private record Step(int waitAfter, Consumer<Minecraft> action) {
    }

    @Override
    public void onInitializeClient() {
        String dir = System.getProperty("fiskheroes.shots");
        if (dir == null) {
            return;
        }
        gameDir = new File(dir);
        gameDir.mkdirs();   // Screenshot.grab only creates the screenshots folder inside it
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (++total > 24000) {
            LOGGER.error("[shots] timed out in phase {}", phase);
            mc.stop();
            return;
        }
        if (phase == 0) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
                return;
            }
            if (mc.screen instanceof TitleScreen && ++titleTicks > 60) {
                LOGGER.info("[shots] creating the world");
                createWorld(mc);
                phase = 1;
            }
            return;
        }
        if (phase == 1) {
            if (mc.player != null && mc.level != null && mc.screen == null && ++worldTicks > 120) {
                plan(mc);
                phase = 2;
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.poll();
        if (step == null) {
            LOGGER.info("[shots] done");
            mc.stop();
            return;
        }
        step.action.accept(mc);
        wait = step.waitAfter;
    }

    private void createWorld(Minecraft mc) {
        String name = "shots-" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1234L, false, false);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    private void shot(Minecraft mc, String name) {
        Screenshot.grab(gameDir, name + ".png", mc.getMainRenderTarget(), message -> {
        });
        LOGGER.info("[shots] {}", name);
    }


    /** Presses or releases a Palladium ability key, exactly as the keybind would. */
    private void key(String hero, String ability, boolean pressed) {
        new AbilityKeyPressedMessage(new AbilityReference(new ResourceLocation(FiskHeroes.MOD_ID, hero), ability), pressed).send();
    }

    private void suit(Minecraft mc, String hero) {
        for (int i = 0; i < 4; i++) {
            command(mc, "item replace entity @s armor." + SLOTS[i] + " with " + FiskHeroes.MOD_ID + ":" + hero + "_" + PIECES[i]);
        }
    }

    private void idle(int ticks) {
        steps.add(new Step(ticks, m -> {
        }));
    }

    private void look(Minecraft mc, float yaw, float pitch) {
        command(mc, "tp @s 0 -60 0 " + yaw + " " + pitch);
    }

    /** One ability demo: dress as the hero, wait for the power and the energy bar, press the key(s), photograph, release. */
    private void demo(String hero, String name, String[] keys, int run, float yaw, float pitch, CameraType camera, Consumer<Minecraft> prepare) {
        demo(hero, name, keys, 40, run, yaw, pitch, camera, prepare);
    }

    private void demo(String hero, String name, String[] keys, int charge, int run, float yaw, float pitch, CameraType camera, Consumer<Minecraft> prepare) {
        steps.add(new Step(8, m -> {
            suit(m, hero);
            command(m, "item replace entity @s weapon.mainhand with minecraft:air");
            command(m, "effect clear @s");
            look(m, yaw, pitch);
            m.options.setCameraType(camera);
            if (prepare != null) {
                prepare.accept(m);
            }
        }));
        idle(charge);
        steps.add(new Step(run, m -> {
            for (String k : keys) {
                key(hero, k, true);
            }
        }));
        steps.add(new Step(2, m -> {
            for (String k : keys) {
                AbilityInstance instance = AbilityUtil.getInstance(m.player, new ResourceLocation(FiskHeroes.MOD_ID, hero), k);
                LOGGER.info("[shots] state {}.{}: {}", hero, k, instance == null ? "no such ability (power not active)"
                        : "unlocked=" + instance.isUnlocked() + " enabled=" + instance.isEnabled() + " cooldown=" + instance.cooldown);
            }
        }));
        steps.add(new Step(5, m -> shot(m, "ability_" + name)));
        steps.add(new Step(15, m -> {
            for (String k : keys) {
                key(hero, k, false);
            }
        }));
    }

    private void plan(Minecraft mc) {
        steps.add(new Step(10, m -> {
            m.options.hideGui = true;
            m.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            command(m, "gamemode creative");
            command(m, "time set noon");
            command(m, "weather clear");
        }));
        for (String hero : HEROES) {
            steps.add(new Step(8, m -> {
                for (int i = 0; i < 4; i++) {
                    command(m, "item replace entity @s armor." + SLOTS[i] + " with " + FiskHeroes.MOD_ID + ":" + hero + "_" + PIECES[i]);
                }
                command(m, "item replace entity @s weapon.mainhand with minecraft:air");
                command(m, "tp @s 0 -60 0 0 0");
                m.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }));
            steps.add(new Step(40, m -> {
            }));
            steps.add(new Step(6, m -> shot(m, hero + "_front")));
            steps.add(new Step(10, m -> {
                command(m, "tp @s 0 -60 0 35 0");
            }));
            steps.add(new Step(25, m -> {
            }));
            steps.add(new Step(6, m -> shot(m, hero + "_34")));
            steps.add(new Step(10, m -> {
                command(m, "tp @s 0 -60 0 0 0");
                m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }));
            steps.add(new Step(25, m -> {
            }));
            steps.add(new Step(6, m -> shot(m, hero + "_back")));
        }
        // Thor with the hammer in hand, Cap with the shield.
        steps.add(new Step(8, m -> {
            for (int i = 0; i < 4; i++) {
                command(m, "item replace entity @s armor." + SLOTS[i] + " with " + FiskHeroes.MOD_ID + ":thor_" + PIECES[i]);
            }
            command(m, "item replace entity @s weapon.mainhand with " + FiskHeroes.MOD_ID + ":mjolnir");
            m.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }));
        steps.add(new Step(40, m -> {
        }));
        steps.add(new Step(6, m -> shot(m, "thor_mjolnir")));

        // ---- ability demos
        CameraType back = CameraType.THIRD_PERSON_BACK;
        CameraType front = CameraType.THIRD_PERSON_FRONT;
        steps.add(new Step(10, m -> command(m, "fill -12 -60 9 12 -40 9 minecraft:stone_bricks")));   // a wall to grapple to
        demo("iron_man", "iron_man_repulsors", new String[]{"repulsor_aim"}, 110, 18, 0, 14, front, null);
        demo("iron_man", "iron_man_unibeam", new String[]{"unibeam"}, 140, 14, 0, 12, front, null);
        demo("iron_man", "iron_man_missile", new String[]{"missile"}, 140, 3, 0, 4, front, null);
        demo("superman", "superman_heat_vision", new String[]{"heat_vision"}, 14, 0, 14, front, null);
        demo("superman", "superman_freeze_breath", new String[]{"freeze_breath"}, 22, 0, 14, front, null);
        demo("thor", "thor_lightning_beam", new String[]{"lightning_aim"}, 14, 0, 14, front, null);
        demo("thor", "thor_lightning_strike", new String[]{"lightning_strike"}, 6, 0, 0, front,
                m -> command(m, "item replace entity @s weapon.mainhand with " + FiskHeroes.MOD_ID + ":mjolnir"));
        demo("thor", "thor_storm", new String[]{"storm"}, 20, 0, 0, front,
                m -> command(m, "item replace entity @s weapon.mainhand with " + FiskHeroes.MOD_ID + ":mjolnir"));
        demo("thor", "thor_hammer_throw", new String[]{"hammer_throw"}, 5, 0, 8, front,
                m -> command(m, "item replace entity @s weapon.mainhand with " + FiskHeroes.MOD_ID + ":mjolnir"));
        demo("captain_america", "cap_shield_throw", new String[]{"shield_throw"}, 6, 0, 8, front,
                m -> command(m, "item replace entity @s weapon.mainhand with " + FiskHeroes.MOD_ID + ":vibranium_shield"));
        demo("batman", "batman_batarang", new String[]{"batarang"}, 4, 0, 8, front, null);
        demo("batman", "batman_smoke_bomb", new String[]{"smoke_bomb"}, 6, 0, 0, back, null);
        demo("batman", "batman_grapple", new String[]{"grapple"}, 10, 0, -12, back, null);
        demo("spider_man", "spider_web_zip", new String[]{"web_zip"}, 10, 0, -12, back, null);
        demo("spider_man", "spider_web_shot", new String[]{"web_shot"}, 3, 0, 4, front, null);
        demo("black_panther", "panther_claws", new String[]{"claws"}, 15, 0, 0, front, null);
        demo("flash", "flash_speed_force", new String[]{"speed_force"}, 40, 0, 0, back,
                m -> {
                    m.options.keyUp.setDown(true);
                    m.options.keySprint.setDown(true);
                });
        steps.add(new Step(5, m -> {
            m.options.keyUp.setDown(false);
            m.options.keySprint.setDown(false);
        }));
        // wall crawl: stand at the wall and walk into it with the jump key held
        steps.add(new Step(8, m -> {
            suit(m, "spider_man");
            command(m, "weather clear");
            command(m, "tp @s 0 -60 7 0 0");
            m.options.setCameraType(back);
        }));
        idle(40);
        steps.add(new Step(40, m -> {
            key("spider_man", "wall_climb", true);
            m.options.keyUp.setDown(true);
            m.options.keyJump.setDown(true);
        }));
        steps.add(new Step(2, m -> {
            AbilityInstance instance = AbilityUtil.getInstance(m.player, new ResourceLocation(FiskHeroes.MOD_ID, "spider_man"), "wall_climb");
            LOGGER.info("[shots] state spider_man.wall_climb: {} at y={}", instance == null ? "none" : "enabled=" + instance.isEnabled(), m.player.getY());
        }));
        steps.add(new Step(5, m -> shot(m, "ability_spider_wall_crawl")));
        steps.add(new Step(5, m -> {
            key("spider_man", "wall_climb", false);
            m.options.keyUp.setDown(false);
            m.options.keyJump.setDown(false);
        }));
        // ---- every item in the inventory
        steps.add(new Step(10, m -> {
            m.options.hideGui = false;
            command(m, "gamemode survival");
            command(m, "clear @s");
            command(m, "effect clear @s");
            for (String hero : HEROES) {
                for (String piece : PIECES) {
                    command(m, "give @s " + FiskHeroes.MOD_ID + ":" + hero + "_" + piece);
                }
            }
            for (String gadget : new String[]{"vibranium_shield", "mjolnir", "batarang", "kryptonite"}) {
                command(m, "give @s " + FiskHeroes.MOD_ID + ":" + gadget);
            }
        }));
        idle(30);
        steps.add(new Step(30, m -> m.setScreen(new InventoryScreen(m.player))));
        steps.add(new Step(6, m -> shot(m, "inventory")));
        // The creative tab.
        steps.add(new Step(30, m -> {
            m.options.hideGui = false;
            try {
                var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation(FiskHeroes.MOD_ID, "heroes"));
                var field = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                field.setAccessible(true);
                field.set(null, tab);
            } catch (Exception e) {
                LOGGER.warn("[shots] could not select the tab", e);
            }
            m.setScreen(new CreativeModeInventoryScreen(m.player, m.player.connection.enabledFeatures(), true));
        }));
        steps.add(new Step(6, m -> shot(m, "creative_tab")));
    }
}
