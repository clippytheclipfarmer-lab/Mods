package com.example.heroes.stones;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Small shared helpers for the stone abilities: look rays and a tick-delayed task queue. */
public final class StoneUtil {
    private record Task(long dueTick, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private StoneUtil() {
    }

    public static Vec3 lookEnd(LivingEntity e, double range) {
        return e.getEyePosition().add(e.getViewVector(1F).scale(range));
    }

    /** First block along the look direction (solid blocks only), or a MISS result at the end of the ray. */
    public static BlockHitResult rayBlock(LivingEntity e, double range) {
        return e.level().clip(new ClipContext(e.getEyePosition(), lookEnd(e, range), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
    }

    /** Where the look ray ends: the block it hits, or the point at full range. */
    public static Vec3 rayEnd(LivingEntity e, double range) {
        BlockHitResult hit = rayBlock(e, range);
        return hit.getType() == HitResult.Type.MISS ? lookEnd(e, range) : hit.getLocation();
    }

    /** Every entity whose bounding box the segment from {@code from} to {@code to} passes through. */
    public static List<Entity> entitiesAlong(ServerLevel level, Entity source, Vec3 from, Vec3 to, double grow, Predicate<Entity> filter) {
        List<Entity> hits = new ArrayList<>();
        for (Entity e : level.getEntities(source, new AABB(from, to).inflate(grow + 1.0), filter)) {
            if (e.getBoundingBox().inflate(grow).clip(from, to).isPresent()) {
                hits.add(e);
            }
        }
        return hits;
    }

    /** Runs {@code action} after {@code delayTicks} server ticks. */
    public static void later(int delayTicks, Runnable action) {
        synchronized (TASKS) {
            TASKS.add(new Task(now + delayTicks, action));
        }
    }

    /** Called once per server tick. */
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
