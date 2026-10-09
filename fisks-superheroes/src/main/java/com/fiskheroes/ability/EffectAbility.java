package com.fiskheroes.ability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;

/** Keeps a status effect on the holder (quietly) for as long as the ability is enabled. */
public class EffectAbility extends Ability {
    public static final PalladiumProperty<String> EFFECT = new StringProperty("effect").configurable("ID of the status effect to keep applied");
    public static final PalladiumProperty<Integer> AMPLIFIER = new IntegerProperty("amplifier").configurable("Amplifier of the effect (0 = level I)");

    public EffectAbility() {
        this.withProperty(ICON, new ItemIcon(Items.POTION));
        this.withProperty(EFFECT, "minecraft:night_vision");
        this.withProperty(AMPLIFIER, 0);
    }

    private static MobEffect effect(AbilityInstance entry) {
        return BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(entry.getProperty(EFFECT)));
    }

    private static void apply(LivingEntity entity, AbilityInstance entry) {
        MobEffect effect = effect(entry);
        if (effect != null) {
            entity.addEffect(new MobEffectInstance(effect, 400, entry.getProperty(AMPLIFIER), true, false, false));
        }
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && !entity.level().isClientSide) {
            apply(entity, entry);
        }
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && !entity.level().isClientSide && entity.tickCount % 100 == 0) {
            apply(entity, entry);
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (entity.level().isClientSide) {
            return;
        }
        MobEffect effect = effect(entry);
        MobEffectInstance current = effect == null ? null : entity.getEffect(effect);
        if (current != null && current.isAmbient() && current.getAmplifier() == entry.getProperty(AMPLIFIER)) {
            entity.removeEffect(effect);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Keeps a status effect (night vision, ...) on the holder while enabled.";
    }
}
