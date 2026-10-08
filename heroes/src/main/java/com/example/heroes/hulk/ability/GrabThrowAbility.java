package com.example.heroes.hulk.ability;

import com.example.heroes.hulk.GrabSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Press to grab a mob, player or block in front of you; press again to throw it. Use with an 'action' condition. */
public class GrabThrowAbility extends Ability {
    public GrabThrowAbility() {
        this.withProperty(ICON, new ItemIcon(Items.IRON_BLOCK));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && !entity.level().isClientSide) {
            GrabSystem.use(entity);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Grab and throw mobs, players and blocks. Use with an 'action' condition.";
    }
}
