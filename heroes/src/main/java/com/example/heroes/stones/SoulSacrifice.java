package com.example.heroes.stones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * The Soul Stone's price: "a soul for a soul". The Soul Urn only gives up its stone if a loved one is given in exchange,
 * a pet you tamed or a villager standing next to the urn.
 */
final class SoulSacrifice {
    private static final double REACH = 6.0;

    private SoulSacrifice() {
    }

    /** Takes the sacrifice (and returns true), or tells the player what the urn wants (and returns false). */
    static boolean pay(ServerLevel level, BlockPos urn, Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        AABB area = new AABB(urn).inflate(REACH);
        LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && (e instanceof Villager
                        || (e instanceof TamableAnimal pet && pet.isTame() && pet.isOwnedBy(player))))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(urn.getX() + 0.5, urn.getY() + 0.5, urn.getZ() + 0.5))).orElse(null);
        if (victim == null) {
            player.displayClientMessage(Component.literal("The urn whispers: \"A soul for a soul.\" Bring a pet you love, or a villager, within " + (int) REACH + " blocks."), false);
            return false;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 40, 0.4, 0.8, 0.4, 0.08);
        level.playSound(null, urn, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 2.0F, 0.5F);
        victim.kill();
        player.displayClientMessage(Component.literal("The urn takes " + (victim.hasCustomName() ? victim.getCustomName().getString() : "a soul") + " and releases the stone."), false);
        return true;
    }
}
