package com.example.heroes.symbiote;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A villager taken by a symbiote: black, hostile, and (if it was a perfect host) elite, able to yank targets in with a tendril. */
public class SymbioteVillagerEntity extends Monster implements SymbioteCreature {
    private static final EntityDataAccessor<Boolean> ELITE = SynchedEntityData.defineId(SymbioteVillagerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.03F), 1.4F);

    public SymbioteVillagerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ELITE, false);
    }

    public boolean isElite() {
        return entityData.get(ELITE);
    }

    public void setElite(boolean elite) {
        entityData.set(ELITE, elite);
        if (elite) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(90.0);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(11.0);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
            setHealth(90.0F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Elite", isElite());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Elite")) {
            setElite(true);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (tickCount % 6 == 0) {
            level.sendParticles(BLACK, getX(), getY() + 1, getZ(), 1, 0.3, 0.6, 0.3, 0.0);
        }
        // Elite: a tendril yanks distant targets in.
        LivingEntity target = getTarget();
        if (isElite() && target != null && tickCount % 80 == 0) {
            double d = distanceTo(target);
            if (d > 5 && d < 16) {
                Vec3 pull = position().subtract(target.position()).normalize().scale(1.4);
                target.setDeltaMovement(pull.x, 0.4, pull.z);
                target.hurtMarked = true;
                Vec3 from = position().add(0, 1.2, 0), to = target.position().add(0, 1, 0);
                int steps = (int) (d * 3);
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = from.lerp(to, i / (double) steps);
                    level.sendParticles(BLACK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
                }
                playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, 1.5F, 0.7F);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // The symbiote escapes as a blob again.
        if (level() instanceof ServerLevel level) {
            SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(level);
            if (blob != null) {
                blob.moveTo(getX(), getY(), getZ(), getYRot(), 0F);
                level.addFreshEntity(blob);
            }
        }
    }

    @Override
    public float getScale() {
        return isElite() ? 1.2F : 1.0F;
    }
}
