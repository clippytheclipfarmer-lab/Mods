package com.example.heroes.client;

import com.example.heroes.origin.OriginNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;

/** Client side of the origin system: keeps the latest sheet from the server and opens it with K. */
public final class OriginClient {
    private static KeyMapping key;
    static volatile CompoundTag sheet = new CompoundTag();

    private OriginClient() {
    }

    public static void init() {
        MorphClient.init();
        ClientPlayNetworking.registerGlobalReceiver(OriginNet.SHEET, (client, handler, buf, sender) -> {
            CompoundTag tag = buf.readNbt();
            client.execute(() -> sheet = tag == null ? new CompoundTag() : tag);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sheet = new CompoundTag());
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heroes.character_sheet", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.heroes"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new CharacterScreen());
                }
            }
        });
    }
}
