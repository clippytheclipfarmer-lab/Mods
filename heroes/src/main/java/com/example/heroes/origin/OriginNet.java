package com.example.heroes.origin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Sends everything the client needs to draw the character sheet (the client has no race data of its own). */
public final class OriginNet {
    public static final ResourceLocation SHEET = new ResourceLocation("heroes", "sheet");

    private OriginNet() {
    }

    public static void sync(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(describe(player));
        ServerPlayNetworking.send(player, SHEET, buf);
    }

    /** An empty tag means "no character yet". */
    private static CompoundTag describe(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        Sheet sheet = OriginApi.get(player);
        if (sheet == null || sheet.raceDef() == null) {
            return tag;
        }
        Race race = sheet.raceDef();
        Race.Subtype subtype = sheet.subtypeDef();
        tag.putString("Race", race.name);
        tag.putString("Subtype", subtype.name);
        tag.putString("Description", race.description);
        int level = sheet.level();
        tag.putInt("Level", level);
        tag.putInt("Xp", sheet.xp);
        tag.putInt("XpThis", Levels.xpFor(level));
        tag.putInt("XpNext", Levels.xpForNext(level));
        tag.putInt("Proficiency", Levels.proficiency(level));
        int[] scores = new int[6], rolled = sheet.rolled.clone();
        for (Ability5e a : Ability5e.values()) {
            scores[a.ordinal()] = sheet.score(a);
        }
        tag.putIntArray("Scores", scores);
        tag.putIntArray("Rolled", rolled);
        ListTag notes = new ListTag();
        if (sheet.trait("hunger_multiplier") > 0 && sheet.trait("hunger_multiplier") < 1) {
            notes.add(StringTag.valueOf("Slow to tire: hunger drains " + Math.round((1 - sheet.trait("hunger_multiplier")) * 100) + "% slower"));
        }
        if (sheet.trait("regen_hp_per_10s") > 0) {
            notes.add(StringTag.valueOf("Quick healing: +" + sheet.trait("regen_hp_per_10s") + " HP every 10 s"));
        }
        if (sheet.hasFlag("magical_potential")) {
            notes.add(StringTag.valueOf("Born with magical potential"));
        }
        if (sheet.hasFlag("warrior")) {
            notes.add(StringTag.valueOf("A trained warrior"));
        }
        tag.put("Notes", notes);
        tag.putFloat("Health", player.getHealth());
        tag.putFloat("MaxHealth", player.getMaxHealth());
        tag.putInt("Armor", player.getArmorValue());
        return tag;
    }

    public static void init() {
    }
}
