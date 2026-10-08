package com.example.heroes.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The public hook for the character generator. Call {@link #assign} (or {@link #assignRandom}) when a character is
 * created; everything else (stats, attributes, XP, the K sheet) follows from that.
 */
public final class OriginApi {
    private OriginApi() {
    }

    /** The character of a player, or null when none has been assigned yet. */
    @Nullable
    public static Sheet get(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }
        return OriginData.get(player.server).sheet(player.getUUID());
    }

    /** Gives the player this race, a subtype rolled by weight, and freshly rolled ability scores (level 1, no XP). */
    public static Sheet assign(ServerPlayer player, Race race) {
        Sheet sheet = new Sheet();
        sheet.race = race.id;
        sheet.subtype = Races.randomSubtype(race, player.getRandom()).id;
        sheet.rollScores(player.getRandom());
        sheet.rollHeight(player.getRandom());
        OriginData.get(player.server).put(player.getUUID(), sheet);
        refresh(player);
        player.sendSystemMessage(Component.literal("You are " + sheet.subtypeDef().name + ", level 1."));
        return sheet;
    }

    /** Picks the race by weight as well. Returns null when no races are loaded. */
    @Nullable
    public static Sheet assignRandom(ServerPlayer player) {
        Race race = Races.randomRace(player.getRandom());
        return race == null ? null : assign(player, race);
    }

    /** Rolls the six ability scores again, keeping race, subtype, level and XP. */
    public static void rerollScores(ServerPlayer player) {
        Sheet sheet = get(player);
        if (sheet != null) {
            sheet.rollScores(player.getRandom());
            OriginData.get(player.server).setDirty();
            refresh(player);
        }
    }

    public static void clear(ServerPlayer player) {
        OriginData.get(player.server).remove(player.getUUID());
        OriginEffects.clear(player);
        OriginNet.sync(player);
    }

    public static boolean hasFlag(LivingEntity entity, String flag) {
        Sheet sheet = get(entity);
        return sheet != null && sheet.hasFlag(flag);
    }

    /** Adds character XP (affected by Intelligence); announces level-ups. */
    public static void addXp(ServerPlayer player, int amount, boolean scaleWithInt) {
        Sheet sheet = get(player);
        if (sheet == null || amount <= 0) {
            return;
        }
        if (scaleWithInt) {
            amount = Math.max(1, (int) Math.round(amount * (1.0 + 0.05 * sheet.mod(Ability5e.INT))));
        }
        int before = sheet.level();
        sheet.xp += amount;
        OriginData.get(player.server).setDirty();
        int after = sheet.level();
        if (after > before) {
            player.sendSystemMessage(Component.literal("§6Level up! You are now level " + after + "."));
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            OriginEffects.apply(player);
        }
        OriginNet.sync(player);
    }

    /** Re-applies attributes and tells the client. */
    public static void refresh(ServerPlayer player) {
        OriginEffects.apply(player);
        OriginNet.sync(player);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation("heroes", path);
    }
}
