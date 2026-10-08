package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Wearing the suit burns the symbiote's food: about 3 minutes of suit time empties its hunger bar, so it cannot be worn forever. */
public class SuitUpkeepAbility extends Ability {
    public SuitUpkeepAbility() {
        this.withProperty(ICON, new ItemIcon(Items.BONE_MEAL));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && !entity.level().isClientSide && entity.tickCount % 40 == 0 && SymbioteHost.isSuited(entity)) {
            SymbioteHost.feed(entity, -1);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "The suit burns the symbiote's food.";
    }
}
