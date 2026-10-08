package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Always-on healing for the host (stronger in the suit), and slow mending of the symbiote's armor and life. */
public class RegenAbility extends Ability {
    public RegenAbility() {
        this.withProperty(ICON, new ItemIcon(Items.GLISTERING_MELON_SLICE));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.level().isClientSide || !entity.isAlive()) {
            return;
        }
        boolean suited = SymbioteHost.isSuited(entity);
        if (entity.getHealth() < entity.getMaxHealth() && entity.tickCount % (suited ? 20 : 40) == 0) {
            entity.heal(suited ? 2.0F : 0.5F);
        }
        if (entity.tickCount % 20 == 0) {
            // The symbiote mends too: its armor once the host has been left alone for 5 s, its own life slowly (0.1 heart per second).
            var armor = SymbioteHost.bar(entity, SymbioteHost.ARMOR);
            if (armor != null && !SymbioteHost.recentlyHit(entity, 100) && armor.get() < SymbioteHost.ARMOR_MAX) {
                armor.add(suited ? 50 : 30);
            }
            var core = SymbioteHost.bar(entity, SymbioteHost.CORE);
            if (core != null && core.get() > 0 && core.get() < SymbioteHost.CORE_MAX) {
                core.add(1);
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Fast healing for symbiote hosts.";
    }
}
