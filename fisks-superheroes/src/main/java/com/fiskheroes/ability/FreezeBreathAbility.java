package com.fiskheroes.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

/** A cone of freezing breath: slows and freezes creatures, puts out fires and turns water to ice. Use with a 'held' condition. */
public class FreezeBreathAbility extends Ability {
    public static final PalladiumProperty<Float> RANGE = new FloatProperty("range").configurable("Length of the cone in blocks");

    public FreezeBreathAbility() {
        this.withProperty(ICON, new ItemIcon(Items.SNOWBALL));
        this.withProperty(RANGE, 12F);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double range = entry.getProperty(RANGE);
        Vec3 origin = entity.getEyePosition().add(entity.getViewVector(1F).scale(0.5)).subtract(0, 0.15, 0);
        Vec3 look = entity.getViewVector(1F);
        for (int i = 0; i < 12; i++) {
            double dist = level.random.nextDouble() * range;
            double spread = 0.08 + dist * 0.07;
            Vec3 p = origin.add(look.scale(dist)).add((level.random.nextDouble() - 0.5) * spread * 2, (level.random.nextDouble() - 0.5) * spread * 2, (level.random.nextDouble() - 0.5) * spread * 2);
            level.sendParticles(i % 6 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(range), e -> e != entity && e.isAlive())) {
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0).subtract(origin);
            double dist = to.length();
            if (dist > range || dist < 0.5 || to.normalize().dot(look) < 0.82) {
                continue;
            }
            target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 6));
            target.clearFire();
            if (entity.tickCount % 10 == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            }
            if (entity.tickCount % 20 == 0) {
                target.hurt(level.damageSources().freeze(), 3F);
            }
        }
        if (entity.tickCount % 4 == 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            for (double dist = 1; dist <= range; dist += 1.5) {
                BlockPos pos = BlockPos.containing(origin.add(look.scale(dist)));
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    level.removeBlock(pos, false);
                } else if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
                    level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                } else if (state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
                    level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                } else if (state.isAir() && level.getBlockState(pos.below()).isSolidRender(level, pos.below()) && !level.getBlockState(pos.below()).is(Blocks.SNOW)) {
                    level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
                }
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Freezing breath cone.";
    }
}
