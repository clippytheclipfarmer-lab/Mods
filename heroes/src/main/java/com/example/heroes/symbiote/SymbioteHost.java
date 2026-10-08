package com.example.heroes.symbiote;

import com.example.heroes.HeroesMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.PowerManager;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.SuperpowerUtil;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladium.power.energybar.EnergyBar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Helpers for entities that carry a symbiote (the {@code symbiote:symbiote} or {@code symbiote:apex} power). */
public final class SymbioteHost {
    public static final ResourceLocation BASE = HeroesMod.symbiote("symbiote");
    public static final ResourceLocation APEX = HeroesMod.symbiote("apex");
    public static final String HUNGER = "hunger";
    public static final int FULL = 100;

    public static final String SUIT = "suit";
    /** Symbiote armor (energy bar, per mille of the host's maximum health) and the symbiote's own life (percent of 5 hearts). */
    public static final String ARMOR = "armor";
    public static final String CORE = "core";
    public static final int ARMOR_MAX = 1000;
    public static final int CORE_MAX = 100;
    public static final float CORE_HEALTH = 10.0F; // 5 hearts
    /** A symbiote that was driven off cannot bond again for 10 minutes. */
    public static final int BLOB_LOCK_TICKS = 12000;
    private static final Set<UUID> PENDING_INIT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RELEASE_KEY = new ConcurrentHashMap<>();

    private SymbioteHost() {
    }

    /** Is the black suit currently covering the entity? (Works on both sides: Palladium syncs the ability state.) */
    public static boolean isSuited(LivingEntity entity) {
        return AbilityUtil.isEnabled(entity, BASE, SUIT) || AbilityUtil.isEnabled(entity, APEX, SUIT);
    }

    /** Makes the suit spread by itself, as if the host had pressed the suit key. */
    public static void requestSuit(LivingEntity entity) {
        if (entity.level().isClientSide || isSuited(entity)) {
            return;
        }
        for (ResourceLocation id : new ResourceLocation[]{APEX, BASE}) {
            AbilityInstance suit = AbilityUtil.getInstance(entity, id, SUIT);
            if (suit != null) {
                suit.cooldown = 0;
                suit.keyPressed(entity, true);
                RELEASE_KEY.put(entity.getUUID(), true);
                return;
            }
        }
    }

    /** Lets go of the simulated key press one tick after {@link #requestSuit}. */
    public static void releaseSimulatedKeys(net.minecraft.server.MinecraftServer server) {
        if (RELEASE_KEY.isEmpty()) {
            return;
        }
        for (UUID id : RELEASE_KEY.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                for (ResourceLocation power : new ResourceLocation[]{APEX, BASE}) {
                    AbilityInstance suit = AbilityUtil.getInstance(player, power, SUIT);
                    if (suit != null) {
                        suit.keyPressed(player, false);
                    }
                }
            }
        }
        RELEASE_KEY.clear();
    }

    public static boolean isHost(LivingEntity entity) {
        return PowerUtil.hasPower(entity, BASE) || PowerUtil.hasPower(entity, APEX);
    }

    public static boolean isApex(LivingEntity entity) {
        return PowerUtil.hasPower(entity, APEX);
    }

    public static void bond(ServerPlayer player, boolean perfect) {
        SuperpowerUtil.addSuperpower(player, perfect ? APEX : BASE);
        PENDING_INIT.add(player.getUUID());
        player.sendSystemMessage(Component.literal(perfect
                ? "The symbiote and you are one. A perfect match - it has found its true host."
                : "The symbiote bonds with you. Feed it, or it will take what it wants."));
    }

    public static void release(LivingEntity entity) {
        entity.removeTag(CONTROL_TAG);
        SuperpowerUtil.removeSuperpower(entity, BASE);
        SuperpowerUtil.removeSuperpower(entity, APEX);
    }

    private static EnergyBar hungerBar(LivingEntity entity) {
        return bar(entity, HUNGER);
    }

    public static EnergyBar bar(LivingEntity entity, String name) {
        var handler = PowerManager.getPowerHandler(entity).orElse(null);
        if (handler == null) {
            return null;
        }
        for (ResourceLocation id : new ResourceLocation[]{APEX, BASE}) {
            IPowerHolder holder = handler.getPowerHolders().get(id);
            if (holder != null && holder.getEnergyBars().get(name) != null) {
                return holder.getEnergyBars().get(name);
            }
        }
        return null;
    }

    /** The symbiote's own hunger bar: {@link #FULL} = sated, 0 = starving. -1 for entities without a symbiote. */
    public static int satiation(LivingEntity entity) {
        EnergyBar bar = hungerBar(entity);
        return bar == null ? -1 : bar.get();
    }

    public static void feed(LivingEntity entity, int amount) {
        EnergyBar bar = hungerBar(entity);
        if (bar != null) {
            bar.set(Math.max(0, Math.min(FULL, bar.get() + amount)));
        }
    }

    public static boolean isStarving(LivingEntity entity) {
        EnergyBar bar = hungerBar(entity);
        return bar != null && bar.get() <= 0;
    }

    /**
     * Called when a host eats: while the symbiote is not full, it eats first - the whole item goes to its food bar
     * (meat counts 4x its food value, anything else 2x), and the host gets neither food nor any bad effect (the symbiote
     * cannot get food poisoning). Returns false when the symbiote is full, so the host eats normally.
     */
    public static boolean eatFirst(LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        int food = satiation(entity);
        var props = stack.getItem().getFoodProperties();
        if (food < 0 || food >= FULL || props == null) {
            return false;
        }
        feed(entity, props.getNutrition() * (props.isMeat() ? 4 : 2));
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("\u00a77The symbiote takes it first."), true);
        }
        return true;
    }

    /** While this tag is on the host, the symbiote is in control of the body until it has eaten its fill. */
    public static final String CONTROL_TAG = "heroes_symbiote_control";

    public static boolean isControlled(LivingEntity entity) {
        return entity.getTags().contains(CONTROL_TAG);
    }

    // ------------------------------------------------------------------ the symbiote's own health

    /** Runs every server tick: lets go of simulated keys and fills the armor and core bars of freshly bonded hosts. */
    public static void tickServer(net.minecraft.server.MinecraftServer server) {
        releaseSimulatedKeys(server);
        for (UUID id : PENDING_INIT) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                PENDING_INIT.remove(id);
                continue;
            }
            EnergyBar armor = bar(player, ARMOR);
            EnergyBar core = bar(player, CORE);
            EnergyBar hunger = bar(player, HUNGER);
            if (armor != null && core != null && hunger != null) {
                hunger.set(FULL);
                armor.set(ARMOR_MAX);
                core.set(CORE_MAX);
                PENDING_INIT.remove(id);
            }
        }
    }

    public static void markHit(LivingEntity entity) {
        LAST_HIT.put(entity.getUUID(), entity.level().getGameTime());
    }

    public static boolean recentlyHit(LivingEntity entity, int ticks) {
        Long at = LAST_HIT.get(entity.getUUID());
        return at != null && entity.level().getGameTime() - at < ticks;
    }

    /** The symbiote armor soaks up damage first while suited (it is as big as the host's health). Returns what is left for the host. */
    public static float absorb(LivingEntity entity, float amount) {
        EnergyBar armor = bar(entity, ARMOR);
        if (armor == null || armor.get() <= 0) {
            return amount;
        }
        float maxHp = Math.max(1.0F, entity.getMaxHealth());
        float availableHp = armor.get() / (float) ARMOR_MAX * maxHp;
        float absorbed = Math.min(amount, availableHp);
        int left = Math.max(0, Math.round((availableHp - absorbed) / maxHp * ARMOR_MAX));
        armor.set(left);
        if (left == 0 && entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("\u00a77The symbiote armor is torn away!"), true);
        }
        return amount - absorbed;
    }

    /** Damage to the symbiote itself (only fire and sonic attacks reach it). At zero it lets go of the host. */
    public static void damageCore(LivingEntity entity, float hp, boolean sonic) {
        EnergyBar core = bar(entity, CORE);
        if (core == null || entity.level().isClientSide || hp <= 0) {
            return;
        }
        int left = Math.max(0, core.get() - Math.round(hp / CORE_HEALTH * CORE_MAX));
        core.set(left);
        entity.level().playSound(null, entity.blockPosition(), sonic ? SoundEvents.BELL_BLOCK : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 1.2F, sonic ? 0.5F : 0.7F);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(sonic ? "\u00a7cThe symbiote shrieks at the sound!" : "\u00a7cThe symbiote burns!"), true);
        }
        if (left <= 0) {
            shed(entity);
        }
    }

    /** A sonic attack (a ringing bell, a sonic boom, a thunderclap) hurts every symbiote host in range. */
    public static void sonicHit(ServerLevel level, Vec3 origin, double radius, float hp) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(origin, origin).inflate(radius),
                e -> e.isAlive() && isHost(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (e.position().distanceTo(origin) <= radius) {
                markHit(e);
                damageCore(e, hp, true);
            }
        }
    }

    /** The symbiote's life is gone: it tears free of the host and becomes a blob that cannot bond for 10 minutes. */
    public static void shed(LivingEntity host) {
        if (!(host.level() instanceof ServerLevel level) || !isHost(host)) {
            return;
        }
        release(host);
        SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(level);
        if (blob != null) {
            blob.moveTo(host.getX(), host.getY(), host.getZ(), host.getYRot(), 0F);
            blob.setPersistenceRequired();
            blob.lockBonding(BLOB_LOCK_TICKS);
            level.addFreshEntity(blob);
        }
        level.playSound(null, host.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.5F, 0.6F);
        if (host instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("The wounded symbiote tears itself free of you and flees as a blob."));
        }
    }
}
