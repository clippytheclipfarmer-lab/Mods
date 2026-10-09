package com.example.heroes.stones.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.BooleanProperty;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Time Stone: freezing time, stopping projectiles, the day cycle and a local time-rate field. */
public final class TimeAbilities {
    /** Entity tag on everything currently held in a time freeze (it is saved with the entity, see {@link #thaw}). */
    public static final String FROZEN_TAG = "stones_frozen";

    private TimeAbilities() {
    }

    // ----------------------------------------------------------------- freeze

    private static final Map<UUID, Set<UUID>> FROZEN_BY = new ConcurrentHashMap<>();

    /** True while someone is holding this entity frozen. */
    public static boolean isHeld(UUID entity) {
        return FROZEN_BY.values().stream().anyMatch(set -> set.contains(entity));
    }

    private static void freeze(Entity e) {
        if (e instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setDeltaMovement(Vec3.ZERO);
        } else {
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
        }
        e.hurtMarked = true;
        e.addTag(FROZEN_TAG);
    }

    /** Lets a frozen entity move again. Safe to call on anything. */
    public static void thaw(Entity e) {
        if (e.removeTag(FROZEN_TAG)) {
            if (e instanceof Mob mob) {
                mob.setNoAi(false);
            } else {
                e.setNoGravity(false);
            }
        }
    }

    /** Releases everything every caster froze (server shutdown). */
    public static void thawAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Set<UUID> set : FROZEN_BY.values()) {
                for (UUID id : set) {
                    Entity e = level.getEntity(id);
                    if (e != null) {
                        thaw(e);
                    }
                }
            }
        }
        FROZEN_BY.clear();
    }

    /** Toggle: halts time around the holder for everything but players (or only projectiles, see PROJECTILES_ONLY). */
    public static class TimeFreeze extends Ability {
        public static final PalladiumProperty<Boolean> PROJECTILES_ONLY = new BooleanProperty("projectiles_only").configurable("If true only projectiles (arrows, fireballs, ...) are stopped");
        public static final PalladiumProperty<Integer> RADIUS = new IntegerProperty("radius").configurable("Radius in blocks");

        public TimeFreeze() {
            this.withProperty(ICON, new ItemIcon(Items.CLOCK));
            this.withProperty(PROJECTILES_ONLY, false);
            this.withProperty(RADIUS, 40);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && entity.level() instanceof ServerLevel level) {
                FROZEN_BY.put(entity.getUUID(), new HashSet<>());
                level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
            }
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Set<UUID> mine = FROZEN_BY.get(entity.getUUID());
            if (!enabled || mine == null || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            boolean projectilesOnly = entry.getProperty(PROJECTILES_ONLY);
            double radius = entry.getProperty(RADIUS);
            Set<UUID> still = new HashSet<>();
            for (Entity e : level.getEntities(entity, new AABB(entity.position(), entity.position()).inflate(radius), t -> canFreeze(entity, t, projectilesOnly))) {
                freeze(e);
                still.add(e.getUUID());
                mine.add(e.getUUID());
            }
            // Anything that walked out of the field is released.
            for (UUID id : new HashSet<>(mine)) {
                if (!still.contains(id)) {
                    Entity e = level.getEntity(id);
                    if (e != null) {
                        thaw(e);
                    }
                    mine.remove(id);
                }
            }
            if (entity.tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 1.5, 1.0, 1.5, 0.2);
            }
        }

        private static boolean canFreeze(LivingEntity caster, Entity target, boolean projectilesOnly) {
            if (target instanceof Player || !target.isAlive()) {
                return false;
            }
            if (target instanceof Projectile projectile) {
                return projectile.getOwner() != caster;
            }
            return !projectilesOnly && (target instanceof Mob || target instanceof net.minecraft.world.entity.item.ItemEntity);
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Set<UUID> mine = FROZEN_BY.remove(entity.getUUID());
            if (mine != null && entity.level() instanceof ServerLevel level) {
                for (UUID id : mine) {
                    Entity e = level.getEntity(id);
                    if (e != null) {
                        thaw(e);
                    }
                }
                level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Freezes time for everything near the holder except players.";
        }
    }

    // ----------------------------------------------------------------- day cycle

    /** Hold: the daylight cycle races forward. */
    public static class FastForward extends Ability {
        public FastForward() {
            this.withProperty(ICON, new ItemIcon(Items.SUNFLOWER));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && entity.level() instanceof ServerLevel level) {
                level.setDayTime(level.getDayTime() + 100);
                if (entity.tickCount % 10 == 0) {
                    level.playSound(null, entity.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.4F, 1.6F);
                }
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Rapidly advances the daylight cycle while held.";
        }
    }

    /** Action: stops or restarts the daylight cycle. */
    public static class DaylightToggle extends StoneAbilities.Action {
        public DaylightToggle() {
            super(Items.CLOCK);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            MinecraftServer server = level.getServer();
            GameRules.BooleanValue rule = server.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
            rule.set(!rule.get(), server);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(rule.get() ? "Time flows again." : "The daylight cycle is frozen."), true);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Stops or restarts the daylight cycle.";
        }
    }

    // ----------------------------------------------------------------- local time rate

    private static final Map<UUID, Integer> RATE = new ConcurrentHashMap<>();

    /** Extra cooldown ticks per tick for a holder running time faster (read by StoneHero). */
    public static int extraCooldownTicks(Entity entity) {
        return Math.max(0, RATE.getOrDefault(entity.getUUID(), 0));
    }

    /**
     * Toggle: bend the flow of time around you. Scroll (see {@link RateStep}) from -5 (everything nearby crawls) to +5
     * (you and your cooldowns run faster). An approximation: slowness and drag around you, speed and haste on you.
     */
    public static class TimeRate extends Ability {
        public TimeRate() {
            this.withProperty(ICON, new ItemIcon(Items.REDSTONE));
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled) {
                RATE.putIfAbsent(entity.getUUID(), 0);
            }
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Integer rate = RATE.get(entity.getUUID());
            if (!enabled || rate == null || rate == 0 || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            if (rate > 0) {
                if (entity.tickCount % 20 == 0) {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, rate - 1, true, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, rate - 1, true, false, false));
                }
                return;
            }
            int drag = -rate;
            for (Entity e : level.getEntities(entity, entity.getBoundingBox().inflate(32), t -> t != entity && t.isAlive() && !(t instanceof Player))) {
                if (e instanceof LivingEntity living && entity.tickCount % 10 == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, Math.min(6, drag + 1), true, false, false));
                } else if (e instanceof Projectile) {
                    e.setDeltaMovement(e.getDeltaMovement().scale(1.0 - 0.15 * drag));
                }
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            RATE.remove(entity.getUUID());
        }

        @Override
        public String getDocumentationDescription() {
            return "Slow down the world around you or speed yourself up; use the step abilities to change the rate.";
        }
    }

    /** Scroll step for {@link TimeRate}. */
    public static class RateStep extends Ability {
        public static final PalladiumProperty<Integer> DELTA = new IntegerProperty("delta").configurable("+1 speeds time up, -1 slows it down");

        public RateStep() {
            this.withProperty(ICON, new ItemIcon(Items.REDSTONE));
            this.withProperty(DELTA, 1);
            this.withProperty(HIDDEN_IN_GUI, true);
            this.withProperty(HIDDEN_IN_BAR, true);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level) || !RATE.containsKey(entity.getUUID())) {
                return;
            }
            int next = Math.max(-5, Math.min(5, RATE.get(entity.getUUID()) + entry.getProperty(DELTA)));
            RATE.put(entity.getUUID(), next);
            level.playSound(null, entity.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.8F, 1.0F + next * 0.08F);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(next == 0 ? "Time flows normally." : next > 0 ? "Time quickens (+" + next + ")." : "Time slows (" + next + ")."), true);
            }
        }
    }
}
