package com.example.heroes.pod;

import com.example.heroes.space.PlanetDef;
import com.example.heroes.space.Planets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A one-seat pod. The server flies it: manually (W moves along where you look, sneak to get out) or on autopilot
 * toward a celestial body picked on the star map. Riders are plain passengers, so movement is server-authoritative.
 */
public class SpacePodEntity extends Entity {
    private static final double MANUAL_MAX_SPEED = 1.6;
    private static final double AUTOPILOT_MAX_SPEED = 3.0;
    private static final double CLIMB_SPEED = 2.2;

    @Nullable
    private ResourceLocation target;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    private int lerpSteps;

    public SpacePodEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Nullable
    public ResourceLocation getTarget() {
        return target;
    }

    public void setTarget(@Nullable ResourceLocation target) {
        this.target = target;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        target = tag.contains("Target") ? new ResourceLocation(tag.getString("Target")) : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (target != null) {
            tag.putString("Target", target.toString());
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.3;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player) {
            spawnAtLocation(PodRegistry.SPACE_POD_ITEM);
            discard();
            return true;
        }
        return false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 10;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                double nx = getX() + (lerpX - getX()) / lerpSteps;
                double ny = getY() + (lerpY - getY()) / lerpSteps;
                double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
                setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
                setXRot(getXRot() + (lerpXRot - getXRot()) / lerpSteps);
                lerpSteps--;
                setPos(nx, ny, nz);
                setRot(getYRot(), getXRot());
            }
            return;
        }

        Entity rider = getFirstPassenger();
        Vec3 velocity = getDeltaMovement();
        if (target != null) {
            velocity = autopilot(rider);
        } else if (rider instanceof LivingEntity pilot) {
            Vec3 look = pilot.getLookAngle();
            velocity = velocity.scale(0.92).add(look.scale(pilot.zza * 0.12));
            if (velocity.length() > MANUAL_MAX_SPEED) {
                velocity = velocity.normalize().scale(MANUAL_MAX_SPEED);
            }
            setYRot(pilot.getYRot());
            setXRot(pilot.getXRot());
        } else {
            velocity = velocity.scale(0.8);
        }
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
    }

    private Vec3 autopilot(@Nullable Entity rider) {
        PlanetDef def = Planets.get(target);
        if (def == null) {
            target = null;
            return Vec3.ZERO;
        }
        if (!level().dimension().equals(Planets.SPACE)) {
            // Still inside an atmosphere: climb straight up until the launch altitude takes us into space.
            setXRot(-90F);
            return new Vec3(0, CLIMB_SPEED, 0);
        }
        Vec3 to = def.position.subtract(position());
        double distance = to.length();
        double stop = stopDistance(def);
        if (distance <= stop) {
            ResourceLocation arrived = target;
            target = null;
            if (rider instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("Arrived at " + def.name + "."), true);
            }
            return Vec3.ZERO;
        }
        double speed = Mth.clamp((distance - stop) * 0.08, 0.4, AUTOPILOT_MAX_SPEED);
        Vec3 direction = to.normalize();
        setYRot((float) (Mth.atan2(-direction.x, direction.z) * (180.0 / Math.PI)));
        setXRot((float) (-Math.asin(direction.y) * (180.0 / Math.PI)));
        return direction.scale(speed);
    }

    /** How far from the body's center the pod stops. Planets: close enough that landing triggers. */
    public static double stopDistance(PlanetDef def) {
        return switch (def.kind) {
            case PLANET -> def.radius + 3;
            case STATION -> def.radius + 10;
            case STAR -> def.radius + 30;
            case BLACK_HOLE -> def.radius * 4 + 60;
        };
    }
}
