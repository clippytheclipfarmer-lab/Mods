package com.example.heroes.city;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builder villagers: stand around the rebuild front hammering, with construction sounds. Scripted, not real pathing. */
final class CityBuilders {
    static final String TAG = "heroes_builder";
    private static final SoundEvent[] SOUNDS = {
            SoundEvents.ANVIL_USE, SoundEvents.VILLAGER_WORK_MASON, SoundEvents.VILLAGER_WORK_TOOLSMITH,
            SoundEvents.WOOD_PLACE, SoundEvents.STONE_PLACE, SoundEvents.METAL_PLACE, SoundEvents.CHAIN_PLACE
    };

    private static final Map<String, List<UUID>> BUILDERS = new HashMap<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private CityBuilders() {
    }

    static int count(String region) {
        List<UUID> list = BUILDERS.get(region);
        return list == null ? 0 : list.size();
    }

    static void clear() {
        BUILDERS.clear();
        ACTIVE.clear();
    }

    /** Builders saved into the world by a previous session are removed when they load. */
    static void onEntityLoad(Entity entity, ServerLevel level) {
        if (entity.getTags().contains(TAG) && !ACTIVE.contains(entity.getUUID())) {
            level.getServer().execute(entity::discard);
        }
    }

    static void despawn(String region) {
        List<UUID> list = BUILDERS.remove(region);
        if (list == null || CityManager.server() == null) {
            return;
        }
        for (UUID id : list) {
            ACTIVE.remove(id);
            for (ServerLevel level : CityManager.server().getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                    break;
                }
            }
        }
    }

    static void tick(ServerLevel level, CityRegion region, BlockPos front, int pending) {
        if (front == null || region.maxBuilders <= 0) {
            return;
        }
        boolean playerNear = false;
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(front) < 96 * 96) {
                playerNear = true;
                break;
            }
        }
        List<UUID> list = BUILDERS.computeIfAbsent(region.name, k -> new ArrayList<>());
        if (!playerNear) {
            return;
        }

        // Keep the number of builders in proportion to the amount of work left.
        int wanted = Math.min(region.maxBuilders, 1 + pending / 2000);
        long time = level.getGameTime();
        list.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) {
                ACTIVE.remove(id);
                return true;
            }
            return false;
        });
        while (list.size() < wanted && time % 20 == 0) {
            BlockPos spot = groundNear(level, front, 6);
            if (spot == null) {
                break;
            }
            list.add(spawn(level, spot));
        }
        while (list.size() > wanted) {
            UUID id = list.remove(list.size() - 1);
            ACTIVE.remove(id);
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }

        for (UUID id : list) {
            Entity e = level.getEntity(id);
            if (e == null) {
                continue;
            }
            // Wander to a new spot near the front now and then, and hammer away in between.
            if ((time + id.hashCode()) % 120 == 0) {
                BlockPos spot = groundNear(level, front, 8);
                if (spot != null) {
                    e.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            }
            e.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(front));
            if (region.sounds && (time + id.hashCode()) % 16 == 0) {
                SoundEvent sound = SOUNDS[level.random.nextInt(SOUNDS.length)];
                level.playSound(null, e.blockPosition(), sound, SoundSource.NEUTRAL, 0.9F, 0.9F + level.random.nextFloat() * 0.3F);
            }
        }
    }

    private static UUID spawn(ServerLevel level, BlockPos pos) {
        Villager builder = new Villager(EntityType.VILLAGER, level);
        builder.setVillagerData(builder.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(2));
        builder.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        builder.setNoAi(true);
        builder.setInvulnerable(true);
        builder.setSilent(true);
        builder.setCustomName(Component.literal("Builder"));
        builder.addTag(TAG);
        builder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        ACTIVE.add(builder.getUUID());
        level.addFreshEntity(builder);
        return builder.getUUID();
    }

    /** A standable spot (solid below, two free blocks above) within range of the pos, or null. */
    static BlockPos groundNear(ServerLevel level, BlockPos center, int range) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = center.getX() + level.random.nextInt(range * 2 + 1) - range;
            int z = center.getZ() + level.random.nextInt(range * 2 + 1) - range;
            for (int y = center.getY() + 3; y >= center.getY() - 8; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!level.isLoaded(p)) {
                    break;
                }
                if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                        && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                    return p;
                }
            }
        }
        return null;
    }
}
