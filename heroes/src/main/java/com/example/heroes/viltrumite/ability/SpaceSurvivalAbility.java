package com.example.heroes.viltrumite.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Passive: never runs out of air; drowning, suffocation, freezing and impact damage are cancelled (see ViltrumiteHero). */
public class SpaceSurvivalAbility extends Ability {
    public SpaceSurvivalAbility() {
        this.withProperty(ICON, new ItemIcon(Items.HEART_OF_THE_SEA));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && !entity.level().isClientSide) {
            entity.setAirSupply(entity.getMaxAirSupply());
            if (entity.getTicksFrozen() > 0) {
                entity.setTicksFrozen(0);
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Viltrumite space survival: infinite air, immune to drowning, suffocation, freezing and impact damage.";
    }
}
