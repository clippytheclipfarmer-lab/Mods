package com.example.heroes.symbiote.klyntar;

import com.example.heroes.common.HeroEffects;
import com.example.heroes.symbiote.SymbioteCreature;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Knull, god of the abyss and father of the symbiotes. A giant boss that awakens in the hive: it yanks players in with
 * tendrils, calls crawlers and brutes, stomps, and in its last phase plunges everyone into darkness.
 */
public class KnullEntity extends Zombie implements SymbioteCreature {
    public static final float SCALE = 2.8F;
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.0F, 0.04F), 2.0F);
    private static final float MAX_DAMAGE_PER_HIT = 60.0F;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal("Knull, God of the Abyss"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    public KnullEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 900.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(SCALE);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.74F * SCALE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, Math.min(amount, MAX_DAMAGE_PER_HIT));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    private int phase() {
        float f = getHealth() / getMaxHealth();
        return f > 0.6F ? 1 : f > 0.3F ? 2 : 3;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int phase = phase();
        if (tickCount % 4 == 0) {
            level.sendParticles(BLACK, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, getBbWidth() / 2, getBbHeight() / 3, getBbWidth() / 2, 0.01);
        }
        LivingEntity target = getTarget();

        // Tendril: yank a distant target into reach.
        if (target != null && tickCount % (phase == 1 ? 90 : 60) == 0) {
            double d = distanceTo(target);
            if (d > 7 && d < 32) {
                Vec3 pull = position().subtract(target.position()).normalize().scale(1.9);
                target.setDeltaMovement(pull.x, 0.5, pull.z);
                target.hurtMarked = true;
                Vec3 from = position().add(0, getBbHeight() * 0.6, 0), to = target.position().add(0, 1, 0);
                int steps = (int) (d * 3);
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = from.lerp(to, i / (double) steps);
                    level.sendParticles(BLACK, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
                }
                playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, 3.0F, 0.5F);
            }
        }
        // Summon minions.
        int interval = phase == 1 ? 300 : phase == 2 ? 200 : 120;
        if (target != null && tickCount % interval == 0 && KlyntarSystem.countNear(level, position(), 40) < 14) {
            KlyntarSystem.spawnAround(level, KlyntarEntities.SYMBIOTE_CRAWLER, position(), 3, 6);
            if (phase >= 2) {
                KlyntarSystem.spawnAround(level, KlyntarEntities.SYMBIOTE_BRUTE, position(), 1, 7);
            }
            playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.6F);
        }
        // Stomp.
        if (phase >= 2 && target != null && tickCount % 160 == 0) {
            HeroEffects.blast(level, this, position(), 11, 16.0F, 2.2, 0.9);
            HeroEffects.ring(level, position(), 11);
            HeroEffects.boom(level, position(), 0.5F);
        }
        // Last phase: darkness for everyone nearby, and Knull speeds up.
        if (phase == 3 && tickCount % 100 == 0) {
            for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(48))) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
            }
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1));
            level.playSound(null, blockPosition(), SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            KlyntarData data = KlyntarData.get(level.getServer());
            data.knullDefeated = true;
            data.setDirty();
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(Component.literal("Knull falls. The hive falls silent."));
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        spawnAtLocation(new ItemStack(Items.NETHER_STAR, 2));
        spawnAtLocation(new ItemStack(Items.NETHERITE_SCRAP, 8));
        spawnAtLocation(new ItemStack(Items.ECHO_SHARD, 4));
    }
}
