package com.example.heroes.hulk.ability;

import com.example.heroes.hulk.HulkRage;
import com.example.heroes.hulk.NukeSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.BooleanProperty;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive, no button: if rage stays maxed out for {@code hold_ticks}, Hulk detonates in a gamma blast that wipes
 * out everything around him. Rare by design - it needs sustained, continuous full rage.
 */
public class GammaNukeAbility extends Ability {
    public static final PalladiumProperty<Integer> HOLD_TICKS = new IntegerProperty("hold_ticks").configurable("Ticks rage must stay at 100% before detonation");
    public static final PalladiumProperty<Integer> RADIUS = new IntegerProperty("radius").configurable("Blast radius in blocks");
    public static final PalladiumProperty<Float> DAMAGE = new FloatProperty("damage").configurable("Damage to living things at the center (falls off with distance)");
    public static final PalladiumProperty<Boolean> BLOCK_DAMAGE = new BooleanProperty("block_damage").configurable("Whether the blast destroys terrain (also needs the mobGriefing gamerule)");
    public static final PalladiumProperty<Integer> COOLDOWN = new IntegerProperty("cooldown").configurable("Ticks before it can happen again");

    /** Consecutive ticks at full rage, and the game time of the last detonation (not persisted across restarts). */
    private static final Map<UUID, Integer> FULL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_BLAST = new ConcurrentHashMap<>();

    public GammaNukeAbility() {
        this.withProperty(ICON, new ItemIcon(Items.TNT));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
        this.withProperty(HOLD_TICKS, 200);
        this.withProperty(RADIUS, 100);
        this.withProperty(DAMAGE, 400F);
        this.withProperty(BLOCK_DAMAGE, true);
        this.withProperty(COOLDOWN, 24000);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = entity.getUUID();
        Long last = LAST_BLAST.get(id);
        boolean onCooldown = last != null && level.getGameTime() - last < entry.getProperty(COOLDOWN);
        if (!enabled || onCooldown || !HulkRage.isFull(entity)) {
            FULL_TICKS.remove(id);
            return;
        }

        int ticks = FULL_TICKS.merge(id, 1, Integer::sum);
        int hold = entry.getProperty(HOLD_TICKS);
        // Build-up: gamma aura, rumbling, and a warning.
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                3 + ticks / 20, entity.getBbWidth(), entity.getBbHeight() / 2, entity.getBbWidth(), 0.1);
        if (ticks % 20 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
        }
        if (ticks == 1 && entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("The gamma is overflowing..."), true);
        }
        if (ticks < hold) {
            return;
        }

        FULL_TICKS.remove(id);
        LAST_BLAST.put(id, level.getGameTime());
        HulkRage.set(entity, 0);
        NukeSystem.detonate(level, entity, entry.getProperty(RADIUS), entry.getProperty(DAMAGE), entry.getProperty(BLOCK_DAMAGE));
        // Spent: shaky and slow for a while afterwards.
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
    }

    @Override
    public String getDocumentationDescription() {
        return "Passive gamma detonation when rage is maxed for a sustained time. No button.";
    }
}
