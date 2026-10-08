package com.example.heroes.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared shockwave logic for the Hulk powers. */
public final class HeroEffects {
    private HeroEffects() {
    }

    /** Damages and launches every living entity within radius of the origin. */
    public static void blast(ServerLevel level, LivingEntity source, Vec3 origin, double radius, float damage, double knockback, double lift) {
        AABB box = new AABB(origin, origin).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != source && e.isAlive())) {
            double dist = target.position().distanceTo(origin);
            if (dist > radius) {
                continue;
            }
            double falloff = 1.0 - dist / radius * 0.6;
            if (source instanceof Player player) {
                target.hurt(level.damageSources().playerAttack(player), (float) (damage * falloff));
            } else {
                target.hurt(level.damageSources().mobAttack(source), (float) (damage * falloff));
            }
            Vec3 away = target.position().subtract(origin).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : away.normalize();
            target.push(away.x * knockback * falloff, lift * falloff, away.z * knockback * falloff);
            target.hurtMarked = true;
        }
    }

    /** Breaks weak blocks (glass, leaves, dirt, ...) in a flat disc around the origin. */
    public static void breakWeakBlocks(ServerLevel level, LivingEntity source, BlockPos center, int radius, float maxHardness) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 2, radius))) {
            if (pos.distSqr(center) > radius * radius) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (!state.isAir() && hardness >= 0 && hardness <= maxHardness) {
                level.destroyBlock(pos.immutable(), true, source);
            }
        }
    }

    public static void ring(ServerLevel level, Vec3 origin, double radius) {
        int points = (int) (radius * 12);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            level.sendParticles(ParticleTypes.CLOUD, origin.x + Math.cos(angle) * radius, origin.y + 0.1,
                    origin.z + Math.sin(angle) * radius, 1, 0.1, 0.05, 0.1, 0.02);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.2, origin.z, 3, radius / 4, 0.1, radius / 4, 0);
    }

    public static void boom(ServerLevel level, Vec3 at, float pitch) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, pitch);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F, 0.5F);
    }
}
