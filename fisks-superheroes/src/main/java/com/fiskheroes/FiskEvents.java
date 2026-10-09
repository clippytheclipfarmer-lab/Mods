package com.fiskheroes;

import com.fiskheroes.ability.KineticAbilities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladium.power.energybar.EnergyBar;
import org.joml.Vector3f;

import java.util.Collection;

/** World-side rules: the Black Panther suit soaking up hits, and the task queue. */
final class FiskEvents {
    private FiskEvents() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true;
            }
            return !absorb(entity, amount);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> FiskUtil.tickTasks());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FiskUtil.clearTasks());
    }

    /** Returns true if the hit was soaked up (and so must not hurt). */
    private static boolean absorb(LivingEntity entity, float amount) {
        Collection<AbilityInstance> entries = AbilityUtil.getEnabledEntries(entity, FiskHeroes.KINETIC_ABSORB.get());
        if (entries.isEmpty()) {
            return false;
        }
        AbilityInstance entry = entries.iterator().next();
        EnergyBar bar = entry.getHolder().getEnergyBars().get(entry.getProperty(KineticAbilities.Absorb.BAR));
        if (bar == null) {
            return false;
        }
        int gain = Math.round(amount * entry.getProperty(KineticAbilities.Absorb.EFFICIENCY));
        if (bar.get() + gain > bar.getMax()) {
            return false; // the suit is full: the hit goes through
        }
        bar.add(gain);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0.71F, 0.48F, 1.0F), 1.3F), entity.getX(), entity.getY() + 1, entity.getZ(), 14, 0.4, 0.7, 0.4, 0);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        return true;
    }
}
