package com.example.heroes.hulk;

import com.example.heroes.HeroesMod;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.api.TypedScaleModifier;

/** Pehkui scale type driven by the rage ability; stacks with other mods' scaling. */
public final class HulkScale {
    public static final ScaleType RAGE_SCALE = ScaleRegistries.register(ScaleRegistries.SCALE_TYPES,
            HeroesMod.hulk("rage"), ScaleType.Builder.create().affectsDimensions().build());
    public static final ScaleModifier RAGE_MODIFIER = ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS,
            HeroesMod.hulk("rage"), new TypedScaleModifier(() -> RAGE_SCALE));

    private HulkScale() {
    }

    public static void init() {
        ScaleTypes.BASE.getDefaultBaseValueModifiers().add(RAGE_MODIFIER);
    }

    public static void set(LivingEntity entity, float scale) {
        var data = RAGE_SCALE.getScaleData(entity);
        if (data.getTargetScale() != scale) {
            data.setTargetScale(scale);
        }
    }
}
