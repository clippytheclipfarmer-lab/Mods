package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive. The "hunger" energy bar fills over time; kills and consuming feed it. When it is full the symbiote takes
 * control for ten seconds and attacks whatever is closest, then settles for a while.
 */
public class HungerAbility extends Ability {
    private static final int TAKEOVER_TICKS = 200;
    private static final Map<UUID, Integer> TAKEOVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> FED_DURING = new ConcurrentHashMap<>();

    public HungerAbility() {
        this.withProperty(ICON, new ItemIcon(Items.BONE));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = entity.getUUID();
        Integer left = TAKEOVER.get(id);
        if (left == null) {
            if (SymbioteHost.isStarving(entity)) {
                TAKEOVER.put(id, TAKEOVER_TICKS);
                FED_DURING.put(id, false);
                entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TAKEOVER_TICKS, 0, false, false));
                if (entity instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("The symbiote is starving - it takes control!"));
                }
            }
            return;
        }

        // Taken over: lunge at the nearest creature and attack it.
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(16),
                        e -> e != entity && e.isAlive() && !e.isSpectator() && !SymbioteHost.isHost(e)
                                && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(entity))).orElse(null);
        if (target != null) {
            Vec3 dir = target.position().subtract(entity.position());
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            if (flat.lengthSqr() > 1.0E-4) {
                flat = flat.normalize().scale(0.18);
                entity.setDeltaMovement(entity.getDeltaMovement().add(flat.x, 0, flat.z));
                entity.hurtMarked = true;
            }
            if (entity.distanceToSqr(target) < 9.0 && left % 8 == 0 && entity instanceof Player player) {
                player.attack(target);
                FED_DURING.put(id, true);
            }
        }
        left--;
        if (left <= 0) {
            TAKEOVER.remove(id);
            SymbioteHost.setHunger(entity, FED_DURING.remove(id) ? 50 : 75);
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("The symbiote lets go, for now."));
            }
        } else {
            TAKEOVER.put(id, left);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Symbiote hunger and takeover.";
    }
}
