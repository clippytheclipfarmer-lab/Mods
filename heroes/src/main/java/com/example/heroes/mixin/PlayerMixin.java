package com.example.heroes.mixin;

import com.example.heroes.origin.OriginApi;
import com.example.heroes.origin.Sheet;
import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A host's symbiote eats first: while its food bar is not full, food goes to it instead of the host. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private void heroes$symbioteEatsFirst(Level level, ItemStack food, CallbackInfoReturnable<ItemStack> cir) {
        Player self = (Player) (Object) this;
        if (level.isClientSide || !food.isEdible() || !SymbioteHost.eatFirst(self, food)) {
            return;
        }
        // Same bookkeeping as a normal meal, minus the food level and the food's effects.
        self.awardStat(Stats.ITEM_USED.get(food.getItem()));
        level.playSound(null, self.getX(), self.getY(), self.getZ(), self.getEatingSound(food), SoundSource.NEUTRAL, 1.0F,
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
        if (self instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, food);
        }
        if (!self.getAbilities().instabuild) {
            food.shrink(1);
        }
        cir.setReturnValue(food);
    }

    /** Race trait: some races (the Asgardians) tire more slowly, so hunger drains by a fraction less. */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float heroes$slowerHunger(float exhaustion) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer) {
            Sheet sheet = OriginApi.get(self);
            double multiplier = sheet == null ? 0 : sheet.trait("hunger_multiplier");
            if (multiplier > 0) {
                return (float) (exhaustion * multiplier);
            }
        }
        return exhaustion;
    }
}
