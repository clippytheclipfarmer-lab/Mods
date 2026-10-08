package com.example.heroes.hulk.ability;

import com.example.heroes.hulk.HulkRage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immortal Hulk: instead of dying, he revives at partial health in a rage. Marker ability, checked from the
 * death event in {@link com.example.heroes.hulk.HulkHero}; has a long cooldown.
 */
public class ImmortalityAbility extends Ability {
    public static final PalladiumProperty<Integer> COOLDOWN_TICKS = new IntegerProperty("revive_cooldown")
            .configurable("Ticks before Hulk can cheat death again");

    private static final Map<UUID, Long> LAST_REVIVE = new ConcurrentHashMap<>();

    public ImmortalityAbility() {
        this.withProperty(ICON, new ItemIcon(Items.TOTEM_OF_UNDYING));
        this.withProperty(COOLDOWN_TICKS, 6000);
    }

    /** Tries to revive the entity instead of letting it die. Returns true if death was cancelled. */
    public static boolean tryRevive(LivingEntity entity, AbilityInstance entry) {
        long now = entity.level().getGameTime();
        Long last = LAST_REVIVE.get(entity.getUUID());
        if (last != null && now - last < entry.getProperty(COOLDOWN_TICKS)) {
            return false;
        }
        LAST_REVIVE.put(entity.getUUID(), now);
        entity.setHealth(Math.max(1F, entity.getMaxHealth() * 0.4F));
        entity.removeAllEffects();
        entity.clearFire();
        HulkRage.add(entity, 100);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1, entity.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5F, 0.8F);
        }
        return true;
    }

    @Override
    public String getDocumentationDescription() {
        return "Revives the entity instead of dying, with a cooldown. Passive.";
    }
}
