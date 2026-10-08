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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Helpers for entities that carry a symbiote (the {@code symbiote:symbiote} or {@code symbiote:apex} power). */
public final class SymbioteHost {
    public static final ResourceLocation BASE = HeroesMod.symbiote("symbiote");
    public static final ResourceLocation APEX = HeroesMod.symbiote("apex");
    public static final String HUNGER = "hunger";

    public static final String SUIT = "suit";
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
        player.sendSystemMessage(Component.literal(perfect
                ? "The symbiote and you are one. A perfect match - it has found its true host."
                : "The symbiote bonds with you. Feed it, or it will take what it wants."));
    }

    public static void release(LivingEntity entity) {
        SuperpowerUtil.removeSuperpower(entity, BASE);
        SuperpowerUtil.removeSuperpower(entity, APEX);
    }

    private static EnergyBar hungerBar(LivingEntity entity) {
        var handler = PowerManager.getPowerHandler(entity).orElse(null);
        if (handler == null) {
            return null;
        }
        for (ResourceLocation id : new ResourceLocation[]{APEX, BASE}) {
            IPowerHolder holder = handler.getPowerHolders().get(id);
            if (holder != null && holder.getEnergyBars().get(HUNGER) != null) {
                return holder.getEnergyBars().get(HUNGER);
            }
        }
        return null;
    }

    /** 0 = fed, 100 = starving. Does nothing for entities without a symbiote. */
    public static void addHunger(LivingEntity entity, int delta) {
        EnergyBar bar = hungerBar(entity);
        if (bar != null) {
            bar.add(delta);
        }
    }

    public static void setHunger(LivingEntity entity, int value) {
        EnergyBar bar = hungerBar(entity);
        if (bar != null) {
            bar.set(value);
        }
    }

    public static boolean isStarving(LivingEntity entity) {
        EnergyBar bar = hungerBar(entity);
        return bar != null && bar.get() >= bar.getMax();
    }
}
