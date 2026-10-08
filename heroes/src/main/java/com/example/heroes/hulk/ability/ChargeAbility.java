package com.example.heroes.hulk.ability;

import com.example.heroes.common.HeroEffects;
import com.example.heroes.hulk.HulkRage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Super sprint with a shoulder charge that bowls over mobs and smashes weak blocks. Use with a 'held' condition. */
public class ChargeAbility extends Ability {
    public ChargeAbility() {
        this.withProperty(ICON, new ItemIcon(Items.LEATHER_BOOTS));
    }

    private static final int RAMP_TICKS = 80;

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled) {
            return;
        }
        float rage = HulkRage.fraction(entity);
        // The longer you keep running the faster you go: ramps from ~10 to ~38 blocks/s over 4 seconds.
        float ramp = Math.min(entry.getEnabledTicks(), RAMP_TICKS) / (float) RAMP_TICKS;
        Vec3 look = entity.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        dir = dir.normalize();

        if (entity.level().isClientSide) {
            // Players steer their own movement on their client.
            if (entity instanceof Player player && player.isLocalPlayer()) {
                double speed = 0.5 + (0.6 + 0.8 * rage) * ramp;
                entity.setDeltaMovement(dir.x * speed, entity.getDeltaMovement().y, dir.z * speed);
            }
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        float tier = HulkRage.tier(rage).multiplier;
        AABB front = entity.getBoundingBox().move(dir.scale(entity.getBbWidth())).inflate(0.3, 0, 0.3);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, front, e -> e != entity && e.isAlive())) {
            target.hurt(entity instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(entity),
                    (4 + 6 * rage) * tier * (0.5F + ramp));
            target.push(dir.x * (0.8 + rage + ramp), 0.45, dir.z * (0.8 + rage + ramp));
            target.hurtMarked = true;
        }

        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(front.minX, front.minY, front.minZ),
                    BlockPos.containing(front.maxX, front.maxY + 0.5, front.maxZ))) {
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (!state.isAir() && hardness >= 0 && hardness <= 1.5F + 2.0F * rage * tier * (0.5F + ramp)) {
                    level.destroyBlock(pos.immutable(), true, entity);
                }
            }
        }
        if (entity.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1, entity.getZ(), 2, 0.3, 0.05, 0.3, 0.02);
        }
        if (entity.tickCount % 8 == 0) {
            HeroEffects.boom(level, entity.position(), 1.6F - 0.5F * ramp);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Super sprint with shoulder charge. Hold to run.";
    }
}
