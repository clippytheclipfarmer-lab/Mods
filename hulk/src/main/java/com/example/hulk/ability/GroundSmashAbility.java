package com.example.hulk.ability;

import com.example.hulk.HulkEffects;
import com.example.hulk.HulkRage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Slams the ground: damage, knockback and weak-block breaking, scaled by rage. */
public class GroundSmashAbility extends Ability {
    public GroundSmashAbility() {
        this.withProperty(ICON, new ItemIcon(Items.ANVIL));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        float rage = HulkRage.fraction(entity);
        double radius = 4 + 4 * rage;
        Vec3 origin = entity.position();
        HulkEffects.blast(level, entity, origin, radius, 6 + 10 * rage, 1.0 + 1.0 * rage, 0.5 + 0.4 * rage);
        HulkEffects.breakWeakBlocks(level, entity, entity.blockPosition().below(), (int) radius, 1.0F + 2.0F * rage);
        HulkEffects.ring(level, origin, radius);
        HulkEffects.boom(level, origin, 0.8F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Ground slam shockwave. Use with an 'action' condition.";
    }
}
