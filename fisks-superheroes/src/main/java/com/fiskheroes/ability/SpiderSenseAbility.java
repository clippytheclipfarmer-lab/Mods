package com.fiskheroes.ability;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

import java.util.List;

/** Danger sense: hostile creatures that are after you glow when they get close, and a tingle warns you. */
public class SpiderSenseAbility extends Ability {
    public static final PalladiumProperty<Float> RADIUS = new FloatProperty("radius").configurable("Detection radius in blocks");

    public SpiderSenseAbility() {
        this.withProperty(ICON, new ItemIcon(Items.SPIDER_EYE));
        this.withProperty(RADIUS, 16F);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.level().isClientSide || entity.tickCount % 10 != 0) {
            return;
        }
        double radius = entry.getProperty(RADIUS);
        List<Mob> threats = entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius),
                m -> m instanceof Enemy && m.isAlive() && (m.getTarget() == entity || m.distanceToSqr(entity) < 36));
        for (Mob threat : threats) {
            threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
        }
        if (!threats.isEmpty() && entity.tickCount % 60 == 0) {
            entity.level().playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 2.0F);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Highlights nearby threats.";
    }
}
