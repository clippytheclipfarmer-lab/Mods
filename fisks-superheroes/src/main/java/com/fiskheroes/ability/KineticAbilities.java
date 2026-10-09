package com.fiskheroes.ability;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.energybar.EnergyBar;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;
import org.joml.Vector3f;

/** The Black Panther suit: it soaks up hits into an energy bar and releases them as a shockwave. */
public final class KineticAbilities {
    private KineticAbilities() {
    }

    /**
     * Passive marker: while enabled, damage taken is soaked into the power's energy bar instead of hurting, as long as the
     * bar has room (see {@link com.fiskheroes.FiskEvents}).
     */
    public static class Absorb extends Ability {
        public static final PalladiumProperty<String> BAR = new StringProperty("energy_bar").configurable("Name of the energy bar that stores the energy");
        public static final PalladiumProperty<Float> EFFICIENCY = new FloatProperty("efficiency").configurable("Energy gained per point of damage soaked");

        public Absorb() {
            this.withProperty(ICON, new ItemIcon(Items.AMETHYST_SHARD));
            this.withProperty(BAR, "kinetic");
            this.withProperty(EFFICIENCY, 2.5F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Soaks up damage into an energy bar.";
        }
    }

    /** Action: spends the stored energy in a shockwave around the holder. */
    public static class Release extends Ability {
        public static final PalladiumProperty<String> BAR = new StringProperty("energy_bar").configurable("Name of the energy bar to spend");
        public static final PalladiumProperty<Float> DAMAGE_PER_ENERGY = new FloatProperty("damage_per_energy").configurable("Damage per unit of stored energy");

        public Release() {
            this.withProperty(ICON, new ItemIcon(Items.FIREWORK_STAR));
            this.withProperty(BAR, "kinetic");
            this.withProperty(DAMAGE_PER_ENERGY, 0.35F);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            EnergyBar bar = holder.getEnergyBars().get(entry.getProperty(BAR));
            int energy = bar == null ? 0 : bar.get();
            if (energy < 10) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.fiskheroes.not_enough_energy"), true);
                }
                return;
            }
            bar.set(0);
            double radius = 3.5 + energy / 18.0;
            float damage = energy * entry.getProperty(DAMAGE_PER_ENERGY);
            Vec3 origin = entity.position();
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius), e -> e != entity && e.isAlive())) {
                double dist = target.position().distanceTo(origin);
                if (dist > radius) {
                    continue;
                }
                float scaled = (float) (damage * (1.0 - dist / radius * 0.5));
                target.hurt(com.fiskheroes.FiskUtil.attack(level, entity), scaled);
                Vec3 away = target.position().subtract(origin).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                target.push(away.x * 1.2, 0.5, away.z * 1.2);
                target.hurtMarked = true;
            }
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.71F, 0.48F, 1.0F), 1.6F);
            for (int i = 0; i < 70; i++) {
                double angle = Math.PI * 2 * i / 70.0;
                for (double r = 1; r <= radius; r += radius / 3) {
                    level.sendParticles(dust, origin.x + Math.cos(angle) * r, origin.y + 0.2, origin.z + Math.sin(angle) * r, 1, 0.05, 0.05, 0.05, 0);
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.5, origin.z, 2, radius / 4, 0.2, radius / 4, 0);
            level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.4F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Releases the stored kinetic energy as a shockwave.";
        }
    }
}
