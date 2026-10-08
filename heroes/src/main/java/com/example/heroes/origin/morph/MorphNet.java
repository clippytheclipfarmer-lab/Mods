package com.example.heroes.origin.morph;

import com.example.heroes.origin.OriginApi;
import com.example.heroes.origin.Sheet;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Tells clients what each shapeshifted player looks like (the client draws mob shapes and swaps skins). */
public final class MorphNet {
    public static final ResourceLocation MORPH = new ResourceLocation("heroes", "morph");

    private MorphNet() {
    }

    public static void init() {
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayer player) {
                send(viewer, player);
            }
        });
    }

    private static void send(ServerPlayer to, ServerPlayer about) {
        Sheet sheet = OriginApi.get(about);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(about.getUUID());
        buf.writeUtf(sheet == null ? "" : sheet.morphKind);
        buf.writeUtf(sheet == null ? "" : sheet.morphTarget);
        ServerPlayNetworking.send(to, MORPH, buf);
    }

    /** Sends the player's shape to themselves and everyone who can see them. */
    public static void broadcast(ServerPlayer player) {
        send(player, player);
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            send(viewer, player);
        }
    }

    /** A joining player learns about everyone who is shapeshifted. */
    public static void sendAllTo(ServerPlayer joiner) {
        for (ServerPlayer other : joiner.server.getPlayerList().getPlayers()) {
            Sheet sheet = OriginApi.get(other);
            if (sheet != null && !sheet.morphKind.isEmpty()) {
                send(joiner, other);
            }
        }
    }
}
