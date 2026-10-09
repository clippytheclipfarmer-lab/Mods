package com.fiskheroes.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Sickness from kryptonite: slows, weakens and slowly hurts. Superman's powers switch off while it lasts (see superman.json). */
public class KryptoniteEffect extends MobEffect {
    public KryptoniteEffect() {
        super(MobEffectCategory.HARMFUL, 0x3FDC5A);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "6c0f6d64-2d1a-4b5f-9a8e-0c5f4f1d7a01", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE, "6c0f6d64-2d1a-4b5f-9a8e-0c5f4f1d7a02", -0.7, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() > 2.0F) {
            entity.hurt(entity.damageSources().magic(), 1.0F);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }
}
