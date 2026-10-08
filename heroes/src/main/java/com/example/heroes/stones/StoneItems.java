package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The six Infinity Stone items. */
public final class StoneItems {
    private static final Map<InfinityStone, Item> ITEMS = new EnumMap<>(InfinityStone.class);
    public static Item GAUNTLET;
    public static Item NECKLACE;
    public static Item BRACERS;

    private StoneItems() {
    }

    public static Item get(InfinityStone stone) {
        return ITEMS.get(stone);
    }

    public static void init() {
        StoneContainers.init();
        for (InfinityStone stone : InfinityStone.values()) {
            Item item = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, stone.id()),
                    new StoneItem(stone, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
            ITEMS.put(stone, item);
        }
        GAUNTLET = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "infinity_gauntlet"), new HolderItem(new Item.Properties()));
        NECKLACE = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "infinity_necklace"), new HolderItem(new Item.Properties()));
        BRACERS = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "infinity_bracers"), new HolderItem(new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            ITEMS.values().forEach(entries::accept);
            entries.accept(GAUNTLET);
            entries.accept(NECKLACE);
            entries.accept(BRACERS);
            for (InfinityStone stone : InfinityStone.values()) {
                entries.accept(StoneContainers.stack(stone, true));
                entries.accept(StoneContainers.stack(stone, false));
            }
        });
    }

    public static final class StoneItem extends Item {
        public final InfinityStone stone;

        StoneItem(InfinityStone stone, Properties properties) {
            super(properties);
            this.stone = stone;
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.literal("One of the six Infinity Stones.").withStyle(net.minecraft.ChatFormatting.GRAY));
            if (stone == InfinityStone.POWER) {
                tooltip.add(Component.literal("Dangerous to carry: it hurts you and pulses shockwaves.").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                tooltip.add(Component.literal("Wear it, socket it in a holder, or keep it in the Orb.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }
}
