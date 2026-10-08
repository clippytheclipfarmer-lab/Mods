package com.example.heroes.symbiote.ability;

import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import org.joml.Vector3f;

import java.util.Comparator;

/** Eats the weakened creature in front of you: heals the host and feeds the symbiote. Stronger ones are just bitten. Use with an 'action' condition. */
public class ConsumeAbility extends Ability {
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.03F), 1.4F);

    public ConsumeAbility() {
        this.withProperty(ICON, new ItemIcon(Items.ROTTEN_FLESH));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = entity.getViewVector(1F);
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(3.5),
                        e -> e != entity && e.isAlive() && !(e instanceof Player) && !SymbioteHost.isHost(e)
                                && e.position().subtract(entity.position()).normalize().dot(look) > 0.4)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(entity))).orElse(null);
        if (target == null) {
            return;
        }
        level.sendParticles(BLACK, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 30, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 0.6F);
        boolean weak = target.getHealth() <= target.getMaxHealth() * 0.4F || target.getHealth() <= 12.0F;
        if (weak) {
            target.hurt(level.damageSources().mobAttack(entity), 1000.0F);
            entity.heal(8.0F * (float) StoneBoost.mult(entity, InfinityStone.SOUL, 2.0));
            SymbioteHost.addHunger(entity, -40);
        } else {
            target.hurt(level.damageSources().mobAttack(entity), 8.0F);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Consume a weakened creature to heal and feed the symbiote.";
    }
}
