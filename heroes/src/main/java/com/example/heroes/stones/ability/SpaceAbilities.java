package com.example.heroes.stones.ability;

import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import com.example.heroes.stones.StoneUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Space Stone extras: step assist, the force field and the black hole (the latter needs the Power Stone too). */
public final class SpaceAbilities {
    private SpaceAbilities() {
    }

    /** Walk up full blocks without jumping. */
    public static class StepAssist extends Ability {
        public StepAssist() {
            this.withProperty(ICON, new ItemIcon(Items.SMOOTH_STONE_SLAB));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled) {
                entity.setMaxUpStep(1.1F);
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            entity.setMaxUpStep(0.6F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Lets the holder step up full blocks.";
        }
    }

    /** Toggle: an impenetrable bubble that makes the holder invulnerable but unable to move. */
    public static class ForceField extends Ability {
        /** Everyone whose field is up right now (read by the damage hook in StoneHero). */
        public static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

        public ForceField() {
            this.withProperty(ICON, new ItemIcon(Items.HEART_OF_THE_SEA));
        }

        public static boolean isProtected(Entity entity) {
            return ACTIVE.contains(entity.getUUID());
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled) {
                ACTIVE.add(entity.getUUID());
                if (entity.level() instanceof ServerLevel level) {
                    level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
                }
            }
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled) {
                return;
            }
            ACTIVE.add(entity.getUUID());
            entity.fallDistance = 0;
            entity.setDeltaMovement(0, Math.min(0, entity.getDeltaMovement().y), 0); // rooted: no walking, no jumping
            entity.clearFire();
            if (entity.level() instanceof ServerLevel level && entity.tickCount % 3 == 0) {
                double radius = 1.6;
                for (int i = 0; i < 10; i++) {
                    double yaw = level.random.nextDouble() * Math.PI * 2;
                    double pitch = (level.random.nextDouble() - 0.5) * Math.PI;
                    level.sendParticles(ParticleTypes.END_ROD, entity.getX() + Math.cos(yaw) * Math.cos(pitch) * radius,
                            entity.getY() + 1 + Math.sin(pitch) * radius * 1.2, entity.getZ() + Math.sin(yaw) * Math.cos(pitch) * radius,
                            1, 0, 0, 0, 0);
                }
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            ACTIVE.remove(entity.getUUID());
            if (entity.level() instanceof ServerLevel level) {
                level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Invulnerable force field; the holder cannot move while it is up.";
        }
    }

    /** Toggle: a black hole ahead of the holder that drags in, crushes and erodes everything. Needs the Power Stone as well. */
    public static class BlackHole extends Ability {
        private static final double PULL_RADIUS = 28;
        private static final double ERODE_RADIUS = 5;
        private static final Map<UUID, Vec3> CENTER = new ConcurrentHashMap<>();

        public BlackHole() {
            this.withProperty(ICON, new ItemIcon(Items.OBSIDIAN));
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            if (!StoneBoost.has(entity, InfinityStone.POWER)) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("The black hole needs the Power Stone too."), true);
                }
                return;
            }
            Vec3 eye = entity.getEyePosition();
            var hit = StoneUtil.rayBlock(entity, 40);
            Vec3 center = hit.getType() == HitResult.Type.MISS ? StoneUtil.lookEnd(entity, 20)
                    : hit.getLocation().subtract(entity.getViewVector(1F).scale(ERODE_RADIUS));
            CENTER.put(entity.getUUID(), center.y < eye.y - 30 ? eye : center);
            level.playSound(null, entity.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.5F);
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Vec3 center = CENTER.get(entity.getUUID());
            if (!enabled || center == null || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            if (!StoneBoost.has(entity, InfinityStone.POWER)) {
                CENTER.remove(entity.getUUID());
                return;
            }
            // Pull and crush.
            AABB box = new AABB(center, center).inflate(PULL_RADIUS);
            for (Entity e : level.getEntities(entity, box, t -> !(t instanceof Player p && p.isSpectator()))) {
                Vec3 to = center.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                double dist = Math.max(to.length(), 0.5);
                if (dist > PULL_RADIUS) {
                    continue;
                }
                if (dist < 2.5) {
                    if (e instanceof ItemEntity) {
                        e.discard();
                    } else if (e instanceof LivingEntity living) {
                        living.hurt(level.damageSources().magic(), 8F);
                    }
                    continue;
                }
                double pull = Math.min(0.18 + 6.0 / dist * 0.12, 1.2);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(to.normalize().scale(pull)));
                e.hurtMarked = true;
                e.fallDistance = 0;
            }
            // Erode blocks near the core.
            if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && entity.tickCount % 2 == 0) {
                BlockPos origin = BlockPos.containing(center);
                for (int i = 0; i < 10; i++) {
                    BlockPos pos = origin.offset(level.random.nextInt(11) - 5, level.random.nextInt(11) - 5, level.random.nextInt(11) - 5);
                    if (pos.distToCenterSqr(center) > ERODE_RADIUS * ERODE_RADIUS) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
                        level.removeBlock(pos, false);
                    }
                }
            }
            // The look of it.
            for (int i = 0; i < 18; i++) {
                double yaw = level.random.nextDouble() * Math.PI * 2;
                double r = 1.5 + level.random.nextDouble() * 4;
                level.sendParticles(ParticleTypes.PORTAL, center.x + Math.cos(yaw) * r, center.y + (level.random.nextDouble() - 0.5) * 2, center.z + Math.sin(yaw) * r,
                        0, -Math.cos(yaw), 0.0, -Math.sin(yaw), 1.4);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 6, 0.6, 0.6, 0.6, 0.02);
            if (entity.tickCount % 30 == 0) {
                level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.4F);
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (CENTER.remove(entity.getUUID()) != null && entity.level() instanceof ServerLevel level) {
                level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "A black hole that consumes entities and blocks. Requires the Power Stone as well.";
        }
    }
}
