package com.example.hulk;

import com.example.hulk.ability.GroundSmashAbility;
import com.example.hulk.ability.RageScalingAbility;
import com.example.hulk.ability.SuperJumpAbility;
import com.example.hulk.ability.ThunderclapAbility;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladiumcore.event.EventResult;
import net.threetag.palladiumcore.event.LivingEntityEvents;
import net.threetag.palladiumcore.registry.DeferredRegister;

public class HulkMod implements ModInitializer {
    public static final String MOD_ID = "hulk";

    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MOD_ID, Ability.REGISTRY);

    static {
        ABILITIES.register("rage_scaling", RageScalingAbility::new);
        ABILITIES.register("ground_smash", GroundSmashAbility::new);
        ABILITIES.register("super_jump", SuperJumpAbility::new);
        ABILITIES.register("thunderclap", ThunderclapAbility::new);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ABILITIES.register();
        HulkScale.init();

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Hulk shrugs off falls (needed for the super jump).
            return !(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && HulkRage.isHulk(player));
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
