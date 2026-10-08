package com.example.heroes.mixin;

import com.example.heroes.origin.Ability5e;
import com.example.heroes.origin.OriginApi;
import com.example.heroes.origin.Sheet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CHA: charming players get cheaper villager trades (about 8% per point of modifier), clumsy ones pay more. */
@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void heroes$charismaPrices(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        Sheet sheet = OriginApi.get(player);
        int cha = sheet == null ? 0 : sheet.mod(Ability5e.CHA);
        if (cha == 0) {
            return;
        }
        for (MerchantOffer offer : ((Villager) (Object) this).getOffers()) {
            int count = offer.getBaseCostA().getCount();
            int discount = (int) Math.round(count * 0.08 * cha);
            if (discount == 0 && count >= 3) {
                discount = Integer.signum(cha);
            }
            offer.addToSpecialPriceDiff(-discount);
        }
    }
}
