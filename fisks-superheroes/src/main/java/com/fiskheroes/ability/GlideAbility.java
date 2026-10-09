package com.fiskheroes.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Spread the cape and glide: hold the key in the air to sink slowly and drift forward. Use with a 'held' condition. */
public class GlideAbility extends Ability {
    public GlideAbility() {
        this.withProperty(ICON, new ItemIcon(Items.PHANTOM_MEMBRANE));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.onGround() || entity.isInWater()) {
            return;
        }
        entity.fallDistance = 0;
        boolean local = entity.level().isClientSide ? entity instanceof Player p && p.isLocalPlayer() : !(entity instanceof Player);
        if (!local) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        Vec3 look = entity.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double push = horizontal < 0.75 ? 0.045 : 0.0;
        // Looking down dives, looking up flares.
        double dy = Math.max(motion.y, -0.09 + look.y * 0.12);
        entity.setDeltaMovement(motion.x * 0.99 + flat.x * push, dy, motion.z * 0.99 + flat.z * push);
    }

    @Override
    public String getDocumentationDescription() {
        return "Glide on a cape while the key is held in the air.";
    }
}
