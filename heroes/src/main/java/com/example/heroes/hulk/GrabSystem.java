package com.example.heroes.hulk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.example.heroes.common.HeroEffects;
import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side state for Hulk's grab-and-throw. A press with nothing in hand grabs the entity (or block, which
 * becomes a falling-block entity) in front of him; the next press throws it. Thrown things explode on impact.
 */
public final class GrabSystem {
    private static final int MAX_HOLD_TICKS = 200;

    private static final class Held {
        final Entity entity;
        final boolean hadNoGravity;
        final boolean hadNoAi;
        int ticks;

        Held(Entity entity) {
            this.entity = entity;
            this.hadNoGravity = entity.isNoGravity();
            this.hadNoAi = entity instanceof Mob mob && mob.isNoAi();
        }
    }

    private static final class Thrown {
        final Entity entity;
        final UUID thrower;
        final float damage;
        final double radius;
        Vec3 lastPos;
        int ticks;

        Thrown(Entity entity, UUID thrower, float damage, double radius) {
            this.entity = entity;
            this.thrower = thrower;
            this.damage = damage;
            this.radius = radius;
            this.lastPos = entity.position();
        }
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();
    private static final Map<UUID, Thrown> THROWN = new HashMap<>();

    private GrabSystem() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(GrabSystem::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HELD.clear();
            THROWN.clear();
        });
    }

    public static boolean isHolding(LivingEntity holder) {
        return HELD.containsKey(holder.getUUID());
    }

    /** One press: grab if empty-handed, otherwise throw. */
    public static void use(LivingEntity holder) {
        if (!(holder.level() instanceof ServerLevel level)) {
            return;
        }
        Held held = HELD.get(holder.getUUID());
        if (held != null) {
            throwHeld(level, holder, held);
        } else {
            grab(level, holder);
        }
    }

    private static void grab(ServerLevel level, LivingEntity holder) {
        float tier = HulkRage.tier(holder).multiplier;
        double mind = StoneBoost.mult(holder, InfinityStone.MIND, 2.0);
        double reach = 6.0 * Math.max(1.0, holder.getBbHeight() / 1.8) * mind;
        Vec3 eye = holder.getEyePosition();
        Vec3 end = eye.add(holder.getViewVector(1F).scale(reach));

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, holder, eye, end,
                new AABB(eye, end).inflate(1.0),
                e -> e != holder && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof FallingBlockEntity)
                        && e.getBbHeight() <= 3.5 * tier * mind && !HELD.containsKey(e.getUUID()));
        Entity target = hit != null ? hit.getEntity() : null;

        if (target == null && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, holder));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (!state.hasBlockEntity() && hardness >= 0 && hardness <= 5.0F * tier && state.getFluidState().isEmpty()) {
                    target = FallingBlockEntity.fall(level, pos.immutable(), state);
                }
            }
        }
        if (target == null) {
            return;
        }
        Held held = new Held(target);
        target.setNoGravity(true);
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
        }
        HELD.put(holder.getUUID(), held);
        HeroEffects.boom(level, target.position(), 1.6F);
    }

    private static void throwHeld(ServerLevel level, LivingEntity holder, Held held) {
        release(holder.getUUID());
        Entity entity = held.entity;
        float rage = HulkRage.fraction(holder);
        float tier = HulkRage.tier(rage).multiplier;
        double speed = 1.6 + 1.2 * rage * tier;
        entity.setDeltaMovement(holder.getViewVector(1F).scale(speed));
        entity.hurtMarked = true;
        entity.fallDistance = 0;
        if (entity instanceof FallingBlockEntity block) {
            block.setHurtsEntities(2.0F, 80);
        }
        HeroEffects.boom(level, holder.position(), 1.2F);
        THROWN.put(entity.getUUID(), new Thrown(entity, holder.getUUID(), (6 + 10 * rage) * tier, (2.5 + 1.5 * rage) * tier));
    }

    /** Puts a held entity back to normal (it drops where it is). */
    public static void release(UUID holderId) {
        Held held = HELD.remove(holderId);
        if (held == null) {
            return;
        }
        Entity entity = held.entity;
        entity.setNoGravity(held.hadNoGravity);
        if (entity instanceof Mob mob) {
            mob.setNoAi(held.hadNoAi);
        }
    }

    private static void tick(ServerLevel level) {
        Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator();
        java.util.List<UUID> toRelease = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Held> e = it.next();
            Held held = e.getValue();
            if (held.entity.level() != level) {
                continue;
            }
            Entity holder = level.getEntity(e.getKey());
            if (!(holder instanceof LivingEntity living) || !living.isAlive() || held.entity.isRemoved()
                    || !(HulkRage.isHulk(living) || StoneBoost.has(living, InfinityStone.MIND)) || ++held.ticks > MAX_HOLD_TICKS) {
                toRelease.add(e.getKey());
                continue;
            }
            // Carry it out in front of his chest.
            double scale = Math.max(1.0, holder.getBbHeight() / 1.8);
            Vec3 look = living.getViewVector(1F);
            Vec3 pos = living.getEyePosition().add(look.scale(1.8 * scale)).subtract(0, held.entity.getBbHeight() / 2, 0);
            moveTo(held.entity, pos);
        }
        toRelease.forEach(GrabSystem::release);

        Iterator<Map.Entry<UUID, Thrown>> tit = THROWN.entrySet().iterator();
        while (tit.hasNext()) {
            Thrown t = tit.next().getValue();
            if (t.entity.level() != level) {
                continue;
            }
            if (t.entity.isRemoved()) {
                tit.remove();
                impact(level, t);
                continue;
            }
            t.ticks++;
            t.lastPos = t.entity.position();
            boolean hit = false;
            if (t.ticks > 2) {
                hit = t.entity.onGround() || t.entity.horizontalCollision || t.entity.verticalCollision
                        || !level.getEntitiesOfClass(LivingEntity.class, t.entity.getBoundingBox().inflate(0.4),
                        e -> e != t.entity && !e.getUUID().equals(t.thrower)).isEmpty();
            }
            if (hit || t.ticks > 120) {
                tit.remove();
                impact(level, t);
            }
        }
    }

    private static void moveTo(Entity entity, Vec3 pos) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        } else {
            entity.setPos(pos);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        entity.hurtMarked = true;
    }

    private static void impact(ServerLevel level, Thrown t) {
        Entity thrower = level.getEntity(t.thrower);
        LivingEntity source = thrower instanceof LivingEntity living ? living
                : t.entity instanceof LivingEntity living ? living : null;
        if (source != null) {
            HeroEffects.blast(level, source, t.lastPos, t.radius, t.damage, 1.0, 0.5);
        }
        HeroEffects.ring(level, t.lastPos, t.radius);
        HeroEffects.boom(level, t.lastPos, 1.0F);
    }
}
