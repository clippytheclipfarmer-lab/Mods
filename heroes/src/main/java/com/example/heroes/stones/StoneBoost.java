package com.example.heroes.stones;

import net.minecraft.world.entity.LivingEntity;

/**
 * What the stones do to the other heroes' powers. Each stone boosts the abilities it fits; wearing several
 * stones also gives a growing general bonus (+10% per stone, doubled at the full set).
 */
public final class StoneBoost {
    private StoneBoost() {
    }

    public static boolean has(LivingEntity entity, InfinityStone stone) {
        return StoneWear.active(entity).contains(stone);
    }

    /** {@code factor} if the entity wears the stone, otherwise 1. */
    public static double mult(LivingEntity entity, InfinityStone stone, double factor) {
        return has(entity, stone) ? factor : 1.0;
    }

    /** The general bonus for the number of stones worn (1.0 with none, 2.0 with all six). */
    public static double general(LivingEntity entity) {
        int n = StoneWear.active(entity).size();
        return n >= InfinityStone.values().length ? 2.0 : 1.0 + 0.1 * n;
    }
}
