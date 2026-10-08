package com.example.heroes.origin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.api.TypedScaleModifier;

/** Pehkui scale type for a character's race and height; stacks with other scaling such as the Hulk's rage. */
public final class OriginScale {
    /** A player is 1.8 blocks tall. */
    public static final float PLAYER_HEIGHT = 1.8F;
    public static final ScaleType RACE_SCALE = ScaleRegistries.register(ScaleRegistries.SCALE_TYPES,
            new ResourceLocation("heroes", "race"), ScaleType.Builder.create().affectsDimensions().build());
    public static final ScaleModifier RACE_MODIFIER = ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS,
            new ResourceLocation("heroes", "race"), new TypedScaleModifier(() -> RACE_SCALE));

    private OriginScale() {
    }

    public static void init() {
        ScaleTypes.BASE.getDefaultBaseValueModifiers().add(RACE_MODIFIER);
    }

    /** Sets the entity's size so that it is this many blocks tall (0 or less = normal size). */
    public static void setHeight(LivingEntity entity, float blocks) {
        float scale = blocks <= 0 ? 1.0F : blocks / PLAYER_HEIGHT;
        var data = RACE_SCALE.getScaleData(entity);
        if (Math.abs(data.getTargetScale() - scale) > 1.0E-3F) {
            data.setTargetScale(scale);
        }
    }
}
