package com.example.heroes.origin;

import com.example.heroes.symbiote.klyntar.KnullEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;

/** Wires the origin system into the game: sync, attributes, XP from kills, bosses, biomes and dimensions, dodging, commands. */
public final class OriginSystem {
    private OriginSystem() {
    }

    public static void init() {
        Races.init();
        OriginNet.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> OriginCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> OriginApi.refresh(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> OriginApi.refresh(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                OriginEffects.tick(player);
                if (player.tickCount % 40 == 0) {
                    explore(player);
                }
            }
        });

        // DEX: dodge attacks.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.getEntity() != null && OriginEffects.dodges(player)));

        // XP for kills; bosses are worth a lot.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && killer != entity && OriginApi.get(killer) != null) {
                OriginApi.addXp(killer, killXp(entity), true);
            }
        });
    }

    /** Hostile creatures give twice their health in XP, peaceful ones a quarter of that; bosses have fixed rewards. */
    static int killXp(LivingEntity victim) {
        if (victim instanceof EnderDragon) {
            return 5000;
        }
        if (victim instanceof KnullEntity) {
            return 10000;
        }
        if (victim instanceof WitherBoss) {
            return 3000;
        }
        if (victim instanceof Warden) {
            return 2000;
        }
        int xp = Math.round(victim.getMaxHealth() * 2);
        if (!(victim instanceof Enemy)) {
            xp = victim instanceof Animal ? Math.max(1, xp / 4) : Math.max(1, xp / 8);
        }
        return Math.max(1, xp);
    }

    /** First visit to a biome (+25 XP) or a dimension (+200 XP). */
    private static void explore(ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null) {
            return;
        }
        String dimension = player.level().dimension().location().toString();
        if (sheet.dimensions.add(dimension)) {
            OriginData.get(player.server).setDirty();
            if (sheet.dimensions.size() > 1) { // the first dimension is simply where you start
                player.displayClientMessage(Component.literal("§eNew dimension: " + dimension), true);
                OriginApi.addXp(player, 200, true);
            }
        }
        var biome = player.level().getBiome(player.blockPosition()).unwrapKey();
        if (biome.isPresent() && sheet.biomes.add(biome.get().location().toString())) {
            OriginData.get(player.server).setDirty();
            if (sheet.biomes.size() > 1) {
                player.displayClientMessage(Component.literal("§eNew biome: " + biome.get().location().getPath().replace('_', ' ')), true);
                OriginApi.addXp(player, 25, true);
            }
        }
    }
}
