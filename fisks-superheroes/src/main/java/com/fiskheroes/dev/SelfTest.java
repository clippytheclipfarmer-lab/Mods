package com.fiskheroes.dev;

import com.fiskheroes.FiskHeroes;
import com.google.gson.JsonObject;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.threetag.palladium.power.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Development aid, off unless the system property {@code fiskheroes.selftest} is set (the {@code runSelftest} Gradle task
 * sets it): parses every power JSON of this addon with Palladium's own parser right after the addon pack loads, prints the
 * result and exits. It needs no world, so it also runs before the Minecraft EULA prompt.
 */
public class SelfTest implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("fiskheroes-selftest");

    @Override
    public void onInitializeServer() {
        if (System.getProperty("fiskheroes.selftest") == null) {
            return;
        }
        int failures = 0;
        int checked = 0;
        Path dir = FabricLoader.getInstance().getModContainer(FiskHeroes.MOD_ID).orElseThrow()
                .findPath("data/" + FiskHeroes.MOD_ID + "/palladium/powers").orElseThrow();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            for (Path file : files) {
                String name = file.getFileName().toString().replace(".json", "");
                ResourceLocation id = new ResourceLocation(FiskHeroes.MOD_ID, name);
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    JsonObject json = GsonHelper.parse(reader);
                    Power power = Power.fromJSON(id, json);
                    checked++;
                    LOGGER.info("[selftest] ok {} ({} abilities)", id, power.getAbilities().size());
                } catch (Throwable t) {
                    failures++;
                    LOGGER.error("[selftest] FAILED {}", id, t);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[selftest] could not read the powers", e);
            failures++;
        }
        LOGGER.info("[selftest] {} powers parsed, {} failures", checked, failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
