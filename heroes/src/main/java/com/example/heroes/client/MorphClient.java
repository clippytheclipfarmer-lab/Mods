package com.example.heroes.client;

import com.example.heroes.origin.morph.MorphNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import virtuoel.pehkui.util.ScaleUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client side of shapeshifting: remembers who looks like what, swaps skins, and draws mob shapes with the mob's own renderer. */
public final class MorphClient {
    private record State(String kind, String target) {
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Entity> DUMMIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_TICK = new ConcurrentHashMap<>();

    private MorphClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MorphNet.MORPH, (client, handler, buf, sender) -> {
            UUID id = buf.readUUID();
            String kind = buf.readUtf();
            String target = buf.readUtf();
            client.execute(() -> {
                if (kind.isEmpty()) {
                    STATES.remove(id);
                    DUMMIES.remove(id);
                } else {
                    STATES.put(id, new State(kind, target));
                    DUMMIES.remove(id);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATES.clear();
            DUMMIES.clear();
            LAST_TICK.clear();
        });
    }

    // ------------------------------------------------------------------ skins

    private static PlayerInfo skinSource(AbstractClientPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null || !s.kind().equals("skin") || Minecraft.getInstance().getConnection() == null) {
            return null;
        }
        try {
            return Minecraft.getInstance().getConnection().getPlayerInfo(UUID.fromString(s.target()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ResourceLocation skin(AbstractClientPlayer player) {
        PlayerInfo info = skinSource(player);
        return info == null ? null : info.getSkinLocation();
    }

    public static String modelName(AbstractClientPlayer player) {
        PlayerInfo info = skinSource(player);
        return info == null ? null : info.getModelName();
    }

    // ------------------------------------------------------------------ mob shapes

    /** Draws the player as the mob they have become. Returns false to let the normal player renderer run. */
    @SuppressWarnings("unchecked")
    public static boolean renderMob(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        State s = STATES.get(player.getUUID());
        if (s == null || !s.kind().equals("mob")) {
            return false;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Entity dummy = DUMMIES.get(player.getUUID());
            if (dummy == null || dummy.level() != mc.level) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(s.target()));
                dummy = type.create(mc.level);
                if (dummy == null) {
                    STATES.remove(player.getUUID());
                    return false;
                }
                DUMMIES.put(player.getUUID(), dummy);
            }
            copyPose(player, dummy, partialTick);
            pose.pushPose();
            // The player's scale (set to match the mob's hitbox) is already applied to the pose; the mob is drawn at its own size.
            float w = ScaleUtils.getModelWidthScale(player, partialTick);
            float h = ScaleUtils.getModelHeightScale(player, partialTick);
            pose.scale(w == 0 ? 1 : 1 / w, h == 0 ? 1 : 1 / h, w == 0 ? 1 : 1 / w);
            EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(dummy);
            renderer.render(dummy, yaw, partialTick, pose, buffer, light);
            pose.popPose();
            return true;
        } catch (Exception e) {
            STATES.remove(player.getUUID()); // a shape that cannot be drawn falls back to the player
            DUMMIES.remove(player.getUUID());
            return false;
        }
    }

    private static void copyPose(AbstractClientPlayer player, Entity dummy, float partialTick) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.xOld = player.xOld;
        dummy.yOld = player.yOld;
        dummy.zOld = player.zOld;
        dummy.setYRot(player.getYRot());
        dummy.setXRot(player.getXRot());
        dummy.yRotO = player.yRotO;
        dummy.xRotO = player.xRotO;
        dummy.tickCount = player.tickCount;
        dummy.setOnGround(player.onGround());
        if (dummy instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;
            living.hurtTime = player.hurtTime;
            living.hurtDuration = player.hurtDuration;
            living.attackAnim = player.attackAnim;
            living.oAttackAnim = player.oAttackAnim;
            living.swinging = player.swinging;
            living.swingTime = player.swingTime;
            Integer last = LAST_TICK.get(player.getUUID());
            if (last == null || last != player.tickCount) {
                LAST_TICK.put(player.getUUID(), player.tickCount);
                living.walkAnimation.update(player.walkAnimation.speed(), 1.0F); // limb swing, once per game tick
            }
        }
    }
}
