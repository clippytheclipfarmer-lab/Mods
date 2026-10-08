package com.example.heroes.origin;

import net.minecraft.util.RandomSource;

public final class Dice {
    private Dice() {
    }

    public static int d(RandomSource random, int sides) {
        return random.nextInt(sides) + 1;
    }

    /** 4d6, drop the lowest die: the classic 5e way to roll an ability score. */
    public static int fourD6DropLowest(RandomSource random) {
        int a = d(random, 6), b = d(random, 6), c = d(random, 6), e = d(random, 6);
        return a + b + c + e - Math.min(Math.min(a, b), Math.min(c, e));
    }
}
