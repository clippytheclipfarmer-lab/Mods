package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Hold meat in either hand and press the key: the symbiote eats it. Use with an 'action' condition. */
public class FeedAbility extends Ability {
    public FeedAbility() {
        this.withProperty(ICON, new ItemIcon(Items.COOKED_BEEF));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.level().isClientSide) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            var props = stack.getItem().getFoodProperties();
            if (props != null && props.isMeat()) {
                stack.shrink(1);
                SymbioteHost.feed(entity, props.getNutrition() * 4);
                entity.level().playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 0.7F);
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("\u00a77The symbiote devours it."), true);
                }
                return;
            }
        }
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("\u00a77The symbiote only wants meat."), true);
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Feed the symbiote meat.";
    }
}
