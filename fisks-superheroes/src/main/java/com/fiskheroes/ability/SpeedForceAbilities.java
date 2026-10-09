package com.fiskheroes.ability;

import com.fiskheroes.FiskUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The Flash's speed: toggle it on, scroll to pick the level (1 to 5). Faster levels bowl over whatever you run into. */
public final class SpeedForceAbilities {
    private static final UUID MODIFIER_ID = UUID.fromString("b3a4e7f0-5c1d-4e6a-8f2b-3d9c7a6e1f10");
    private static final Map<UUID, Integer> LEVEL = new ConcurrentHashMap<>();
    private static final int MAX_LEVEL = 5;

    private SpeedForceAbilities() {
    }

    public static int level(LivingEntity entity) {
        return LEVEL.getOrDefault(entity.getUUID(), 0);
    }

    public static class SpeedForce extends Ability {
        public SpeedForce() {
            this.withProperty(ICON, new ItemIcon(Items.SUGAR));
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled) {
                LEVEL.putIfAbsent(entity.getUUID(), 2);
            }
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Integer level = LEVEL.get(entity.getUUID());
            if (!enabled || level == null) {
                return;
            }
            entity.setMaxUpStep(1.1F);
            if (entity.level().isClientSide) {
                return;
            }
            AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            double amount = 1.1 * level;
            AttributeModifier current = speed == null ? null : speed.getModifier(MODIFIER_ID);
            if (speed != null && (current == null || current.getAmount() != amount)) {
                speed.removeModifier(MODIFIER_ID);
                speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, "Speed Force", amount, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (!(entity.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            entity.fallDistance = 0;
            Vec3 motion = entity.getDeltaMovement();
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontal > 0.15 && entity.tickCount % 2 == 0) {
                Vec3 behind = entity.position().subtract(motion.x * 1.5, 0, motion.z * 1.5);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, behind.x, behind.y + 0.9, behind.z, 4 + level * 2, 0.35, 0.8, 0.35, 0.05);
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.2F), behind.x, behind.y + 0.9, behind.z, 3, 0.3, 0.7, 0.3, 0);
            }
            // At speed you bowl over what is in front of you.
            if (level >= 3 && horizontal > 0.5 && entity.isSprinting()) {
                Vec3 dir = new Vec3(motion.x, 0, motion.z).normalize();
                for (LivingEntity other : serverLevel.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.6).move(dir.scale(1.0)), e -> e != entity && e.isAlive())) {
                    other.hurt(FiskUtil.attack(serverLevel, entity), level * 2.5F);
                    other.push(dir.x * 1.5, 0.4, dir.z * 1.5);
                    other.hurtMarked = true;
                }
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            LEVEL.remove(entity.getUUID());
            entity.setMaxUpStep(0.6F);
            if (!entity.level().isClientSide) {
                AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.removeModifier(MODIFIER_ID);
                }
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Super speed with five levels, set with the step ability.";
        }
    }

    public static class SpeedStep extends Ability {
        public static final PalladiumProperty<Integer> DELTA = new IntegerProperty("delta").configurable("+1 faster, -1 slower");

        public SpeedStep() {
            this.withProperty(ICON, new ItemIcon(Items.SUGAR));
            this.withProperty(DELTA, 1);
            this.withProperty(HIDDEN_IN_GUI, true);
            this.withProperty(HIDDEN_IN_BAR, true);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level) || !LEVEL.containsKey(entity.getUUID())) {
                return;
            }
            int next = Math.max(1, Math.min(MAX_LEVEL, LEVEL.get(entity.getUUID()) + entry.getProperty(DELTA)));
            LEVEL.put(entity.getUUID(), next);
            level.playSound(null, entity.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.8F, 0.8F + next * 0.15F);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.fiskheroes.speed_level", next, MAX_LEVEL), true);
            }
        }
    }
}
