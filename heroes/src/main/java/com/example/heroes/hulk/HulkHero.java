package com.example.heroes.hulk;

import com.example.heroes.HeroesMod;
import com.example.heroes.hulk.ability.ChargeAbility;
import com.example.heroes.hulk.ability.GammaNukeAbility;
import com.example.heroes.hulk.ability.GrabThrowAbility;
import com.example.heroes.hulk.ability.GroundSmashAbility;
import com.example.heroes.hulk.ability.ImmortalityAbility;
import com.example.heroes.hulk.ability.RageScalingAbility;
import com.example.heroes.hulk.ability.StompQuakeAbility;
import com.example.heroes.hulk.ability.SuperJumpAbility;
import com.example.heroes.hulk.ability.ThunderclapAbility;
import com.example.heroes.hulk.ability.WallClimbAbility;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladiumcore.event.EventResult;
import net.threetag.palladiumcore.event.LivingEntityEvents;
import net.threetag.palladiumcore.registry.DeferredRegister;
import net.threetag.palladiumcore.registry.RegistrySupplier;

/** Hulk: abilities live in the {@code hulk} namespace, the power is {@code hulk:hulk}. */
public final class HulkHero {
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(HeroesMod.HULK, Ability.REGISTRY);

    public static final RegistrySupplier<Ability> IMMORTALITY;

    static {
        ABILITIES.register("rage_scaling", RageScalingAbility::new);
        ABILITIES.register("ground_smash", GroundSmashAbility::new);
        ABILITIES.register("super_jump", SuperJumpAbility::new);
        ABILITIES.register("thunderclap", ThunderclapAbility::new);
        ABILITIES.register("stomp_quake", StompQuakeAbility::new);
        ABILITIES.register("grab_throw", GrabThrowAbility::new);
        ABILITIES.register("charge", ChargeAbility::new);
        ABILITIES.register("wall_climb", WallClimbAbility::new);
        ABILITIES.register("gamma_nuke", GammaNukeAbility::new);
        IMMORTALITY = ABILITIES.register("immortality", ImmortalityAbility::new);
    }

    private HulkHero() {
    }

    public static void init() {
        ABILITIES.register();
        HulkScale.init();
        GrabSystem.init();
        NukeSystem.init();

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Hulk shrugs off falls (needed for the super jump).
            return !(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && HulkRage.isHulk(player));
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }
            for (var entry : AbilityUtil.getEnabledEntries(player, IMMORTALITY.get())) {
                if (ImmortalityAbility.tryRevive(player, entry)) {
                    return false;
                }
            }
            return true;
        });

        LivingEntityEvents.HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide) {
                if (entity instanceof ServerPlayer victim && HulkRage.isHulk(victim)) {
                    HulkRage.add(victim, 5 + Math.round(amount.get() * 2));
                }
                if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && HulkRage.isHulk(attacker)) {
                    HulkRage.add(attacker, 3);
                }
            }
            return EventResult.pass();
        });
    }
}
