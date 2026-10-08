package com.example.heroes.viltrumite;

import com.example.heroes.HeroesMod;
import com.example.heroes.viltrumite.ability.SonicBoomAbility;
import com.example.heroes.viltrumite.ability.SpaceSurvivalAbility;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.PowerManager;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladiumcore.registry.DeferredRegister;
import net.threetag.palladiumcore.registry.RegistrySupplier;

/**
 * Viltrumite (Invincible): flight, super strength and durability, space survival.
 * Mostly Palladium JSON ({@code data/viltrumite/palladium/powers/viltrumite.json}); Java only for the extras.
 */
public final class ViltrumiteHero {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(HeroesMod.VILTRUMITE, Ability.REGISTRY);

    public static final RegistrySupplier<Ability> SPACE_SURVIVAL = ABILITIES.register("space_survival", SpaceSurvivalAbility::new);
    public static final RegistrySupplier<Ability> SONIC_BOOM = ABILITIES.register("sonic_boom", SonicBoomAbility::new);

    private ViltrumiteHero() {
    }

    public static boolean isViltrumite(LivingEntity entity) {
        return PowerManager.getPowerHandler(entity)
                .map(handler -> handler.getPowerHolders().containsKey(HeroesMod.viltrumite("viltrumite"))).orElse(false);
    }

    public static void init() {
        ABILITIES.register();

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Space survival and flight-speed durability: these never hurt a Viltrumite.
            boolean immune = source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FREEZE)
                    || source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL);
            return !(immune && isViltrumite(entity) && !AbilityUtil.getEnabledEntries(entity, SPACE_SURVIVAL.get()).isEmpty());
        });
    }
}
