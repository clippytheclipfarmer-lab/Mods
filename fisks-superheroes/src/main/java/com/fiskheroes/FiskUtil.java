package com.fiskheroes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Look rays, damage sources and a tick-delayed task queue shared by the abilities. */
public final class FiskUtil {
    private record Task(long dueTick, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private FiskUtil() {
    }

    public static Vec3 lookEnd(LivingEntity e, double range) {
        return e.getEyePosition().add(e.getViewVector(1F).scale(range));
    }

    public static BlockHitResult rayBlock(LivingEntity e, double range) {
        return e.level().clip(new ClipContext(e.getEyePosition(), lookEnd(e, range), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
    }

    /** Where the look ray ends: the block it hits or the point at full range. */
    public static Vec3 rayEnd(LivingEntity e, double range) {
        BlockHitResult hit = rayBlock(e, range);
        return hit.getType() == HitResult.Type.MISS ? lookEnd(e, range) : hit.getLocation();
    }

    /** Every entity the segment passes through (box grown by {@code grow}). */
    public static List<Entity> entitiesAlong(ServerLevel level, Entity source, Vec3 from, Vec3 to, double grow, Predicate<Entity> filter) {
        List<Entity> hits = new ArrayList<>();
        for (Entity e : level.getEntities(source, new AABB(from, to).inflate(grow + 1.0), filter)) {
            if (e.getBoundingBox().inflate(grow).clip(from, to).isPresent()) {
                hits.add(e);
            }
        }
        return hits;
    }

    /** The closest living entity along the look ray (stopping at the first block), or null. */
    public static LivingEntity lookedAtLiving(LivingEntity e, double range) {
        if (!(e.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 from = e.getEyePosition();
        Vec3 to = rayEnd(e, range);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity hit : entitiesAlong(level, e, from, to, 0.3, t -> t instanceof LivingEntity l && l.isAlive() && !(t instanceof Player p && p.isSpectator()))) {
            double d = hit.distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) hit;
            }
        }
        return best;
    }

    public static DamageSource attack(ServerLevel level, LivingEntity attacker) {
        return attacker instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(attacker);
    }

    /** Runs {@code action} after {@code delayTicks} server ticks. */
    public static void later(int delayTicks, Runnable action) {
        synchronized (TASKS) {
            TASKS.add(new Task(now + delayTicks, action));
        }
    }

    public static void tickTasks() {
        now++;
        List<Runnable> due = new ArrayList<>();
        synchronized (TASKS) {
            TASKS.removeIf(task -> {
                if (task.dueTick <= now) {
                    due.add(task.action);
                    return true;
                }
                return false;
            });
        }
        due.forEach(Runnable::run);
    }

    public static void clearTasks() {
        synchronized (TASKS) {
            TASKS.clear();
        }
    }
}
