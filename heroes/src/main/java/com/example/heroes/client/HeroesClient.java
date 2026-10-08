package com.example.heroes.client;

import com.example.heroes.pod.PodNet;
import com.example.heroes.pod.PodRegistry;
import com.example.heroes.pod.SpacePodEntity;
import com.example.heroes.space.SpaceSystem;
import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneContainers;
import com.example.heroes.symbiote.SymbioteEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/** Client side: planet gravity, the space pod renderer, and the H-key star map. */
public class HeroesClient implements ClientModInitializer {
    private static volatile float gravity = 1F;
    private static KeyMapping starMapKey;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpaceSystem.GRAVITY_PACKET, (client, handler, buf, sender) -> {
            float g = buf.readFloat();
            client.execute(() -> gravity = g);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> gravity = 1F);

        // Space pod
        EntityModelLayerRegistry.registerModelLayer(SpacePodModel.LAYER, SpacePodModel::createBodyLayer);
        EntityRendererRegistry.register(PodRegistry.SPACE_POD, SpacePodRenderer::new);

        // Stone containers show their gem only when filled
        for (InfinityStone stone : InfinityStone.values()) {
            net.minecraft.client.renderer.item.ItemProperties.register(StoneContainers.item(stone), new net.minecraft.resources.ResourceLocation("heroes", "filled"),
                    (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().getBoolean("Filled") ? 1.0F : 0.0F);
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(StoneContainers.block(stone), net.minecraft.client.renderer.RenderType.cutout());
        }

        // Symbiote mobs
        EntityModelLayerRegistry.registerModelLayer(SymbioteBlobModel.LAYER, SymbioteBlobModel::createBodyLayer);
        EntityRendererRegistry.register(SymbioteEntities.SYMBIOTE_BLOB, SymbioteBlobRenderer::new);
        EntityRendererRegistry.register(SymbioteEntities.SYMBIOTE_VILLAGER, SymbioteVillagerRenderer::new);

        // Star map
        starMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.heroes.star_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.heroes"));
        ClientPlayNetworking.registerGlobalReceiver(PodNet.BODIES, (client, handler, buf, sender) -> {
            // Copy the data on the network thread, then show the screen on the main thread.
            var copy = new net.minecraft.network.FriendlyByteBuf(buf.copy());
            client.execute(() -> {
                StarMapScreen.receive(copy);
                if (!(client.screen instanceof StarMapScreen)) {
                    client.setScreen(new StarMapScreen());
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            while (starMapKey.consumeClick()) {
                if (player.getVehicle() instanceof SpacePodEntity && client.screen == null) {
                    StarMapScreen.request();
                } else if (client.screen == null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Sit in a space pod to use the star map."), true);
                }
            }

            float g = gravity;
            if (g == 1F || player.isNoGravity() || player.getAbilities().flying || player.isFallFlying()
                    || player.isInWater() || player.isInLava() || player.onGround() || player.isPassenger()) {
                return;
            }
            // Vanilla applies vy = (vy - 0.08) * 0.98 each tick; add the difference for gravity g.
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y + 0.08 * (1.0 - g) * 0.98, motion.z);
        });
    }
}
