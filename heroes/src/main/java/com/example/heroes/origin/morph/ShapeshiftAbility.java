package com.example.heroes.origin.morph;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Press while looking at a creature to take its shape; press while looking at nothing to change back. Use with an 'action' condition. */
public class ShapeshiftAbility extends Ability {
    public ShapeshiftAbility() {
        this.withProperty(ICON, new ItemIcon(Items.ENDER_EYE));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            Morph.shift(player);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Skrull shapeshifting.";
    }
}
