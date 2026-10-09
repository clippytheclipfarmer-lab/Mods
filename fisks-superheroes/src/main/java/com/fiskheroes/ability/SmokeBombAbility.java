package com.fiskheroes.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

/** Drops a smoke bomb: everything around is blinded and slowed while the holder slips away unseen. Use with an 'action' condition. */
public class SmokeBombAbility extends Ability {
    public static final PalladiumProperty<Float> RADIUS = new FloatProperty("radius").configurable("Radius of the smoke");

    public SmokeBombAbility() {
        this.withProperty(ICON, new ItemIcon(Items.GUNPOWDER));
        this.withProperty(RADIUS, 6F);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = entry.getProperty(RADIUS);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius), e -> e != entity && e.isAlive() && !(e instanceof Player p && p.isCreative()))) {
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            if (other instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(null);
            }
        }
        entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, false, false));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 1, entity.getZ(), 120, radius / 3, 0.8, radius / 3, 0.02);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, entity.getX(), entity.getY() + 0.5, entity.getZ(), 30, radius / 3, 0.3, radius / 3, 0.03);
        level.playSound(null, entity.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Smoke bomb: blinds and slows everything nearby and makes the holder invisible for a few seconds.";
    }
}
