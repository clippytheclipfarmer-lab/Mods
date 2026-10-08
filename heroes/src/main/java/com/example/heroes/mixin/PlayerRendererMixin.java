package com.example.heroes.mixin;

import com.example.heroes.client.MorphClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A shapeshifter who has become a mob is drawn with that mob's renderer instead of as a player. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void heroes$renderMorph(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (MorphClient.renderMob(player, yaw, partialTick, pose, buffer, light)) {
            ci.cancel();
        }
    }
}
