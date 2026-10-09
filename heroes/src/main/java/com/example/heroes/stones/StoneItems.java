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
    public static Item RING;
    public static Item COSMI_ROD;
    public static Item DOUBLE_EDGED_SWORD;

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
        RING = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "stone_ring"), new HolderItem(new Item.Properties(), 1));
        COSMI_ROD = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "cosmi_rod"), new HolderItem(new Item.Properties(), 1, true));
        DOUBLE_EDGED_SWORD = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(HeroesMod.MOD_ID, "double_edged_sword"), new DoubleEdgedSword(new Item.Properties()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(DOUBLE_EDGED_SWORD));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            ITEMS.values().forEach(entries::accept);
            entries.accept(GAUNTLET);
            entries.accept(NECKLACE);
            entries.accept(BRACERS);
            entries.accept(RING);
            entries.accept(COSMI_ROD);
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
            tooltip.add(Component.literal("Cannot be worn on its own: socket it into the Gauntlet, Necklace or Bracers.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            String curse = switch (stone) {
                case POWER -> "Loose, it burns you for 3 hearts every 2 s and pulses shockwaves. It can kill you.";
                case SPACE -> "Loose, it tears you sideways through space every few seconds.";
                case MIND -> "Loose, it clouds your mind and turns nearby monsters on you.";
                case REALITY -> "Loose, it makes reality warp around you with random bad effects.";
                case TIME -> "Loose, time drags: you are slowed and weakened now and then.";
                case SOUL -> "Loose, it drains your health and hunger.";
            };
            tooltip.add(Component.literal(curse).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.literal("Socket it in a holder, or keep it in its container, to be safe.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
