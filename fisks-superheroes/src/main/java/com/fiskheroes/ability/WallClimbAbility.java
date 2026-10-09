package com.fiskheroes.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.BooleanProperty;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

/** Climb any wall you are pushing against. Use with a 'held' condition (Spider-Man) or without one plus sprinting (the Flash). */
public class WallClimbAbility extends Ability {
    public static final PalladiumProperty<Float> SPEED = new FloatProperty("speed").configurable("Upward speed in blocks per tick");
    public static final PalladiumProperty<Boolean> NEEDS_SPRINT = new BooleanProperty("needs_sprint").configurable("If true you must be sprinting into the wall (wall running)");

    public WallClimbAbility() {
        this.withProperty(ICON, new ItemIcon(Items.LADDER));
        this.withProperty(SPEED, 0.28F);
        this.withProperty(NEEDS_SPRINT, false);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled) {
            return;
        }
        // Players steer their own movement on their client; the server follows their reported position.
        boolean local = entity.level().isClientSide ? entity instanceof Player p && p.isLocalPlayer() : !(entity instanceof Player);
        if (!local || !entity.horizontalCollision || (entry.getProperty(NEEDS_SPRINT) && !entity.isSprinting())) {
            return;
        }
        entity.fallDistance = 0;
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * 0.5, entity.isShiftKeyDown() ? 0.0 : entry.getProperty(SPEED), motion.z * 0.5);
    }

    @Override
    public String getDocumentationDescription() {
        return "Climb the wall you are pushing against (sneak to hold still).";
    }
}
