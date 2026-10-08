package com.example.heroes.mixin;

import com.example.heroes.client.MorphClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shapeshifter who has become another player wears that player's skin (and arm model). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void heroes$morphSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = MorphClient.skin((AbstractClientPlayer) (Object) this);
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void heroes$morphModel(CallbackInfoReturnable<String> cir) {
        String model = MorphClient.modelName((AbstractClientPlayer) (Object) this);
        if (model != null) {
            cir.setReturnValue(model);
        }
    }
}
