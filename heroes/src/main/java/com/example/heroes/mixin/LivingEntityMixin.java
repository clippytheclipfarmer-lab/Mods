package com.example.heroes.mixin;

import com.example.heroes.symbiote.SymbioteHost;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Symbiote hosts are immune to food poisoning: the harmful effects of food (rotten flesh, raw chicken, spider eyes, ...) are skipped. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "addEatEffect", at = @At("HEAD"), cancellable = true)
    private void heroes$noFoodPoisoningForHosts(ItemStack food, Level level, LivingEntity entity, CallbackInfo ci) {
        if (level.isClientSide || !food.isEdible() || !SymbioteHost.isHost(entity)) {
            return;
        }
        // Same as vanilla, but the harmful effects never apply.
        for (Pair<MobEffectInstance, Float> pair : food.getItem().getFoodProperties().getEffects()) {
            MobEffectInstance effect = pair.getFirst();
            if (effect != null && effect.getEffect().getCategory() != MobEffectCategory.HARMFUL && level.random.nextFloat() < pair.getSecond()) {
                entity.addEffect(new MobEffectInstance(effect));
            }
        }
        ci.cancel();
    }
}
