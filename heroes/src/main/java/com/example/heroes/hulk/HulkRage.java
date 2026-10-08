package com.example.heroes.hulk;

import com.example.heroes.HeroesMod;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.PowerManager;
import net.threetag.palladium.power.energybar.EnergyBar;

import java.util.Optional;

/** Access to the Hulk power's rage energy bar (0-100). */
public final class HulkRage {
    public static final String BAR = "rage";

    private HulkRage() {
    }

    public static Optional<IPowerHolder> holder(LivingEntity entity) {
        return PowerManager.getPowerHandler(entity)
                .map(handler -> handler.getPowerHolders().get(HeroesMod.hulk("hulk")));
    }

    public static boolean isHulk(LivingEntity entity) {
        return holder(entity).isPresent();
    }

    private static EnergyBar bar(LivingEntity entity) {
        return holder(entity).map(h -> h.getEnergyBars().get(BAR)).orElse(null);
    }

    public static void add(LivingEntity entity, int amount) {
        EnergyBar bar = bar(entity);
        if (bar != null) {
            bar.add(amount);
        }
    }

    /** Rage as a 0..1 fraction. */
    public static float fraction(LivingEntity entity) {
        EnergyBar bar = bar(entity);
        return bar == null || bar.getMax() <= 0 ? 0F : bar.get() / (float) bar.getMax();
    }

    /** Comic-inspired power tiers, driven by rage. */
    public enum Tier {
        SAVAGE(1.0F), WORLDBREAKER(1.6F), TITAN(2.2F);

        /** Multiplier applied to the radius/damage of Hulk's powers. */
        public final float multiplier;

        Tier(float multiplier) {
            this.multiplier = multiplier;
        }
    }

    public static Tier tier(LivingEntity entity) {
        return tier(fraction(entity));
    }

    public static Tier tier(float rage) {
        if (rage >= 0.95F) {
            return Tier.TITAN;
        }
        return rage >= 0.6F ? Tier.WORLDBREAKER : Tier.SAVAGE;
    }
}
