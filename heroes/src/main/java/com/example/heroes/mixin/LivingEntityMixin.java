package com.example.heroes.mixin;

import com.example.heroes.origin.Ability5e;
import com.example.heroes.origin.OriginApi;
import com.example.heroes.origin.Sheet;
import com.example.heroes.symbiote.SymbioteHost;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    /** WIS: harmful effects wear off faster (10% shorter per point of modifier, at most 50% shorter). */
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance heroes$wisdomResistsEffects(MobEffectInstance effect) {
        if ((Object) this instanceof ServerPlayer player && effect.getEffect().getCategory() == MobEffectCategory.HARMFUL && effect.getDuration() > 0) {
            Sheet sheet = OriginApi.get(player);
            int wis = sheet == null ? 0 : sheet.mod(Ability5e.WIS);
            if (wis > 0) {
                int duration = Math.max(1, (int) (effect.getDuration() * (1.0 - Math.min(0.5, 0.1 * wis))));
                return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
            }
        }
        return effect;
    }
}
