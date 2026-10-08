package com.example.heroes.origin;

/** The six D&amp;D 5e ability scores. */
public enum Ability5e {
    STR("str", "Strength"), DEX("dex", "Dexterity"), CON("con", "Constitution"),
    INT("int", "Intelligence"), WIS("wis", "Wisdom"), CHA("cha", "Charisma");

    public final String key;
    public final String label;

    Ability5e(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** The 5e modifier of a score: (score - 10) / 2, rounded down. */
    public static int modifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }
}
