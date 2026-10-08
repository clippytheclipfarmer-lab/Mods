package com.example.heroes.symbiote;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;

/**
 * The black blob inside a symbiote meteor. It slowly drifts about, lures other mobs to itself and absorbs them,
 * and hunts players and villagers. On contact it lifts the victim with a tendril and tries to bond (20% death,
 * 60% ordinary bond, 20% perfect host).
 */
public class SymbioteBlobEntity extends Monster {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(SymbioteBlobEntity.class, EntityDataSerializers.INT);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.03F), 1.6F);
    private static final int BOND_TICKS = 70;
    private static final double LURE_RANGE = 24;

    private LivingEntity bonding;
    private int bondTicks;
    private int cooldown;

    public SymbioteBlobEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.14)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SIZE, 1);
    }

    public int getBlobSize() {
        return entityData.get(SIZE);
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        if (SIZE.equals(accessor)) {
            refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(1.0F + 0.15F * getBlobSize());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BlobSize", getBlobSize());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SIZE, Math.max(1, tag.getInt("BlobSize")));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SeekBondTargetGoal());
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && bonding != null) {
            // A hit breaks the grip.
            endBond(false);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (bonding != null) {
            bondTick(level);
            return;
        }
        if (tickCount % 4 == 0) {
            level.sendParticles(BLACK, getX(), getY() + getBbHeight() / 2, getZ(), 2, getBbWidth() / 3, getBbHeight() / 3, getBbWidth() / 3, 0.0);
        }
        if (tickCount % 60 == 0) {
            playSound(SoundEvents.SLIME_SQUISH, 0.8F, 0.5F);
        }
        if (tickCount % 4 == 0) {
            lureAndAbsorb(level);
        }
        if (tickCount % 5 == 0 && cooldown == 0) {
            tryStartBond(level);
        }
    }

    // ------------------------------------------------------------------ luring and absorbing

    private void lureAndAbsorb(ServerLevel level) {
        AABB area = getBoundingBox().inflate(LURE_RANGE, 8, LURE_RANGE);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, m -> m != this && m.isAlive() && !(m instanceof SymbioteBlobEntity)
                && !(m instanceof SymbioteVillagerEntity) && !(m instanceof Villager) && m.getBbHeight() < 3.2F);
        double absorbRange = 2.2 + getBbWidth();
        for (Mob mob : mobs) {
            if (mob.distanceToSqr(this) <= absorbRange * absorbRange) {
                absorb(level, mob);
            } else if (mob.distanceToSqr(this) < 24 * 24) {
                // Drawn in: head for the blob and get dragged along, whatever the mob's own AI wants.
                mob.getNavigation().moveTo(this, 1.2);
                Vec3 pull = position().subtract(mob.position());
                Vec3 flat = new Vec3(pull.x, 0, pull.z);
                if (flat.lengthSqr() > 1.0E-4) {
                    flat = flat.normalize().scale(0.07);
                    mob.setDeltaMovement(mob.getDeltaMovement().add(flat.x, 0, flat.z));
                    mob.hurtMarked = true;
                }
            }
        }
    }

    private void absorb(ServerLevel level, Mob mob) {
        level.sendParticles(BLACK, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, mob.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 1.0F, 0.6F);
        mob.hurt(damageSources().mobAttack(this), 1000.0F);
        heal(4.0F);
        if (getRandom().nextInt(2) == 0 && getBlobSize() < 6) {
            entityData.set(SIZE, getBlobSize() + 1);
        }
    }

    private void tryStartBond(ServerLevel level) {
        AABB reach = getBoundingBox().inflate(1.3);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach, e -> isBondCandidate(e))) {
            startBond(target);
            return;
        }
    }

    private boolean isBondCandidate(LivingEntity e) {
        if (!e.isAlive() || e == this) {
            return false;
        }
        if (e instanceof Villager) {
            return true;
        }
        return e instanceof Player p && !p.isCreative() && !p.isSpectator() && !SymbioteHost.isHost(p);
    }

    // ------------------------------------------------------------------ bonding

    private void startBond(LivingEntity target) {
        bonding = target;
        bondTicks = 0;
        playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, 2.0F, 0.6F);
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("A black tendril lashes around you..."), true);
        }
    }

    private void bondTick(ServerLevel level) {
        if (bonding == null || !bonding.isAlive() || bonding.distanceToSqr(this) > 144 || (bonding instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()))) {
            endBond(false);
            return;
        }
        bondTicks++;
        getNavigation().stop();

        // Lift the victim up above the blob, then hold them there.
        double lift = Math.min(bondTicks, 25) / 25.0;
        Vec3 hold = position().add(0, getBbHeight() + 0.4 + lift * 1.4, 0);
        Vec3 target = bonding.position().lerp(hold, 0.35);
        if (bonding instanceof ServerPlayer player) {
            player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        } else {
            bonding.setPos(target);
        }
        bonding.setDeltaMovement(Vec3.ZERO);
        bonding.fallDistance = 0;
        bonding.hurtMarked = true;

        // The tendril: a line of black dust from the blob to the victim, thickening as the bond tightens.
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        Vec3 to = bonding.position().add(0, bonding.getBbHeight() / 2, 0);
        int steps = (int) Math.max(4, from.distanceTo(to) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(BLACK, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
        if (bondTicks > 25) {
            // Black spirals around the victim.
            double a = bondTicks * 0.5;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * Math.PI * 2 / 3;
                double y = bonding.getY() + (bondTicks % 20) / 20.0 * bonding.getBbHeight();
                level.sendParticles(BLACK, bonding.getX() + Math.cos(ang) * 0.7, y, bonding.getZ() + Math.sin(ang) * 0.7, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (bondTicks % 10 == 0) {
                level.playSound(null, bonding.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8F, 0.5F + bondTicks / 140F);
            }
        }
        if (bondTicks >= BOND_TICKS) {
            resolve(level);
        }
    }

    /** 20% death, 60% ordinary bond, 20% perfect host. */
    private void resolve(ServerLevel level) {
        LivingEntity victim = bonding;
        int roll = getRandom().nextInt(100);
        boolean fatal = roll < 20;
        boolean perfect = roll >= 80;
        bonding = null;

        if (fatal) {
            if (victim instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("The symbiote rejects you."));
            }
            level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
            victim.hurt(damageSources().genericKill(), Float.MAX_VALUE);
            cooldown = 100;
            return;
        }
        level.playSound(null, victim.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2F, 0.7F);
        level.sendParticles(BLACK, victim.getX(), victim.getY() + 1, victim.getZ(), 80, 0.5, 0.9, 0.5, 0.1);
        if (victim instanceof ServerPlayer player) {
            SymbioteHost.bond(player, perfect);
            discard();
        } else if (victim instanceof Villager villager) {
            SymbioteVillagerEntity host = SymbioteEntities.SYMBIOTE_VILLAGER.create(level);
            if (host != null) {
                host.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0F);
                host.setElite(perfect);
                level.addFreshEntity(host);
            }
            villager.discard();
            discard();
        }
    }

    private void endBond(boolean fatal) {
        bonding = null;
        cooldown = 60;
    }

    // ------------------------------------------------------------------ goals

    /** Drift toward the nearest player or villager. The blob is slow, so there is time to run. */
    private final class SeekBondTargetGoal extends Goal {
        private LivingEntity target;

        SeekBondTargetGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (bonding != null || cooldown > 0) {
                return false;
            }
            target = level().getNearestEntity(level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20, 6, 20),
                    SymbioteBlobEntity.this::isBondCandidate), net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat(),
                    SymbioteBlobEntity.this, getX(), getY(), getZ());
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return bonding == null && target != null && target.isAlive() && distanceToSqr(target) < 30 * 30;
        }

        @Override
        public void tick() {
            getNavigation().moveTo(target, 1.0);
        }

        @Override
        public void stop() {
            target = null;
        }
    }
}
