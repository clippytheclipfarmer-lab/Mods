package com.example.heroes.origin;

/**
 * Character levels 1-20 with the 5e proficiency bonus. The XP thresholds are the 5e table divided by 5 so that a
 * Minecraft playthrough can reach the top.
 */
public final class Levels {
    public static final int MAX_LEVEL = 20;
    /** XP needed to reach level n is THRESHOLDS[n - 1]. */
    private static final int[] THRESHOLDS = {0, 60, 180, 540, 1300, 2800, 4600, 6800, 9600, 12800, 17000, 20000, 24000, 28000, 33000, 39000,
            45000, 53000, 61000, 71000};

    private Levels() {
    }

    public static int levelFor(int xp) {
        int level = 1;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (xp >= THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    public static int xpFor(int level) {
        return THRESHOLDS[Math.max(0, Math.min(MAX_LEVEL, level) - 1)];
    }

    /** XP needed for the next level, or -1 at the top. */
    public static int xpForNext(int level) {
        return level >= MAX_LEVEL ? -1 : THRESHOLDS[level];
    }

    /** 5e proficiency bonus: +2 at levels 1-4, rising by one every four levels. */
    public static int proficiency(int level) {
        return 2 + (Math.max(1, level) - 1) / 4;
    }
}
