package com.example.heroes.stones.ability;

import com.example.heroes.common.HeroEffects;
import com.example.heroes.hulk.GrabSystem;
import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** The abilities of the six stones and the full-set snap. All are used with an 'action' condition. */
public final class StoneAbilities {
    private StoneAbilities() {
    }

    /** Base for an action-triggered ability: runs once, server side, when the key is pressed. */
    abstract static class Action extends Ability {
        Action(Item icon) {
            this.withProperty(ICON, new ItemIcon(icon));
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && entity.level() instanceof ServerLevel level) {
                run(entity, level);
            }
        }

        abstract void run(LivingEntity entity, ServerLevel level);
    }

    private static Vec3 lookEnd(LivingEntity e, double range) {
        return e.getEyePosition().add(e.getViewVector(1F).scale(range));
    }

    private static void beam(ServerLevel level, Vec3 from, Vec3 to, net.minecraft.core.particles.ParticleOptions particle) {
        int steps = (int) Math.max(4, from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ----------------------------------------------------------------- Space: blink

    public static class Blink extends Action {
        public Blink() {
            super(Items.ENDER_PEARL);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            double range = 24 * StoneBoost.general(entity);
            Vec3 eye = entity.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, lookEnd(entity, range), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            Vec3 dest = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(entity.getViewVector(1F).scale(1.2)) : lookEnd(entity, range);
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 40, 0.4, 0.8, 0.4, 0.5);
            entity.teleportTo(dest.x, dest.y - entity.getEyeHeight(), dest.z);
            entity.fallDistance = 0;
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 40, 0.4, 0.8, 0.4, 0.5);
            level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    // ----------------------------------------------------------------- Mind: telekinesis (the Hulk grab/throw, with longer reach)

    public static class Telekinesis extends Action {
        public Telekinesis() {
            super(Items.ENDER_EYE);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            GrabSystem.use(entity);
        }
    }

    // ----------------------------------------------------------------- Reality: turn a creature into something else

    private static final List<Supplier<EntityType<?>>> SHIFT_TARGETS = List.of(
            () -> EntityType.PIG, () -> EntityType.COW, () -> EntityType.SHEEP, () -> EntityType.CHICKEN, () -> EntityType.CREEPER,
            () -> EntityType.ZOMBIE, () -> EntityType.SKELETON, () -> EntityType.SPIDER, () -> EntityType.SLIME, () -> EntityType.VILLAGER,
            () -> EntityType.IRON_GOLEM, () -> EntityType.WOLF, () -> EntityType.RABBIT, () -> EntityType.ENDERMAN);

    public static class RealityShift extends Action {
        public RealityShift() {
            super(Items.AMETHYST_SHARD);
        }

        @Override
        void run(LivingEntity caster, ServerLevel level) {
            Vec3 eye = caster.getEyePosition();
            Vec3 end = lookEnd(caster, 24);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, eye, end, new AABB(eye, end).inflate(1.0),
                    e -> e instanceof Mob && e.isAlive() && !(e instanceof EnderDragon) && !(e instanceof WitherBoss));
            if (hit == null) {
                return;
            }
            Entity old = hit.getEntity();
            EntityType<?> type;
            do {
                type = SHIFT_TARGETS.get(level.random.nextInt(SHIFT_TARGETS.size())).get();
            } while (type == old.getType());
            Entity replacement = type.create(level);
            if (replacement == null) {
                return;
            }
            replacement.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), 0F);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.1F), 2.0F), old.getX(), old.getY() + 1, old.getZ(), 40, 0.5, 0.8, 0.5, 0.0);
            level.playSound(null, old.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
            old.discard();
            level.addFreshEntity(replacement);
        }
    }

    // ----------------------------------------------------------------- Power: energy blast

    public static class PowerBlast extends Action {
        public PowerBlast() {
            super(Items.END_CRYSTAL);
        }

        @Override
        void run(LivingEntity caster, ServerLevel level) {
            Vec3 eye = caster.getEyePosition();
            Vec3 end = lookEnd(caster, 48);
            BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 stop = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.0),
                    e -> e != caster && e.isAlive() && e instanceof LivingEntity);
            Vec3 point = hit != null ? hit.getLocation() : stop;
            beam(level, caster.position().add(0, caster.getBbHeight() * 0.7, 0), point, new DustParticleOptions(new Vector3f(0.7F, 0.2F, 1.0F), 1.8F));
            HeroEffects.blast(level, caster, point, 5.0, 14.0F, 1.0, 0.6);
            level.explode(caster, point.x, point.y, point.z, 2.5F, Level.ExplosionInteraction.MOB);
            level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
    }

    // ----------------------------------------------------------------- Time: rewind (and faster cooldowns, see StoneHero)

    private record Snapshot(ResourceKey<Level> dimension, Vec3 pos, float health) {
    }

    private static final Map<UUID, Deque<Snapshot>> HISTORY = new HashMap<>();

    /** Called every tick for players who wear the Time Stone. */
    public static void recordHistory(ServerPlayer player) {
        Deque<Snapshot> history = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        history.addLast(new Snapshot(player.level().dimension(), player.position(), player.getHealth()));
        while (history.size() > 100) { // five seconds
            history.removeFirst();
        }
    }

    public static void forgetHistory(UUID player) {
        HISTORY.remove(player);
    }

    public static class Rewind extends Action {
        public Rewind() {
            super(Items.CLOCK);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            if (!(entity instanceof ServerPlayer player)) {
                return;
            }
            Deque<Snapshot> history = HISTORY.get(player.getUUID());
            if (history == null || history.isEmpty()) {
                return;
            }
            Snapshot past = history.peekFirst();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
            if (past.dimension().equals(level.dimension())) {
                player.teleportTo(past.pos().x, past.pos().y, past.pos().z);
            }
            player.setHealth(Math.max(player.getHealth(), Math.min(past.health(), player.getMaxHealth())));
            player.fallDistance = 0;
            history.clear();
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 1.5F);
            player.displayClientMessage(Component.literal("Time folds back five seconds."), true);
        }
    }

    // ----------------------------------------------------------------- Soul: drain life

    public static class SoulDrain extends Action {
        public SoulDrain() {
            super(Items.SOUL_LANTERN);
        }

        @Override
        void run(LivingEntity caster, ServerLevel level) {
            Vec3 look = caster.getViewVector(1F);
            LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(9),
                            e -> e != caster && e.isAlive() && e.position().subtract(caster.position()).normalize().dot(look) > 0.5)
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(caster))).orElse(null);
            if (target == null) {
                return;
            }
            float amount = (float) (8.0 * StoneBoost.general(caster));
            target.hurt(level.damageSources().indirectMagic(caster, caster), amount);
            caster.heal(amount * 0.75F);
            beam(level, target.position().add(0, target.getBbHeight() / 2, 0), caster.position().add(0, caster.getBbHeight() / 2, 0), ParticleTypes.SOUL);
            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 0.8F);
        }
    }

    // ----------------------------------------------------------------- Full set: the snap

    public static class Snap extends Action {
        private static final double RADIUS = 128;

        public Snap() {
            super(Items.NETHER_STAR);
        }

        @Override
        void run(LivingEntity caster, ServerLevel level) {
            List<LivingEntity> victims = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RADIUS),
                    e -> e != caster && !(e instanceof Player) && !(e instanceof EnderDragon) && !(e instanceof WitherBoss) && e.isAlive()));
            level.playSound(null, caster.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3.0F, 1.5F);
            int removed = 0;
            for (LivingEntity victim : victims) {
                if (level.random.nextBoolean()) {
                    level.sendParticles(ParticleTypes.ASH, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
                    victim.discard();
                    removed++;
                }
            }
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("Perfectly balanced. " + removed + " creature" + (removed == 1 ? "" : "s") + " turned to ash."), true);
            }
        }
    }
}
