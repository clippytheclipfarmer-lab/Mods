package com.example.heroes.viltrumite.ability;

import com.example.heroes.common.HeroEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** While flying fast: sonic booms and a damaging impact wave ahead. Use with an 'is_fast_flying' condition. */
public class SonicBoomAbility extends Ability {
    public SonicBoomAbility() {
        this.withProperty(ICON, new ItemIcon(Items.FIREWORK_ROCKET));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (enabled && entity.level() instanceof ServerLevel level) {
            boom(level, entity);
        }
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 ahead = entity.position().add(entity.getLookAngle().scale(2.5));
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1, entity.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
        if (entity.tickCount % 10 == 0) {
            HeroEffects.blast(level, entity, ahead, 3.0, 5.0F, 1.2, 0.3);
            boom(level, entity);
        }
    }

    private static void boom(ServerLevel level, LivingEntity entity) {
        level.sendParticles(ParticleTypes.SONIC_BOOM, entity.getX(), entity.getY() + 1, entity.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.8F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Sonic boom effect and impact wave while flying fast.";
    }
}
