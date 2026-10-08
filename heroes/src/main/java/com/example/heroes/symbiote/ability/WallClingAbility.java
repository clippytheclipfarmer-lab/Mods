package com.example.heroes.symbiote.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Hold to stick to the wall you are pushing against; move forward to climb, otherwise you hang. Use with a 'held' condition. */
public class WallClingAbility extends Ability {
    public WallClingAbility() {
        this.withProperty(ICON, new ItemIcon(Items.COBWEB));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled) {
            return;
        }
        entity.fallDistance = 0;
        // Players steer their own movement on their client, like a vanilla ladder.
        boolean local = entity.level().isClientSide ? entity instanceof Player p && p.isLocalPlayer() : !(entity instanceof Player);
        if (local && entity.horizontalCollision) {
            Vec3 motion = entity.getDeltaMovement();
            double up = entity.zza > 0 ? 0.24 : 0.0;
            entity.setDeltaMovement(motion.x * 0.5, up, motion.z * 0.5);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Wall cling for symbiote hosts.";
    }
}
