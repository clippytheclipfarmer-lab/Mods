package com.fiskheroes.entity;

import com.fiskheroes.FiskHeroes;
import com.fiskheroes.FiskUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A thrown gadget: Captain America's shield (ricochets between enemies), Thor's hammer (calls down lightning) and the
 * batarang. All of them fly out, hit, and then return to their owner like a boomerang.
 */
public class GadgetEntity extends ThrowableItemProjectile {
    public enum Kind {
        SHIELD(4, 14F), HAMMER(0, 16F), BATARANG(2, 7F);

        public final int ricochets;
        public final float defaultDamage;

        Kind(int ricochets, float defaultDamage) {
            this.ricochets = ricochets;
            this.defaultDamage = defaultDamage;
        }

        public static Kind byName(String name) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(name)) {
                    return k;
                }
            }
            return SHIELD;
        }
    }

    private static final int MAX_FLIGHT = 24;
    private static final double SPEED = 1.7;

    private Kind kind = Kind.SHIELD;
    private float damage = 10F;
    private boolean giveBack = true;
    private boolean returning;
    private int bounces;
    private int age;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public GadgetEntity(EntityType<? extends GadgetEntity> type, Level level) {
        super(type, level);
    }

    public GadgetEntity(Level level, LivingEntity owner, ItemStack display, Kind kind, float damage, boolean giveBack) {
        super(FiskHeroes.GADGET, owner, level);
        this.setItem(display);
        this.kind = kind;
        this.damage = damage;
        this.giveBack = giveBack;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SHIELD;
    }

    @Override
    protected float getGravity() {
        return 0F;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !alreadyHit.contains(entity.getUUID()) && entity != getOwner();
    }

    @Override
    public void tick() {
        if (returning) {
            returnTick();
            return;
        }
        age++;
        super.tick();
        if (!level().isClientSide) {
            if (age > MAX_FLIGHT) {
                startReturn();
            }
        } else if (age % 2 == 0) {
            level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    private void startReturn() {
        returning = true;
        noPhysics = true;
        setNoGravity(true);
    }

    private void returnTick() {
        age++;
        if (level().isClientSide) {
            return;
        }
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            dropAndDiscard();
            return;
        }
        Vec3 to = owner.getEyePosition().subtract(position()).add(0, -0.3, 0);
        double dist = to.length();
        if (dist < 1.6 || age > 400) {
            giveToOwner(owner);
            return;
        }
        Vec3 delta = to.normalize().scale(Math.min(2.2, SPEED + 0.6));
        setDeltaMovement(delta);
        setPos(getX() + delta.x, getY() + delta.y, getZ() + delta.z);
        hasImpulse = true;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private void giveToOwner(Entity owner) {
        if (giveBack && owner instanceof Player player) {
            ItemStack stack = getItem().copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        discard();
    }

    private void dropAndDiscard() {
        if (giveBack && !level().isClientSide) {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), getItem().copy()));
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || returning) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = getOwner();
        alreadyHit.add(target.getUUID());
        if (target instanceof LivingEntity living && level() instanceof ServerLevel server) {
            living.hurt(server.damageSources().thrown(this, owner), damage);
            Vec3 push = getDeltaMovement().normalize().scale(0.6);
            living.push(push.x, 0.25, push.z);
            living.hurtMarked = true;
            if (kind == Kind.HAMMER) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                if (bolt != null) {
                    bolt.moveTo(target.position());
                    bolt.setVisualOnly(true);
                    server.addFreshEntity(bolt);
                }
                living.hurt(server.damageSources().lightningBolt(), damage * 0.5F);
                living.setSecondsOnFire(4);
            }
            server.playSound(null, target.blockPosition(), kind == Kind.HAMMER ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, kind == Kind.BATARANG ? 1.6F : 1.0F);
        }
        if (bounces < kind.ricochets && ricochet(target)) {
            bounces++;
        } else {
            startReturn();
        }
    }

    /** Redirects the gadget at the nearest enemy that has not been hit yet. */
    private boolean ricochet(Entity from) {
        Entity owner = getOwner();
        LivingEntity next = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(11),
                        e -> e.isAlive() && e != owner && !alreadyHit.contains(e.getUUID()) && !(e instanceof Player p && p.isSpectator()))
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
        if (next == null) {
            return false;
        }
        Vec3 dir = next.position().add(0, next.getBbHeight() / 2, 0).subtract(position()).normalize();
        setDeltaMovement(dir.scale(SPEED));
        age = Math.max(0, age - 8);
        hasImpulse = true;
        return true;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide || returning) {
            return;
        }
        if (kind != Kind.HAMMER && bounces < kind.ricochets) {
            // bounce off the surface
            Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.subtract(normal.scale(2 * v.dot(normal))));
            bounces++;
            level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
        } else {
            startReturn();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Kind", kind.name());
        tag.putFloat("Damage", damage);
        tag.putBoolean("GiveBack", giveBack);
        tag.putBoolean("Returning", returning);
        tag.putInt("Bounces", bounces);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kind = Kind.byName(tag.getString("Kind"));
        damage = tag.getFloat("Damage");
        giveBack = !tag.contains("GiveBack") || tag.getBoolean("GiveBack");
        returning = tag.getBoolean("Returning");
        bounces = tag.getInt("Bounces");
        noPhysics = returning;
    }
}
