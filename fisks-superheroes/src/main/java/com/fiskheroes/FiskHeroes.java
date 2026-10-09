package com.fiskheroes;

import com.fiskheroes.ability.EffectAbility;
import com.fiskheroes.ability.FreezeBreathAbility;
import com.fiskheroes.ability.GlideAbility;
import com.fiskheroes.ability.KineticAbilities;
import com.fiskheroes.ability.KryptoniteWeaknessAbility;
import com.fiskheroes.ability.LightningStrikeAbility;
import com.fiskheroes.ability.SmokeBombAbility;
import com.fiskheroes.ability.SpeedForceAbilities;
import com.fiskheroes.ability.SpiderSenseAbility;
import com.fiskheroes.ability.StormAbility;
import com.fiskheroes.ability.ThrowGadgetAbility;
import com.fiskheroes.ability.WallClimbAbility;
import com.fiskheroes.ability.WebShotAbility;
import com.fiskheroes.ability.ZipLineAbility;
import com.fiskheroes.effect.KryptoniteEffect;
import com.fiskheroes.entity.GadgetEntity;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladiumcore.registry.DeferredRegister;
import net.threetag.palladiumcore.registry.RegistrySupplier;

/**
 * Fisk's Superheroes for Fabric 1.20.1, as a Palladium addon: the suits, items and powers are Palladium JSON
 * (addon/fiskheroes and data/fiskheroes); this class only adds what JSON cannot do (the abilities, the thrown gadget
 * and the kryptonite sickness).
 */
public class FiskHeroes implements ModInitializer {
    public static final String MOD_ID = "fiskheroes";

    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MOD_ID, Ability.REGISTRY);
    public static final RegistrySupplier<Ability> KINETIC_ABSORB = ABILITIES.register("kinetic_absorb", KineticAbilities.Absorb::new);
    public static EntityType<GadgetEntity> GADGET;
    public static MobEffect KRYPTONITE_POISONING;

    static {
        ABILITIES.register("effect", EffectAbility::new);
        ABILITIES.register("throw_gadget", ThrowGadgetAbility::new);
        ABILITIES.register("lightning_strike", LightningStrikeAbility::new);
        ABILITIES.register("storm", StormAbility::new);
        ABILITIES.register("wall_climb", WallClimbAbility::new);
        ABILITIES.register("zip_line", ZipLineAbility::new);
        ABILITIES.register("web_shot", WebShotAbility::new);
        ABILITIES.register("spider_sense", SpiderSenseAbility::new);
        ABILITIES.register("kinetic_release", KineticAbilities.Release::new);
        ABILITIES.register("freeze_breath", FreezeBreathAbility::new);
        ABILITIES.register("smoke_bomb", SmokeBombAbility::new);
        ABILITIES.register("glide", GlideAbility::new);
        ABILITIES.register("speed_force", SpeedForceAbilities.SpeedForce::new);
        ABILITIES.register("speed_step", SpeedForceAbilities.SpeedStep::new);
        ABILITIES.register("kryptonite_weakness", KryptoniteWeaknessAbility::new);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        GADGET = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("gadget"),
                EntityType.Builder.<GadgetEntity>of(GadgetEntity::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(10).updateInterval(2).build("gadget"));
        KRYPTONITE_POISONING = Registry.register(BuiltInRegistries.MOB_EFFECT, id("kryptonite_poisoning"), new KryptoniteEffect());
        ABILITIES.register();
        FiskEvents.init();
    }
}
