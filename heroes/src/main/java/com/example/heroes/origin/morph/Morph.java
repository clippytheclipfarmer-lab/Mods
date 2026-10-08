package com.example.heroes.origin.morph;

import com.example.heroes.origin.Ability5e;
import com.example.heroes.origin.OriginApi;
import com.example.heroes.origin.OriginData;
import com.example.heroes.origin.Sheet;
import com.example.heroes.origin.Race;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladiumcore.registry.DeferredRegister;

/**
 * Shapeshifting (the Skrull ability). Look at a creature and press the key to take on its model; look at nothing and
 * press it to change back. Another character's look is copied from the character system: a race with models gives its
 * model power, anyone else gives their skin; a mob is drawn by the client with its own model. The shape is saved in the
 * character sheet, synced to nearby players, and the hitbox is scaled to the new body.
 */
public final class Morph {
    public static final ResourceLocation MARKER = new ResourceLocation("races", "morphed");
    public static final DeferredRegister<Ability> ABILITIES = DeferredRegister.create("races", Ability.REGISTRY);
    private static final double RANGE = 24;

    static {
        ABILITIES.register("shapeshift", ShapeshiftAbility::new);
    }

    private Morph() {
    }

    public static void init() {
        ABILITIES.register();
        MorphNet.init();
    }

    public static boolean active(Sheet sheet) {
        return sheet != null && !sheet.morphKind.isEmpty();
    }

    /** The creature the player is looking at, or null. */
    public static LivingEntity target(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity && e != player && !e.isSpectator() && e.isAlive(), end.distanceToSqr(eye));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** The key was pressed: copy what you are looking at, or change back when looking at nothing. */
    public static void shift(ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null) {
            return;
        }
        LivingEntity target = target(player);
        if (target == null) {
            if (active(sheet)) {
                revert(player);
            } else {
                player.displayClientMessage(Component.literal("\u00a77Look at a creature to take its shape."), true);
            }
            return;
        }
        if (!copy(sheet, target)) {
            player.displayClientMessage(Component.literal("\u00a77You cannot take that shape."), true);
            return;
        }
        OriginData.get(player.server).setDirty();
        player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.literal("\u00a7aYou take the shape of " + target.getName().getString() + "."), true);
        OriginApi.refresh(player);
    }

    private static boolean copy(Sheet sheet, LivingEntity target) {
        if (target instanceof ServerPlayer other) {
            Sheet theirs = OriginApi.get(other);
            if (theirs != null && active(theirs)) {            // copy the shape they are wearing
                sheet.morphKind = theirs.morphKind;
                sheet.morphTarget = theirs.morphTarget;
                sheet.morphHeight = theirs.morphHeight;
                return true;
            }
            Race race = theirs == null ? null : theirs.raceDef();
            ResourceLocation model = race == null ? null : race.models.get(theirs.gender);
            sheet.morphHeight = theirs != null && theirs.height > 0 ? theirs.height : 1.8F;
            if (model != null) {
                sheet.morphKind = "model";
                sheet.morphTarget = model.toString();
            } else {
                sheet.morphKind = "skin";
                sheet.morphTarget = other.getUUID().toString();
            }
            return true;
        }
        EntityType<?> type = target.getType();
        if (target instanceof EnderDragon || target instanceof WitherBoss || !type.canSummon()) {
            return false;
        }
        sheet.morphKind = "mob";
        sheet.morphTarget = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        sheet.morphHeight = Math.max(0.5F, Math.min(3.6F, type.getHeight()));
        return true;
    }

    public static void revert(ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null || !active(sheet)) {
            return;
        }
        clearQuietly(sheet);
        OriginData.get(player.server).setDirty();
        player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.literal("\u00a77You return to your own shape."), true);
        OriginApi.refresh(player);
    }

    /** Forgets the shape without announcing or refreshing (the caller refreshes). */
    public static void clearQuietly(ServerPlayer player) {
        clearQuietly(OriginApi.get(player));
    }

    private static void clearQuietly(Sheet sheet) {
        if (sheet != null) {
            sheet.morphKind = "";
            sheet.morphTarget = "";
            sheet.morphHeight = 0;
        }
    }
}
