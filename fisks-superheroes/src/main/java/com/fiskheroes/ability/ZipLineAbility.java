package com.fiskheroes.ability;

import com.fiskheroes.FiskUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires a line at the block you look at and reels you towards it while the key is held (Spider-Man's web zip, Batman's
 * grapple). Letting go keeps your momentum. Use with a 'held' condition.
 */
public class ZipLineAbility extends Ability {
    public static final PalladiumProperty<Float> RANGE = new FloatProperty("range").configurable("How far the line reaches");
    public static final PalladiumProperty<Float> SPEED = new FloatProperty("speed").configurable("Reel speed in blocks per tick");
    public static final PalladiumProperty<String> COLOR = new StringProperty("color").configurable("Colour of the line as #rrggbb");

    private static final Map<UUID, Vec3> ANCHORS = new ConcurrentHashMap<>();

    public ZipLineAbility() {
        this.withProperty(ICON, new ItemIcon(Items.STRING));
        this.withProperty(RANGE, 40F);
        this.withProperty(SPEED, 1.1F);
        this.withProperty(COLOR, "#ffffff");
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult hit = FiskUtil.rayBlock(entity, entry.getProperty(RANGE));
        if (hit.getType() == HitResult.Type.MISS) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.fiskheroes.nothing_to_grab"), true);
            }
            return;
        }
        ANCHORS.put(entity.getUUID(), hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.4)));
        level.playSound(null, entity.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        Vec3 anchor = ANCHORS.get(entity.getUUID());
        if (!enabled || anchor == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 chest = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
        Vec3 to = anchor.subtract(chest);
        double dist = to.length();
        if (dist < 2.2) {
            ANCHORS.remove(entity.getUUID());   // arrived
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.4));
            entity.hurtMarked = true;
            return;
        }
        double speed = entry.getProperty(SPEED);
        Vec3 pull = to.normalize().scale(speed * 0.4);
        Vec3 motion = entity.getDeltaMovement().scale(0.88).add(pull);
        if (motion.length() > speed * 1.7) {
            motion = motion.normalize().scale(speed * 1.7);
        }
        entity.setDeltaMovement(motion);
        entity.hurtMarked = true;
        entity.fallDistance = 0;

        int rgb = Integer.parseInt(entry.getProperty(COLOR).replace("#", ""), 16);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 0.7F);
        Vec3 hand = entity.getEyePosition().subtract(0, 0.4, 0);
        int steps = (int) Math.min(40, hand.distanceTo(anchor));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = hand.lerp(anchor, i / (double) Math.max(1, steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        ANCHORS.remove(entity.getUUID());
    }

    @Override
    public String getDocumentationDescription() {
        return "Fires a line at the targeted block and reels the holder in while held (web zip, grappling hook).";
    }
}
