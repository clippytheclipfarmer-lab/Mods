package com.example.hulk.ability;

import com.example.hulk.HulkEffects;
import com.example.hulk.HulkRage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Hold Space to charge (up to 3 s), release to take off; lands with a small shockwave. Use with a 'held' condition. */
public class SuperJumpAbility extends Ability {
    private static final int MAX_CHARGE = 60;
    private static final int MIN_CHARGE = 5;
    /** Entities currently airborne from a super jump -> ticks since launch. */
    private static final Map<UUID, Integer> AIRBORNE = new ConcurrentHashMap<>();

    public SuperJumpAbility() {
        this.withProperty(ICON, new ItemIcon(Items.RABBIT_FOOT));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (entity.level().isClientSide) {
            // While Space is held he crouches and gathers power instead of doing the vanilla hop.
            if (enabled && entity instanceof Player player && player.isLocalPlayer() && entity.onGround()) {
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, Math.min(motion.y, 0), motion.z);
            }
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (enabled) {
            int charge = Math.min(entry.getEnabledTicks(), MAX_CHARGE);
            if (charge > 2 && entity.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        1 + charge / 10, 0.5, 0.05, 0.5, 0.05);
            }
        }
        Integer ticks = AIRBORNE.get(entity.getUUID());
        if (ticks != null) {
            if (ticks > 3 && entity.onGround()) {
                AIRBORNE.remove(entity.getUUID());
                float rage = HulkRage.fraction(entity);
                Vec3 origin = entity.position();
                double radius = 3 + 3 * rage;
                HulkEffects.blast(level, entity, origin, radius, 4 + 6 * rage, 0.8, 0.4);
                HulkEffects.ring(level, origin, radius);
                HulkEffects.boom(level, origin, 1.0F);
            } else {
                AIRBORNE.put(entity.getUUID(), ticks + 1);
            }
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (entity.level().isClientSide) {
            return;
        }
        int charge = Math.min(entry.getEnabledTicks(), MAX_CHARGE);
        if (charge < MIN_CHARGE) {
            return;
        }
        float rage = HulkRage.fraction(entity);
        double power = (0.8 + 1.6 * charge / MAX_CHARGE) * (1.0 + 0.5 * rage);
        Vec3 look = entity.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize().scale(0.6 * power);
        entity.setDeltaMovement(horizontal.x, power, horizontal.z);
        entity.hurtMarked = true;
        entity.fallDistance = 0;
        AIRBORNE.put(entity.getUUID(), 0);
        if (entity.level() instanceof ServerLevel level) {
            HulkEffects.boom(level, entity.position(), 1.4F);
            level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 2, 0.3, 0.1, 0.3, 0);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Charged super jump. Hold to charge, release to leap.";
    }
}
