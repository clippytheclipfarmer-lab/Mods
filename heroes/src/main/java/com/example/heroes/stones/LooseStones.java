package com.example.heroes.stones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The rule of the stones: a stone loose in your inventory is a curse. Each has its own drawback; only the Power Stone
 * (see {@link PowerStonePulse}) can kill you. Socket a stone into a holder or keep it in its container to be safe.
 */
public final class LooseStones {
    private static final Map<UUID, Map<InfinityStone, Integer>> TIMERS = new HashMap<>();

    private LooseStones() {
    }

    public static Set<InfinityStone> carried(ServerPlayer player) {
        Set<InfinityStone> set = EnumSet.noneOf(InfinityStone.class);
        Inventory inv = player.getInventory();
        for (var list : List.of(inv.items, inv.offhand, inv.armor)) {
            for (ItemStack stack : list) {
                if (stack.getItem() instanceof StoneItems.StoneItem stone) {
                    set.add(stone.stone);
                }
            }
        }
        return set;
    }

    /** Called every tick for every player; does the work every five ticks. The Power Stone is handled by PowerStonePulse. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 5 != 0) {
            return;
        }
        UUID id = player.getUUID();
        if (player.isCreative() || player.isSpectator()) {
            TIMERS.remove(id);
            return;
        }
        Set<InfinityStone> carried = carried(player);
        Map<InfinityStone, Integer> timers = TIMERS.computeIfAbsent(id, k -> new EnumMap<>(InfinityStone.class));
        timers.keySet().removeIf(stone -> !carried.contains(stone));
        ServerLevel level = player.serverLevel();
        for (InfinityStone stone : carried) {
            if (stone == InfinityStone.POWER) {
                continue;
            }
            int t = timers.merge(stone, 5, Integer::sum);
            switch (stone) {
                case SPACE -> {
                    if (t >= 200) {
                        timers.put(stone, 0);
                        tearSideways(player, level);
                    }
                }
                case MIND -> {
                    if (t >= 300) {
                        timers.put(stone, 0);
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                        for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(16))) {
                            monster.setTarget(player);
                        }
                        player.displayClientMessage(Component.literal("Voices press on your mind..."), true);
                    }
                }
                case REALITY -> {
                    if (t >= 400) {
                        timers.put(stone, 0);
                        MobEffect[] bad = {MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.DIG_SLOWDOWN, MobEffects.MOVEMENT_SLOWDOWN,
                                MobEffects.WEAKNESS, MobEffects.GLOWING, MobEffects.CONFUSION};
                        MobEffect effect = bad[level.random.nextInt(bad.length)];
                        player.addEffect(new MobEffectInstance(effect, 200, effect == MobEffects.MOVEMENT_SLOWDOWN ? 2 : 0));
                        player.displayClientMessage(Component.literal("Reality warps around you..."), true);
                    }
                }
                case TIME -> {
                    if (t >= 500) {
                        timers.put(stone, 0);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.7, 0.4, 0.2);
                        player.displayClientMessage(Component.literal("Time drags like mud..."), true);
                    }
                }
                case SOUL -> {
                    if (t >= 60) {
                        timers.put(stone, 0);
                        hurtNonLethal(player, level, 2.0F);
                        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 0));
                        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Hurts the player but never takes the last 3 hearts' worth: only the Power Stone is allowed to kill. */
    static void hurtNonLethal(ServerPlayer player, ServerLevel level, float amount) {
        if (player.getHealth() > amount + 1.0F) {
            player.hurt(level.damageSources().magic(), amount);
        }
    }

    /** The loose Space Stone tears the carrier a few blocks sideways to the nearest safe spot. */
    private static void tearSideways(ServerPlayer player, ServerLevel level) {
        if (level.dimension().location().getPath().equals("space")) {
            return; // nothing to stand on out there
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 5 + level.random.nextDouble() * 5;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = player.getBlockY() + 5; y >= player.getBlockY() - 6; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                        && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.below()).isEmpty()) {
                    level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.5);
                    player.teleportTo(x + 0.5, y, z + 0.5);
                    player.fallDistance = 0;
                    level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.5);
                    level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
                    player.displayClientMessage(Component.literal("Space tears sideways around you!"), true);
                    return;
                }
            }
        }
    }

    public static void forget(UUID player) {
        TIMERS.remove(player);
    }

    public static void clear() {
        TIMERS.clear();
    }
}
