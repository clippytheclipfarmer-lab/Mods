package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import org.joml.Vector3f;

/** Fires a tendril: hit a creature and it is yanked to you, hit a block and you are pulled (swung) to it. Use with an 'action' condition. */
public class TendrilAbility extends Ability {
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.03F), 1.2F);

    public TendrilAbility() {
        this.withProperty(ICON, new ItemIcon(Items.LEAD));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double reach = SymbioteHost.isApex(entity) ? 40 : 28;
        Vec3 eye = entity.getEyePosition();
        Vec3 end = eye.add(entity.getViewVector(1F).scale(reach));

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, entity, eye, end, new AABB(eye, end).inflate(1.0),
                e -> e != entity && e.isAlive() && !e.isSpectator() && e.isPickable() && e instanceof LivingEntity);
        Vec3 point;
        if (hit != null) {
            point = hit.getLocation();
            LivingEntity target = (LivingEntity) hit.getEntity();
            Vec3 pull = entity.position().subtract(target.position()).normalize().scale(1.6);
            target.setDeltaMovement(pull.x, 0.4, pull.z);
            target.hurtMarked = true;
            target.hurt(level.damageSources().mobAttack(entity), 2.0F);
        } else {
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (block.getType() != HitResult.Type.BLOCK) {
                return;
            }
            point = block.getLocation();
            double dist = point.distanceTo(entity.position());
            Vec3 swing = point.subtract(entity.position()).normalize().scale(Math.min(0.8 + dist * 0.12, 2.6));
            entity.setDeltaMovement(swing.x, swing.y + 0.3, swing.z);
            entity.hurtMarked = true;
            entity.fallDistance = 0;
        }
        // Visible tendril.
        Vec3 from = entity.position().add(0, entity.getBbHeight() * 0.7, 0);
        int steps = (int) Math.max(4, from.distanceTo(point) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(point, i / (double) steps);
            level.sendParticles(BLACK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Symbiote tendril: yank creatures to you or swing to a block.";
    }
}
