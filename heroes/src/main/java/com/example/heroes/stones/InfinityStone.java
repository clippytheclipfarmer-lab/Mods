package com.example.heroes.stones;

import java.util.Locale;

/** The six stones. Powers and boosts come later; for now they are the objects of the hunt. */
public enum InfinityStone {
    SPACE(0x2f74ff, "minecraft:blue_concrete"),
    MIND(0xf5d442, "minecraft:yellow_concrete"),
    REALITY(0xe03030, "minecraft:red_concrete"),
    POWER(0x9b3fe0, "minecraft:purple_concrete"),
    TIME(0x3ccf5a, "minecraft:lime_concrete"),
    SOUL(0xff8a1f, "minecraft:orange_concrete");

    public final int color;
    /** Block used to decorate this stone's shrine. */
    public final String shrineBlock;

    InfinityStone(int color, String shrineBlock) {
        this.color = color;
        this.shrineBlock = shrineBlock;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_stone";
    }

    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT) + " Stone";
    }

    public static InfinityStone byName(String name) {
        for (InfinityStone s : values()) {
            if (s.name().equalsIgnoreCase(name) || s.id().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
