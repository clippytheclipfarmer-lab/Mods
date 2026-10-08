package com.example.heroes.symbiote.klyntar;

import com.example.heroes.HeroesMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Klyntar's creatures and blocks. */
public final class KlyntarEntities {
    public static final EntityType<SymbioteCrawlerEntity> SYMBIOTE_CRAWLER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "symbiote_crawler"),
            EntityType.Builder.<SymbioteCrawlerEntity>of(SymbioteCrawlerEntity::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(8).build("symbiote_crawler"));
    public static final EntityType<SymbioteBruteEntity> SYMBIOTE_BRUTE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "symbiote_brute"),
            EntityType.Builder.<SymbioteBruteEntity>of(SymbioteBruteEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(10).build("symbiote_brute"));
    public static final EntityType<KnullEntity> KNULL = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "knull"),
            EntityType.Builder.<KnullEntity>of(KnullEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(16).fireImmune().build("knull"));

    public static final Block TAR = block("symbiote_tar", new SymbioteTarBlock());
    public static final Block NUB = block("tendril_nub", new TendrilNubBlock());
    public static final Block ALTAR = block("bonding_altar", new BondingAltarBlock());

    private KlyntarEntities() {
    }

    private static Block block(String name, Block block) {
        ResourceLocation id = new ResourceLocation(HeroesMod.MOD_ID, name);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
        return block;
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SYMBIOTE_CRAWLER, SymbioteCrawlerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SYMBIOTE_BRUTE, SymbioteBruteEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(KNULL, KnullEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(TAR);
            entries.accept(NUB);
            entries.accept(ALTAR);
        });
    }
}
