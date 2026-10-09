package com.example.heroes.stones;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A powerful but high-risk weapon: it hits harder than netherite and withers the target, but every strike cuts the wielder too. */
public class DoubleEdgedSword extends SwordItem {
    private static final float BACKLASH = 3.0F;

    public DoubleEdgedSword(Properties properties) {
        super(Tiers.NETHERITE, 12, -2.0F, properties.rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (attacker instanceof Player player && player.getAbilities().instabuild) {
            return result;
        }
        attacker.hurt(attacker.level().damageSources().magic(), BACKLASH);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1));
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A powerful but high-risk weapon.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Withers what it cuts. Every strike costs you 1.5 hearts.").withStyle(ChatFormatting.DARK_RED));
    }
}
