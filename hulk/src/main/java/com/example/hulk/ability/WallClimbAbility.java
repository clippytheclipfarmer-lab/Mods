package com.example.hulk.ability;

import com.example.hulk.HulkRage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Climb any wall you are pressed against while the key is held. Use with a 'held' condition. */
public class WallClimbAbility extends Ability {
    public WallClimbAbility() {
        this.withProperty(ICON, new ItemIcon(Items.LADDER));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled) {
            return;
        }
        entity.fallDistance = 0;
        // Players steer their own movement on their client; the server follows their reported position.
        boolean local = entity.level().isClientSide ? entity instanceof Player p && p.isLocalPlayer() : !(entity instanceof Player);
        if (local && entity.horizontalCollision) {
            float rage = HulkRage.fraction(entity);
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, 0.3 + 0.15 * rage, motion.z);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Wall climbing. Hold to climb the wall you are pushing against.";
    }
}
