package com.example.heroes.stones;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A worn item with six sockets (Infinity Gauntlet, Necklace or Bracers). Sneak + right-click with a stone in the
 * other hand sockets it; sneak + right-click with an empty off hand takes the last stone out.
 * While worn it gives the powers of every stone socketed in it.
 */
public class HolderItem extends Item {
    private static final String KEY = "Stones";

    public HolderItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    /** The stones socketed in a holder stack. */
    public static Set<InfinityStone> stones(ItemStack stack) {
        Set<InfinityStone> set = EnumSet.noneOf(InfinityStone.class);
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (Tag t : tag.getList(KEY, Tag.TAG_STRING)) {
                InfinityStone stone = InfinityStone.byName(t.getAsString());
                if (stone != null) {
                    set.add(stone);
                }
            }
        }
        return set;
    }

    private static void write(ItemStack stack, Set<InfinityStone> stones) {
        ListTag list = new ListTag();
        stones.forEach(s -> list.add(StringTag.valueOf(s.name())));
        stack.getOrCreateTag().put(KEY, list);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack holder = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(holder);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(holder);
        }
        ItemStack other = player.getOffhandItem();
        Set<InfinityStone> stones = stones(holder);
        if (other.getItem() instanceof StoneItems.StoneItem stoneItem) {
            if (stones.contains(stoneItem.stone)) {
                player.displayClientMessage(Component.literal("That stone is already socketed."), true);
            } else {
                stones.add(stoneItem.stone);
                write(holder, stones);
                other.shrink(1);
                player.displayClientMessage(Component.literal(stoneItem.stone.displayName() + " socketed (" + stones.size() + "/6)."), true);
            }
        } else if (other.isEmpty() && !stones.isEmpty()) {
            List<InfinityStone> list = new ArrayList<>(stones);
            InfinityStone last = list.get(list.size() - 1);
            stones.remove(last);
            write(holder, stones);
            player.getInventory().placeItemBackInInventory(new ItemStack(StoneItems.get(last)));
            player.displayClientMessage(Component.literal(last.displayName() + " removed (" + stones.size() + "/6)."), true);
        }
        return InteractionResultHolder.consume(holder);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !stones(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Set<InfinityStone> stones = stones(stack);
        tooltip.add(Component.literal("Stones: " + stones.size() + "/6").withStyle(ChatFormatting.GOLD));
        for (InfinityStone stone : InfinityStone.values()) {
            boolean has = stones.contains(stone);
            tooltip.add(Component.literal((has ? "  ◆ " : "  ◇ ") + stone.displayName())
                    .withStyle(has ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Sneak + right-click with a stone in your off hand to socket it.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Wear it in an accessory slot.").withStyle(ChatFormatting.GRAY));
    }
}
