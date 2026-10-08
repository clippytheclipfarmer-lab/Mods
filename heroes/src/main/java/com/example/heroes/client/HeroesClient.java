package com.example.heroes.client;

import com.example.heroes.space.SpaceSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/** Client side of the space system: applies the current world's gravity to the local player. */
public class HeroesClient implements ClientModInitializer {
    private static volatile float gravity = 1F;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpaceSystem.GRAVITY_PACKET, (client, handler, buf, sender) -> {
            float g = buf.readFloat();
            client.execute(() -> gravity = g);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> gravity = 1F);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            float g = gravity;
            if (player == null || g == 1F || player.isNoGravity() || player.getAbilities().flying || player.isFallFlying()
                    || player.isInWater() || player.isInLava() || player.onGround() || player.isPassenger()) {
                return;
            }
            // Vanilla applies vy = (vy - 0.08) * 0.98 each tick; add the difference for gravity g.
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y + 0.08 * (1.0 - g) * 0.98, motion.z);
        });
    }
}
