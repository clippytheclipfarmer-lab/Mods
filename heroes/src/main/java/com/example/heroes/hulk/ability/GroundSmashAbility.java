package com.example.heroes.hulk.ability;

import com.example.heroes.common.HeroEffects;
import com.example.heroes.hulk.HulkRage;
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
        float tier = HulkRage.tier(rage).multiplier;
        double radius = (4 + 4 * rage) * tier;
        Vec3 origin = entity.position();
        HeroEffects.blast(level, entity, origin, radius, (6 + 10 * rage) * tier, 1.0 + 1.0 * rage, 0.5 + 0.4 * rage);
        HeroEffects.breakWeakBlocks(level, entity, entity.blockPosition().below(), (int) radius, 1.0F + 2.0F * rage);
        HeroEffects.ring(level, origin, radius);
        HeroEffects.boom(level, origin, 0.8F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Ground slam shockwave. Use with an 'action' condition.";
    }
}
