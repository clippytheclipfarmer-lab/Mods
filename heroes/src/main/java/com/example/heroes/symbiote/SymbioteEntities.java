package com.example.heroes.symbiote;

import com.example.heroes.HeroesMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The symbiote mobs. They are only ever created by the meteor event and the bonding sequence. */
public final class SymbioteEntities {
    public static final EntityType<SymbioteBlobEntity> SYMBIOTE_BLOB = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "symbiote_blob"),
            EntityType.Builder.<SymbioteBlobEntity>of(SymbioteBlobEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 0.8F).clientTrackingRange(10).build("symbiote_blob"));

    public static final EntityType<SymbioteVillagerEntity> SYMBIOTE_VILLAGER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation(HeroesMod.MOD_ID, "symbiote_villager"),
            EntityType.Builder.<SymbioteVillagerEntity>of(SymbioteVillagerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("symbiote_villager"));

    private SymbioteEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SYMBIOTE_BLOB, SymbioteBlobEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SYMBIOTE_VILLAGER, SymbioteVillagerEntity.createAttributes());
    }
}
