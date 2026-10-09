package com.example.heroes.stones;

import com.example.heroes.stones.ability.MindAbilities;
import com.example.heroes.stones.ability.SoulAbilities;
import com.example.heroes.stones.ability.SpaceAbilities;
import com.example.heroes.stones.ability.TimeAbilities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** The world-side rules behind the stone abilities: damage rules, pacified mobs and the soul army. */
final class StoneSystems {
    private static final double SCAN_RADIUS = 64;

    private StoneSystems() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> allowDamage(entity, source));
        // A frozen entity that was saved mid-freeze (server stopped, chunk unloaded) must not stay frozen forever.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getTags().contains(TimeAbilities.FROZEN_TAG) && !TimeAbilities.isHeld(entity.getUUID())) {
                TimeAbilities.thaw(entity);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(TimeAbilities::thawAll);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source) {
        if (SpaceAbilities.ForceField.isProtected(entity)) {
            return false;
        }
        // The Soul Stone makes its wearer untouchable by everything short of the void and /kill.
        if (StoneBoost.has(entity, InfinityStone.SOUL) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        // The Power Stone wearer is made for explosions.
        if (StoneBoost.has(entity, InfinityStone.POWER) && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (entity instanceof Player && attacker != null
                && (attacker.getTags().contains(MindAbilities.PACIFIED_TAG) || attacker.getTags().contains(SoulAbilities.ARMY_TAG))) {
            return false; // pacified mobs and soul soldiers never hurt players
        }
        return true;
    }

    static void tick(MinecraftServer server) {
        StoneUtil.tickTasks();
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            AABB area = player.getBoundingBox().inflate(SCAN_RADIUS);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(MindAbilities.PACIFIED_TAG))) {
                if (mob.getTarget() instanceof Player) {
                    mob.setTarget(null);
                }
            }
            String owner = SoulAbilities.OWNER_PREFIX + player.getUUID();
            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, area, z -> z.getTags().contains(owner))) {
                tickSoldier(level, player, zombie);
            }
        }
    }

    private static void tickSoldier(ServerLevel level, ServerPlayer owner, Zombie soldier) {
        if (soldier.tickCount > SoulAbilities.LIFETIME) {
            level.sendParticles(ParticleTypes.SOUL, soldier.getX(), soldier.getY() + 1, soldier.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            soldier.discard();
            return;
        }
        LivingEntity target = null;
        LivingEntity victim = owner.getLastHurtMob();
        LivingEntity attacker = owner.getLastHurtByMob();
        if (victim != null && victim.isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
            target = victim;
        } else if (attacker != null && attacker.isAlive() && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) {
            target = attacker;
        }
        if (target == soldier || target == owner || (target != null && target.getTags().contains(SoulAbilities.ARMY_TAG))) {
            target = null;
        }
        soldier.setTarget(target);
        if (target == null && soldier.distanceToSqr(owner) > 100) {
            soldier.getNavigation().moveTo(owner, 1.2);
        }
    }
}
