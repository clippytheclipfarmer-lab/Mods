package com.example.heroes.origin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Set;

/** A player's character: race, subtype, rolled ability scores, level and XP. */
public final class Sheet {
    public ResourceLocation race;
    public String subtype = "common";
    /** "male" or "female": picks which model the race wears. */
    public String gender = "male";
    /** The rolled scores (4d6, drop the lowest), before race bonuses. */
    public final int[] rolled = new int[6];
    public int xp;
    /** Shapeshifting: "" (none), "model" (a race model power), "skin" (another player's skin) or "mob" (an entity type). */
    public String morphKind = "";
    public String morphTarget = "";
    public float morphHeight;
    /** This character's height in blocks (a player is 1.8); 0 = normal size. */
    public float height;
    public final Set<String> biomes = new HashSet<>();
    public final Set<String> dimensions = new HashSet<>();

    public int level() {
        return Levels.levelFor(xp);
    }

    public Race raceDef() {
        return Races.get(race);
    }

    public Race.Subtype subtypeDef() {
        Race r = raceDef();
        return r == null ? null : r.subtype(subtype);
    }

    /** Rolled score plus race and subtype bonuses (capped at 20, as in 5e). */
    public int score(Ability5e ability) {
        int total = rolled[ability.ordinal()];
        Race r = raceDef();
        if (r != null) {
            total += r.bonuses[ability.ordinal()] + r.subtype(subtype).bonuses[ability.ordinal()];
        }
        return Math.min(20, total);
    }

    public int mod(Ability5e ability) {
        return Ability5e.modifier(score(ability));
    }

    public boolean hasFlag(String flag) {
        Race r = raceDef();
        Race.Subtype s = subtypeDef();
        return (r != null && r.flags.contains(flag)) || (s != null && s.flags.contains(flag));
    }

    /** A trait value summed over the race and the subtype (0 when absent). */
    public double trait(String name) {
        Race r = raceDef();
        if (r == null) {
            return 0;
        }
        return r.traits.getOrDefault(name, 0.0) + r.subtype(subtype).traits.getOrDefault(name, 0.0);
    }

    /** Rolls a height within the race's range: the average of two uniform rolls, so most are near the middle of the range. */
    public void rollHeight(RandomSource random) {
        Race r = raceDef();
        if (r == null || r.heightMax <= 0) {
            height = 0;
            return;
        }
        double t = (random.nextDouble() + random.nextDouble()) / 2.0;
        height = (float) (r.heightMin + (r.heightMax - r.heightMin) * t);
    }

    public void rollScores(RandomSource random) {
        for (int i = 0; i < 6; i++) {
            rolled[i] = Dice.fourD6DropLowest(random);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Race", race.toString());
        tag.putString("Subtype", subtype);
        tag.putString("Gender", gender);
        tag.putIntArray("Rolled", rolled);
        tag.putInt("Xp", xp);
        tag.putString("MorphKind", morphKind);
        tag.putString("MorphTarget", morphTarget);
        tag.putFloat("MorphHeight", morphHeight);
        tag.putFloat("Height", height);
        ListTag b = new ListTag();
        biomes.forEach(s -> b.add(StringTag.valueOf(s)));
        tag.put("Biomes", b);
        ListTag d = new ListTag();
        dimensions.forEach(s -> d.add(StringTag.valueOf(s)));
        tag.put("Dimensions", d);
        return tag;
    }

    public static Sheet load(CompoundTag tag) {
        Sheet s = new Sheet();
        s.race = new ResourceLocation(tag.getString("Race"));
        s.subtype = tag.getString("Subtype");
        s.gender = tag.contains("Gender") ? tag.getString("Gender") : "male";
        int[] r = tag.getIntArray("Rolled");
        System.arraycopy(r, 0, s.rolled, 0, Math.min(6, r.length));
        s.xp = tag.getInt("Xp");
        s.morphKind = tag.getString("MorphKind");
        s.morphTarget = tag.getString("MorphTarget");
        s.morphHeight = tag.getFloat("MorphHeight");
        s.height = tag.getFloat("Height");
        for (Tag t : tag.getList("Biomes", Tag.TAG_STRING)) {
            s.biomes.add(t.getAsString());
        }
        for (Tag t : tag.getList("Dimensions", Tag.TAG_STRING)) {
            s.dimensions.add(t.getAsString());
        }
        return s;
    }
}
