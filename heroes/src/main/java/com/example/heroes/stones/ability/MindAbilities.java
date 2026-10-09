package com.example.heroes.stones.ability;

import com.example.heroes.stones.StoneBoost;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

/** Mind Stone extras. */
public final class MindAbilities {
    /** Entity tag carried by every pacified mob (it is saved with the mob). */
    public static final String PACIFIED_TAG = "stones_pacified";

    private MindAbilities() {
    }

    /** Pacifies hostile mobs around the holder so they stop attacking players. Sneak while using it to undo it. */
    public static class Pacify extends StoneAbilities.Action {
        public Pacify() {
            super(Items.GOLDEN_CARROT);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            double radius = 24 * StoneBoost.general(entity);
            boolean undo = entity.isShiftKeyDown();
            int changed = 0;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius), m -> m instanceof Enemy && m.isAlive())) {
                if (undo) {
                    if (mob.removeTag(PACIFIED_TAG)) {
                        changed++;
                    }
                } else if (mob.addTag(PACIFIED_TAG)) {
                    mob.setTarget(null);
                    mob.setLastHurtByMob(null);
                    changed++;
                }
                level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.2F), 1.5F), mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 8, 0.3, 0.2, 0.3, 0);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, undo ? 0.6F : 1.4F);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(changed + (undo ? " mobs stirred to anger again." : " mobs pacified.")), true);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Pacifies nearby hostile mobs; sneak while using it to undo the effect.";
        }
    }

    /** Floods the holder's mind with every recipe in the game (once, when the stone is first held). */
    public static class UnlockRecipes extends StoneAbilities.Action {
        public UnlockRecipes() {
            super(Items.KNOWLEDGE_BOOK);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            if (entity instanceof ServerPlayer player) {
                player.awardRecipes(level.getServer().getRecipeManager().getRecipes());
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Unlocks every recipe for the holder when enabled.";
        }
    }
}
