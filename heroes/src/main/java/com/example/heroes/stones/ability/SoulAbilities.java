package com.example.heroes.stones.ability;

import com.example.heroes.stones.StoneBoost;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Soul Stone: the army of the dead. */
public final class SoulAbilities {
    /** Tag on every soul soldier; the owner's id follows in a second tag, see {@link #OWNER_PREFIX}. */
    public static final String ARMY_TAG = "stones_army";
    public static final String OWNER_PREFIX = "stones_owner_";
    /** How long a soldier stays on this side, in ticks. */
    public static final int LIFETIME = 6000;

    private SoulAbilities() {
    }

    /** Summons seven loyal zombies that fight whatever the holder fights. */
    public static class SummonArmy extends StoneAbilities.Action {
        private static final int SOLDIERS = 7;

        public SummonArmy() {
            super(Items.ZOMBIE_HEAD);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            int count = (int) Math.round(SOLDIERS * StoneBoost.general(entity));
            for (int i = 0; i < count; i++) {
                Zombie zombie = EntityType.ZOMBIE.create(level);
                if (zombie == null) {
                    continue;
                }
                double angle = Math.PI * 2 * i / count;
                zombie.moveTo(entity.getX() + Math.cos(angle) * 3, entity.getY(), entity.getZ() + Math.sin(angle) * 3, (float) Math.toDegrees(angle) + 90F, 0F);
                zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
                zombie.setBaby(false);
                zombie.setCustomName(Component.literal("Soul Soldier"));
                zombie.setPersistenceRequired();
                zombie.addTag(ARMY_TAG);
                zombie.addTag(OWNER_PREFIX + entity.getUUID());
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LIFETIME, 0, true, false));
                zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, LIFETIME, 1, true, false));
                level.addFreshEntity(zombie);
                level.sendParticles(ParticleTypes.SOUL, zombie.getX(), zombie.getY() + 1, zombie.getZ(), 16, 0.3, 0.6, 0.3, 0.05);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 2.0F, 0.6F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Summons seven zombies that obey the holder and fight for them for five minutes.";
        }
    }
}
