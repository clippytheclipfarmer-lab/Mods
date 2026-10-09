package com.fiskheroes.ability;

import com.fiskheroes.entity.GadgetEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.BooleanProperty;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;

/**
 * Throws a boomerang gadget: the shield, the hammer or a batarang. With {@code consume} the item is taken from your hand
 * (main hand first, then off hand) and comes back to you; without it the gadget is an endless supply (batarangs).
 * Use with an 'action' condition.
 */
public class ThrowGadgetAbility extends Ability {
    public static final PalladiumProperty<String> GADGET = new StringProperty("gadget").configurable("shield, hammer or batarang");
    public static final PalladiumProperty<String> ITEM = new StringProperty("item").configurable("Item that is thrown (and shown flying)");
    public static final PalladiumProperty<Boolean> CONSUME = new BooleanProperty("consume").configurable("If true the item must be in a hand and is taken from it until it returns");
    public static final PalladiumProperty<Float> DAMAGE = new FloatProperty("damage").configurable("Damage per hit");
    public static final PalladiumProperty<Float> SPEED = new FloatProperty("speed").configurable("Throwing speed");

    public ThrowGadgetAbility() {
        this.withProperty(ICON, new ItemIcon(Items.SHIELD));
        this.withProperty(GADGET, "shield");
        this.withProperty(ITEM, "minecraft:shield");
        this.withProperty(CONSUME, true);
        this.withProperty(DAMAGE, 14F);
        this.withProperty(SPEED, 1.7F);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(entry.getProperty(ITEM)));
        ItemStack thrown;
        if (entry.getProperty(CONSUME)) {
            InteractionHand hand = entity.getMainHandItem().is(item) ? InteractionHand.MAIN_HAND : entity.getOffhandItem().is(item) ? InteractionHand.OFF_HAND : null;
            if (hand == null) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("message.fiskheroes.need_item", item.getDescription()), true);
                }
                return;
            }
            ItemStack held = entity.getItemInHand(hand);
            thrown = held.copy();
            thrown.setCount(1);
            if (!(entity instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild)) {
                held.shrink(1);
            }
        } else {
            thrown = new ItemStack(item);
        }
        GadgetEntity gadget = new GadgetEntity(level, entity, thrown, GadgetEntity.Kind.byName(entry.getProperty(GADGET)), entry.getProperty(DAMAGE), entry.getProperty(CONSUME));
        gadget.setPos(entity.getX(), entity.getEyeY() - 0.2, entity.getZ());
        gadget.shootFromRotation(entity, entity.getXRot(), entity.getYRot(), 0F, entry.getProperty(SPEED), 0F);
        level.addFreshEntity(gadget);
        entity.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, entity.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Throws a boomerang gadget (shield, hammer or batarang) that returns to the holder.";
    }
}
