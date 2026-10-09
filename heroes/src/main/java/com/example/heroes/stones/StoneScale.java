package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.api.TypedScaleModifier;

/** Pehkui scale type driven by the Reality Stone's size ability; stacks with other mods' scaling. */
public final class StoneScale {
    public static final ScaleType RESIZE_SCALE = ScaleRegistries.register(ScaleRegistries.SCALE_TYPES,
            HeroesMod.stones("resize"), ScaleType.Builder.create().affectsDimensions().build());
    public static final ScaleModifier RESIZE_MODIFIER = ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS,
            HeroesMod.stones("resize"), new TypedScaleModifier(() -> RESIZE_SCALE));

    private StoneScale() {
    }

    public static void init() {
        ScaleTypes.BASE.getDefaultBaseValueModifiers().add(RESIZE_MODIFIER);
    }

    public static void set(LivingEntity entity, float scale) {
        var data = RESIZE_SCALE.getScaleData(entity);
        if (data.getTargetScale() != scale) {
            data.setTargetScale(scale);
        }
    }
}
