package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Always-on fast healing: a slow trickle normally, twice as fast and four times as strong while the suit is on. */
public class RegenAbility extends Ability {
    public RegenAbility() {
        this.withProperty(ICON, new ItemIcon(Items.GLISTERING_MELON_SLICE));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.level().isClientSide || !entity.isAlive() || entity.getHealth() >= entity.getMaxHealth()) {
            return;
        }
        boolean suited = SymbioteHost.isSuited(entity);
        if (entity.tickCount % (suited ? 20 : 40) == 0) {
            entity.heal(suited ? 2.0F : 0.5F);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Fast healing for symbiote hosts.";
    }
}
