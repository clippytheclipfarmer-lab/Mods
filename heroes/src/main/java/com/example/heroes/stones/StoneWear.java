package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.threetag.palladium.compat.curiostinkets.CuriosTrinketsUtil;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.SuperpowerUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Works out which stones a player is currently using (stones socketed in worn holders; holders go in accessory
 * slots - or count while held in a hand if no accessory mod is installed) and keeps their Palladium powers in sync.
 */
public final class StoneWear {
    /** Accessory slots (Trinkets/Curios) that holders (and the one-stone Stone Ring) can go in. Loose stones cannot be worn: they must be socketed. */
    private static final String[] SLOTS = {"hand/ring", "offhand/ring", "hand/glove", "offhand/glove", "chest/necklace"};

    private static final Map<UUID, Set<InfinityStone>> ACTIVE = new HashMap<>();
    private static final ResourceLocation SNAP = HeroesMod.stones("snap");

    private StoneWear() {
    }

    public static ResourceLocation powerId(InfinityStone stone) {
        return HeroesMod.stones(stone.name().toLowerCase());
    }

    /** Stones currently active for this entity (empty if it is not a player we track). */
    public static Set<InfinityStone> active(LivingEntity entity) {
        Set<InfinityStone> set = ACTIVE.get(entity.getUUID());
        return set == null ? Set.of() : set;
    }

    public static List<ItemStack> wornStacks(LivingEntity entity) {
        List<ItemStack> worn = new ArrayList<>();
        var util = CuriosTrinketsUtil.getInstance();
        if (util.isTrinkets() || util.isCurios()) {
            for (String slot : SLOTS) {
                worn.addAll(util.getItemsInSlot(entity, slot));
            }
        } else {
            // No accessory mod: holders count while in either hand.
            worn.add(entity.getMainHandItem());
            worn.add(entity.getOffhandItem());
        }
        return worn;
    }

    private static Set<InfinityStone> compute(LivingEntity entity) {
        Set<InfinityStone> set = EnumSet.noneOf(InfinityStone.class);
        // A filled stone container held in either hand lends its stone's powers.
        for (ItemStack stack : new ItemStack[]{entity.getMainHandItem(), entity.getOffhandItem()}) {
            InfinityStone held = StoneContainers.heldStone(stack);
            if (held != null) {
                set.add(held);
            }
        }
        for (ItemStack stack : wornStacks(entity)) {
            if (stack.getItem() instanceof HolderItem) {
                set.addAll(HolderItem.stones(stack));
            }
        }
        return set;
    }

    /** Whether a worn holder has all six stones in it (the full set). */
    private static boolean fullSet(LivingEntity entity) {
        for (ItemStack stack : wornStacks(entity)) {
            if (stack.getItem() instanceof HolderItem && HolderItem.stones(stack).size() == InfinityStone.values().length) {
                return true;
            }
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % 10 != 0) {
                continue;
            }
            Set<InfinityStone> now = compute(player);
            ACTIVE.put(player.getUUID(), now);
            for (InfinityStone stone : InfinityStone.values()) {
                ResourceLocation id = powerId(stone);
                boolean has = PowerUtil.hasPower(player, id);
                if (now.contains(stone) && !has) {
                    SuperpowerUtil.addSuperpower(player, id);
                } else if (!now.contains(stone) && has) {
                    SuperpowerUtil.removeSuperpower(player, id);
                }
            }
            boolean snap = fullSet(player);
            if (snap != PowerUtil.hasPower(player, SNAP)) {
                if (snap) {
                    SuperpowerUtil.addSuperpower(player, SNAP);
                } else {
                    SuperpowerUtil.removeSuperpower(player, SNAP);
                }
            }
        }
    }

    public static void forget(UUID player) {
        ACTIVE.remove(player);
    }
}
