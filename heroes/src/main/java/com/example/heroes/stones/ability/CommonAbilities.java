package com.example.heroes.stones.ability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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

import java.util.ArrayList;

/** Passive building blocks shared by several stones. */
public final class CommonAbilities {
    private CommonAbilities() {
    }

    /** Keeps a status effect on the holder (quietly, no particles) for as long as the ability is enabled. */
    public static class Effect extends Ability {
        public static final PalladiumProperty<String> EFFECT = new StringProperty("effect").configurable("ID of the status effect to keep applied");
        public static final PalladiumProperty<Integer> AMPLIFIER = new IntegerProperty("amplifier").configurable("Amplifier of the effect (0 = level I)");

        public Effect() {
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
                // Long enough that night vision never flickers; ambient and without particles so it stays unobtrusive.
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
            return "Keeps a status effect (night vision, hero of the village, saturation, ...) on the holder while enabled.";
        }
    }

    /** Wipes harmful status effects from the holder. */
    public static class Cleanse extends Ability {
        public Cleanse() {
            this.withProperty(ICON, new ItemIcon(Items.MILK_BUCKET));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || entity.level().isClientSide || entity.tickCount % 10 != 0) {
                return;
            }
            for (MobEffectInstance instance : new ArrayList<>(entity.getActiveEffects())) {
                if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    entity.removeEffect(instance.getEffect());
                }
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Removes harmful status effects from the holder.";
        }
    }
}
