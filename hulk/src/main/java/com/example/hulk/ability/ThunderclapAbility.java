package com.example.hulk.ability;

import com.example.hulk.HulkEffects;
import com.example.hulk.HulkRage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Hand-clap blast: wide knockback, brief stun, shatters glass. */
public class ThunderclapAbility extends Ability {
    public ThunderclapAbility() {
        this.withProperty(ICON, new ItemIcon(Items.GOAT_HORN));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        float rage = HulkRage.fraction(entity);
        double radius = 6 + 6 * rage;
        Vec3 origin = entity.position().add(0, entity.getBbHeight() * 0.6, 0);

        HulkEffects.blast(level, entity, origin, radius, 4 + 6 * rage, 1.6 + 1.2 * rage, 0.3);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius), e -> e != entity)) {
            if (target.position().distanceTo(origin) <= radius) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + (int) (40 * rage), 3));
            }
        }

        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            int r = (int) radius;
            BlockPos center = BlockPos.containing(origin);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (pos.distSqr(center) <= r * r && level.getBlockState(pos).is(BlockTags.IMPERMEABLE)) {
                    level.destroyBlock(pos.immutable(), false, entity);
                }
            }
        }

        level.sendParticles(ParticleTypes.SONIC_BOOM, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Thunderclap AoE blast. Use with an 'action' condition.";
    }
}
