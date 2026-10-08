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
import net.threetag.palladium.power.energybar.EnergyBar;

/** Helpers for entities that carry a symbiote (the {@code symbiote:symbiote} or {@code symbiote:apex} power). */
public final class SymbioteHost {
    public static final ResourceLocation BASE = HeroesMod.symbiote("symbiote");
    public static final ResourceLocation APEX = HeroesMod.symbiote("apex");
    public static final String HUNGER = "hunger";

    private SymbioteHost() {
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
