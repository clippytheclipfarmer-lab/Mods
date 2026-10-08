package com.example.heroes.pod;

import com.example.heroes.HeroesMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** Registration of the space pod entity and item. */
public final class PodRegistry {
    public static final EntityType<SpacePodEntity> SPACE_POD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "space_pod"),
            EntityType.Builder.<SpacePodEntity>of(SpacePodEntity::new, MobCategory.MISC)
                    .sized(2.2F, 1.4F).clientTrackingRange(16).updateInterval(1).fireImmune().build("space_pod"));

    public static final Item SPACE_POD_ITEM = Registry.register(BuiltInRegistries.ITEM,
            new ResourceLocation(HeroesMod.MOD_ID, "space_pod"), new SpacePodItem(new Item.Properties().stacksTo(1)));

    private PodRegistry() {
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(SPACE_POD_ITEM));
        PodNet.init();
    }
}
