package com.example.heroes.city;

import com.example.heroes.HeroesMod;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Selection wand for city regions: left-click a block = corner 1, right-click a block = corner 2. */
public final class CityWand {
    public static final Item WAND = new Item(new Item.Properties().stacksTo(1));

    public record Selection(ResourceKey<Level> dimension, BlockPos pos1, BlockPos pos2) {
    }

    private static final Map<UUID, BlockPos[]> CORNERS = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> DIMS = new HashMap<>();

    private CityWand() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.ITEM, new net.minecraft.resources.ResourceLocation(HeroesMod.MOD_ID, "city_wand"), WAND);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(WAND));

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().is(WAND)) {
                if (!world.isClientSide && player instanceof ServerPlayer sp) {
                    set(sp, 0, pos);
                }
                return InteractionResult.SUCCESS; // do not break the block
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().is(WAND)) {
                if (!world.isClientSide && player instanceof ServerPlayer sp) {
                    set(sp, 1, hit.getBlockPos());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static void set(ServerPlayer player, int index, BlockPos pos) {
        BlockPos[] corners = CORNERS.computeIfAbsent(player.getUUID(), k -> new BlockPos[2]);
        ResourceKey<Level> dim = player.level().dimension();
        if (DIMS.get(player.getUUID()) != dim) {
            corners[0] = null;
            corners[1] = null;
            DIMS.put(player.getUUID(), dim);
        }
        corners[index] = pos.immutable();
        player.displayClientMessage(Component.literal("City corner " + (index + 1) + " set to " + pos.toShortString()
                + (corners[0] != null && corners[1] != null ? " - now use /city create <name>" : "")), true);
    }

    public static Selection selection(ServerPlayer player) {
        BlockPos[] corners = CORNERS.get(player.getUUID());
        if (corners == null || corners[0] == null || corners[1] == null) {
            return null;
        }
        return new Selection(DIMS.get(player.getUUID()), corners[0], corners[1]);
    }
}
