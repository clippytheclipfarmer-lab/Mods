package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * The symbiote's own hunger bar (full = sated, empty = starving). It drains slowly with time and quickly while the suit
 * is on. The symbiote grumbles as it gets hungry; when the bar is empty it <b>takes control of the body</b> and goes
 * hunting - prey, and food lying around - until it has eaten its fill, then it gives the body back.
 */
public class HungerAbility extends Ability {
    private static final Map<UUID, Integer> STAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> HEADING = new ConcurrentHashMap<>();

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
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }
        int food = SymbioteHost.satiation(entity);
        if (food < 0) {
            return;
        }
        UUID id = entity.getUUID();

        if (!SymbioteHost.isControlled(entity)) {
            // Slow drain (a perfect host's symbiote is more efficient).
            if (entity.tickCount % (SymbioteHost.isApex(entity) ? 400 : 200) == 0) {
                SymbioteHost.feed(entity, -1);
                food = SymbioteHost.satiation(entity);
            }
            grumble(entity, level, id, food);
            if (food <= 0) {
                entity.addTag(SymbioteHost.CONTROL_TAG);
                SymbioteHost.requestSuit(entity);
                level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 0.7F);
                if (entity instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("The symbiote is starving - it takes control of your body to feed!"));
                }
            }
            return;
        }

        // In control: it keeps the suit on, hunts, and eats until the bar is full again.
        if (food >= SymbioteHost.FULL) {
            entity.removeTag(SymbioteHost.CONTROL_TAG);
            STAGE.remove(id);
            HEADING.remove(id);
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Fed at last, the symbiote lets go of you."));
            }
            return;
        }
        if (entity instanceof ServerPlayer player && entity.tickCount % 20 == 0) {
            player.displayClientMessage(Component.literal("\u00a74The symbiote is in control... feeding."), true);
        }
        if (entity.tickCount % 40 == 0) {
            SymbioteHost.requestSuit(entity);
        }
        hunt(entity, level, id);
    }

    /** Escalating complaints as the bar empties. */
    private static void grumble(LivingEntity entity, ServerLevel level, UUID id, int food) {
        int stage = food <= 10 ? 2 : food <= 30 ? 1 : 0;
        int before = STAGE.getOrDefault(id, 0);
        STAGE.put(id, stage);
        if (stage > before && entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(stage == 2 ? "\u00a74The symbiote is ravenous. Feed it, or it will feed itself."
                    : "\u00a77The symbiote is getting hungry..."), true);
            level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_AMBIENT, SoundSource.PLAYERS, 0.8F, stage == 2 ? 0.6F : 0.9F);
        }
    }

    private static void hunt(LivingEntity entity, ServerLevel level, UUID id) {
        // Food lying around is eaten on the spot.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1.5), i -> i.getItem().isEdible())) {
            var props = item.getItem().getItem().getFoodProperties();
            if (props != null) {
                SymbioteHost.feed(entity, props.getNutrition() * 4);
                item.getItem().shrink(1);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 0.6F);
                return;
            }
        }
        // Prey: animals and monsters first, people only when nothing else is around.
        var prey = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(28),
                e -> e != entity && e.isAlive() && !e.isSpectator() && !SymbioteHost.isHost(e)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        LivingEntity target = prey.stream().filter(e -> !(e instanceof Player))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(entity))).orElse(null);
        if (target == null) {
            target = prey.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(entity))).orElse(null);
        }
        ItemEntity foodItem = null;
        if (target == null) {
            foodItem = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(20), i -> i.getItem().isEdible())
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(entity))).orElse(null);
        }
        Vec3 dir;
        if (target != null) {
            dir = target.position().subtract(entity.position());
        } else if (foodItem != null) {
            dir = foodItem.position().subtract(entity.position());
        } else {
            // Nothing to eat in sight: prowl in a wandering direction.
            if (entity.tickCount % 60 == 0 || !HEADING.containsKey(id)) {
                HEADING.put(id, entity.getRandom().nextFloat() * 6.2831855F);
            }
            float h = HEADING.get(id);
            dir = new Vec3(Math.cos(h), 0, Math.sin(h));
        }
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() > 1.0E-4) {
            flat = flat.normalize().scale(0.22);
            Vec3 motion = entity.getDeltaMovement();
            double up = entity.horizontalCollision && entity.onGround() ? 0.42 : motion.y;
            entity.setDeltaMovement(motion.x * 0.4 + flat.x, up, motion.z * 0.4 + flat.z);
            entity.hurtMarked = true;
        }
        if (target != null && entity.distanceToSqr(target) < 9.0 && entity.tickCount % 8 == 0) {
            if (entity instanceof Player player) {
                player.attack(target);
            } else {
                target.hurt(level.damageSources().mobAttack(entity), 6.0F);
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Symbiote hunger and takeover.";
    }
}
