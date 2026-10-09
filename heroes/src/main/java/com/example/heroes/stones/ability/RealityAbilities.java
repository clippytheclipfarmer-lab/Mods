package com.example.heroes.stones.ability;

import com.example.heroes.stones.StoneBoost;
import com.example.heroes.stones.StoneScale;
import com.example.heroes.stones.StoneUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reality Stone: resizing, block duplication, area debuffs, weather and bubbles. */
public final class RealityAbilities {
    private RealityAbilities() {
    }

    // ----------------------------------------------------------------- size

    private static final Map<UUID, Integer> SIZE_LEVEL = new ConcurrentHashMap<>();

    /** Toggle: scroll (see {@link ResizeStep}) to shrink down to a tenth or grow up to ten times. */
    public static class Resize extends Ability {
        public Resize() {
            this.withProperty(ICON, new ItemIcon(Items.BROWN_MUSHROOM));
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled) {
                SIZE_LEVEL.putIfAbsent(entity.getUUID(), 0);
            }
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && !entity.level().isClientSide) {
                int level = SIZE_LEVEL.getOrDefault(entity.getUUID(), 0);
                StoneScale.set(entity, (float) Math.pow(10, level / 10.0));
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            SIZE_LEVEL.remove(entity.getUUID());
            if (!entity.level().isClientSide) {
                StoneScale.set(entity, 1.0F);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Toggle; with the step abilities the holder can scale from one tenth to ten times their size.";
        }
    }

    /** Scroll step for {@link Resize} (only does something while Resize is on). */
    public static class ResizeStep extends Ability {
        public static final PalladiumProperty<Integer> DELTA = new IntegerProperty("delta").configurable("Size steps to move (+1 grows, -1 shrinks; 10 steps make a factor of ten)");

        public ResizeStep() {
            this.withProperty(ICON, new ItemIcon(Items.BROWN_MUSHROOM));
            this.withProperty(DELTA, 1);
            this.withProperty(HIDDEN_IN_GUI, true);
            this.withProperty(HIDDEN_IN_BAR, true);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level) || !SIZE_LEVEL.containsKey(entity.getUUID())) {
                return;
            }
            int next = Math.max(-10, Math.min(10, SIZE_LEVEL.get(entity.getUUID()) + entry.getProperty(DELTA)));
            SIZE_LEVEL.put(entity.getUUID(), next);
            level.playSound(null, entity.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.8F, 1.0F + next * 0.05F);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(String.format("Size x%.2f", Math.pow(10, next / 10.0))), true);
            }
        }
    }

    // ----------------------------------------------------------------- block duplication

    /** Toggle: every block you look at is copied into your inventory (a stack, if you do not already carry it). */
    public static class BlockCopy extends Ability {
        public BlockCopy() {
            this.withProperty(ICON, new ItemIcon(Items.GRASS_BLOCK));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
                return;
            }
            BlockHitResult hit = StoneUtil.rayBlock(player, 8);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockState state = player.level().getBlockState(hit.getBlockPos());
            Item item = state.getBlock().asItem();
            if (item == Items.AIR || (state.getDestroySpeed(player.level(), hit.getBlockPos()) < 0 && !player.getAbilities().instabuild)) {
                return;
            }
            if (player.getInventory().countItem(item) == 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(item, item.getMaxStackSize()));
                ((ServerLevel) player.level()).sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 1.2F),
                        hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 12, 0.3, 0.3, 0.3, 0);
                player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Blocks you look at are duplicated into your inventory while enabled.";
        }
    }

    // ----------------------------------------------------------------- effects

    /** Blinds, nauseates and slows everything around the holder. */
    public static class RealityEffects extends StoneAbilities.Action {
        public RealityEffects() {
            super(Items.FERMENTED_SPIDER_EYE);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            double radius = 14 * StoneBoost.general(entity);
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                    e -> e != entity && e.isAlive() && !(e instanceof Player p && p.isCreative()))) {
                other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
                other.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300));
                other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300));
                other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
            }
            level.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.0F, 0.0F), 2.0F), entity.getX(), entity.getY() + 1, entity.getZ(), 120, radius / 3, 0.8, radius / 3, 0);
            level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Gives nearby entities blindness, nausea, darkness and slowness.";
        }
    }

    // ----------------------------------------------------------------- weather

    /** Sets the weather. */
    public static class Weather extends StoneAbilities.Action {
        public static final PalladiumProperty<String> KIND = new StringProperty("weather").configurable("clear, rain or thunder");

        public Weather() {
            super(Items.WATER_BUCKET);
            this.withProperty(KIND, "clear");
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && entity.level() instanceof ServerLevel level) {
                ServerLevel overworld = level.getServer().overworld();
                switch (entry.getProperty(KIND)) {
                    case "rain" -> overworld.setWeatherParameters(0, 6000, true, false);
                    case "thunder" -> overworld.setWeatherParameters(0, 6000, true, true);
                    default -> overworld.setWeatherParameters(6000, 0, false, false);
                }
                level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1, entity.getZ(), 60, 1.2, 1.0, 1.2, 0.3);
                level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.0F);
            }
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
        }

        @Override
        public String getDocumentationDescription() {
            return "Sets the weather to clear, rain or thunder.";
        }
    }

    // ----------------------------------------------------------------- bubble

    /** Turns the blocks you look at (within a few blocks of you) into bubbles. */
    public static class Bubble extends StoneAbilities.Action {
        public Bubble() {
            super(Items.PRISMARINE_CRYSTALS);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            BlockHitResult hit = StoneUtil.rayBlock(entity, 6);
            if (hit.getType() != HitResult.Type.BLOCK || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return;
            }
            BlockPos center = hit.getBlockPos();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
                if (pos.distSqr(center) > 5) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
                    level.removeBlock(pos, false);
                    level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
            level.playSound(null, center, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.5F, 0.8F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Turns the targeted blocks into bubbles (removes them).";
        }
    }
}
