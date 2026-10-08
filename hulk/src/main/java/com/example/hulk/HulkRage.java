package com.example.hulk;

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
                .map(handler -> handler.getPowerHolders().get(HulkMod.id("hulk")));
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
}
