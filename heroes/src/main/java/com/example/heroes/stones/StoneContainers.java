package com.example.heroes.stones;

import com.example.heroes.HeroesMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The six placeable stone containers: the Orb (Power), Tesseract (Space), Scepter (Mind), Aether (Reality), Eye of Agamotto (Time), Soul Urn (Soul). */
public final class StoneContainers {
    private static final Map<InfinityStone, Block> BLOCKS = new EnumMap<>(InfinityStone.class);
    private static final Map<InfinityStone, Item> ITEMS = new EnumMap<>(InfinityStone.class);

    private StoneContainers() {
    }

    public static String id(InfinityStone stone) {
        return switch (stone) {
            case POWER -> "power_orb";
            case SPACE -> "space_tesseract";
            case MIND -> "mind_scepter";
            case REALITY -> "reality_aether";
            case TIME -> "time_eye";
            case SOUL -> "soul_urn";
        };
    }

    private static VoxelShape shape(InfinityStone stone) {
        return switch (stone) {
            case POWER -> Block.box(3, 0, 3, 13, 13, 13);
            case SPACE -> Block.box(4, 0, 4, 12, 11, 12);
            case MIND -> Block.box(5, 0, 5, 11, 16, 11);
            case REALITY -> Block.box(5, 0, 5, 11, 13, 11);
            case TIME -> Block.box(3, 0, 6, 13, 13, 10);
            case SOUL -> Block.box(4, 0, 4, 12, 10, 12);
        };
    }

    public static Block block(InfinityStone stone) {
        return BLOCKS.get(stone);
    }

    public static Item item(InfinityStone stone) {
        return ITEMS.get(stone);
    }

    public static ItemStack stack(InfinityStone stone, boolean filled) {
        ItemStack stack = new ItemStack(ITEMS.get(stone));
        if (filled) {
            stack.getOrCreateTag().putBoolean("Filled", true);
        }
        return stack;
    }

    public static void init() {
        for (InfinityStone stone : InfinityStone.values()) {
            ResourceLocation key = new ResourceLocation(HeroesMod.MOD_ID, id(stone));
            Block block = Registry.register(BuiltInRegistries.BLOCK, key, new StoneContainerBlock(stone, shape(stone)));
            Item item = Registry.register(BuiltInRegistries.ITEM, key, new ContainerItem(block, stone, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
            BLOCKS.put(stone, block);
            ITEMS.put(stone, item);
        }
    }

    static final class ContainerItem extends BlockItem {
        private final InfinityStone stone;

        ContainerItem(Block block, InfinityStone stone, Properties properties) {
            super(block, properties);
            this.stone = stone;
        }

        static boolean filled(ItemStack stack) {
            return stack.getTag() != null && stack.getTag().getBoolean("Filled");
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return filled(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.literal(filled(stack) ? "Holds the " + stone.displayName() + "." : "Empty.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(Component.literal("Place it in the world. Right-click to take the stone out.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
