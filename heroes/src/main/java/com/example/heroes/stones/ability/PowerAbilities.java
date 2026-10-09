package com.example.heroes.stones.ability;

import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import com.example.heroes.stones.StoneUtil;
import com.example.heroes.stones.StoneWear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Power Stone: punch strength by stone count, the aimed beam, controlled explosions, empowerment and the meteor storm. */
public final class PowerAbilities {
    private static final int ALL_STONES = InfinityStone.values().length;

    private PowerAbilities() {
    }

    // ----------------------------------------------------------------- punch strength

    /** Attack damage that grows with every stone worn: 30, 70, 100, 300, 3000 and (all six) near infinite. Vanilla caps attack damage at 2048. */
    public static class PowerPunch extends Ability {
        private static final UUID MODIFIER_ID = UUID.fromString("5b0d6c3e-8c75-4f27-9a1c-6a2f0b1f7001");
        private static final double[] TIERS = {30, 70, 100, 300, 3000, 1.0E6};

        public PowerPunch() {
            this.withProperty(ICON, new ItemIcon(Items.IRON_SWORD));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || entity.level().isClientSide || entity.tickCount % 10 != 0) {
                return;
            }
            int stones = Math.max(1, Math.min(ALL_STONES, StoneWear.active(entity).size()));
            double amount = TIERS[stones - 1];
            AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attribute == null) {
                return;
            }
            AttributeModifier current = attribute.getModifier(MODIFIER_ID);
            if (current == null || current.getAmount() != amount) {
                attribute.removeModifier(MODIFIER_ID);
                attribute.addTransientModifier(new AttributeModifier(MODIFIER_ID, "Power Stone punch", amount, AttributeModifier.Operation.ADDITION));
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attribute != null && !entity.level().isClientSide) {
                attribute.removeModifier(MODIFIER_ID);
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Attack damage scales with the number of stones worn (30, 70, 100, 300, 3000, near infinite).";
        }
    }

    // ----------------------------------------------------------------- empower

    /** Health boost and resistance for a while. Use with an 'action' condition and a long cooldown. */
    public static class Empower extends StoneAbilities.Action {
        public Empower() {
            super(Items.ENCHANTED_GOLDEN_APPLE);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            int duration = (int) (1200 * StoneBoost.general(entity));
            entity.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, duration, 9, false, true));
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 3, false, true));
            entity.heal(entity.getMaxHealth());
            level.sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.0F, 1.0F), 1.5F), entity.getX(), entity.getY() + 1, entity.getZ(), 80, 0.4, 0.8, 0.4, 0);
            level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.7F);
        }

        @Override
        public String getDocumentationDescription() {
            return "Grants a large health boost and resistance for a while.";
        }
    }

    // ----------------------------------------------------------------- controlled explosion

    /** Hold to gather power, release to detonate around you. The holder is immune to explosions (see StoneHero). */
    public static class ControlledExplosion extends Ability {
        private static final int MAX_CHARGE = 100;
        private static final Map<UUID, Integer> CHARGE = new ConcurrentHashMap<>();

        public ControlledExplosion() {
            this.withProperty(ICON, new ItemIcon(Items.TNT));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            int charge = Math.min(CHARGE.merge(entity.getUUID(), 1, Integer::sum), MAX_CHARGE);
            CHARGE.put(entity.getUUID(), charge);
            if (charge % 4 == 0) {
                double ring = 3.5 - 2.5 * charge / MAX_CHARGE;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12 + charge * 0.2;
                    level.sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.1F, 1.0F), 1.4F),
                            entity.getX() + Math.cos(angle) * ring, entity.getY() + 0.3 + charge * 0.01, entity.getZ() + Math.sin(angle) * ring, 1, 0, 0, 0, 0);
                }
            }
            if (charge % 20 == 0) {
                level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F + charge / 100F);
            }
        }

        @Override
        public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            Integer charge = CHARGE.remove(entity.getUUID());
            if (charge == null || charge < 8 || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            float power = (float) Math.min(2.0 + charge / 8.0, 14.0) * (float) StoneBoost.general(entity);
            level.explode(entity, entity.getX(), entity.getY() + 0.5, entity.getZ(), power, Level.ExplosionInteraction.MOB);
        }

        @Override
        public String getDocumentationDescription() {
            return "Hold to charge, release to detonate. The holder is not hurt by explosions.";
        }
    }

    // ----------------------------------------------------------------- the aimed beam

    /** The damage steps of the beam; scrolling while aiming moves along them. */
    private static final float[] BEAM_DAMAGE = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048};
    private static final Map<UUID, Integer> BEAM_LEVEL = new ConcurrentHashMap<>();

    public static int beamLevel(Entity entity) {
        return BEAM_LEVEL.getOrDefault(entity.getUUID(), 3);
    }

    /**
     * Server side of the Power Stone beam (the glowing line is drawn by a 'palladium:energy_beam' ability next to it):
     * 100 blocks, damages everything along the line, smelts the block it hits and sets fires. With all six stones it turns
     * into the rainbow beam that explodes where it lands and erases whatever it touches.
     */
    public static class PowerBeam extends Ability {
        public PowerBeam() {
            this.withProperty(ICON, new ItemIcon(Items.END_CRYSTAL));
        }

        @Override
        public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (!enabled || !(entity.level() instanceof ServerLevel level) || entity.tickCount % 2 != 0) {
                return;
            }
            boolean full = StoneWear.active(entity).size() >= ALL_STONES;
            float damage = BEAM_DAMAGE[beamLevel(entity)] * (full ? 1.0E6F : 1.0F);
            Vec3 from = entity.getEyePosition().add(entity.getViewVector(1F).scale(0.6)).subtract(0, 0.25, 0);
            BlockHitResult blockHit = StoneUtil.rayBlock(entity, 100);
            Vec3 end = blockHit.getType() == HitResult.Type.MISS ? StoneUtil.lookEnd(entity, 100) : blockHit.getLocation();

            for (Entity hit : StoneUtil.entitiesAlong(level, entity, from, end, 0.35, e -> e.isAlive() && e instanceof LivingEntity && !(e instanceof Player p && p.isSpectator()))) {
                hit.hurt(level.damageSources().indirectMagic(entity, entity), damage);
                hit.setSecondsOnFire(5);
            }
            if (full) {
                double length = from.distanceTo(end);
                for (int i = 0; i < length; i += 3) {
                    Vec3 p = from.lerp(end, i / length);
                    level.sendParticles(new DustParticleOptions(new Vector3f(level.random.nextFloat(), level.random.nextFloat(), level.random.nextFloat()), 1.6F), p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0);
                }
            }
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    if (!smelt(level, pos)) {
                        BlockPos above = pos.relative(blockHit.getDirection());
                        if (level.getBlockState(above).isAir() && BaseFireBlock.canBePlacedAt(level, above, Direction.UP)) {
                            level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
                        }
                    }
                    if (full && entity.tickCount % 10 == 0) {
                        level.explode(entity, end.x, end.y, end.z, 4.0F, Level.ExplosionInteraction.MOB);
                    }
                }
                level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.02);
            }
        }

        /** Turns the block into what a furnace would make of it (sand to glass, ore to ingot block-form, ...). */
        private static boolean smelt(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            Item item = state.getBlock().asItem();
            if (item == Items.AIR || state.getDestroySpeed(level, pos) < 0) {
                return false;
            }
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(item)), level);
            if (recipe.isEmpty()) {
                return false;
            }
            ItemStack result = recipe.get().getResultItem(level.registryAccess());
            if (result.getItem() instanceof BlockItem blockItem) {
                level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
                return true;
            }
            return false;
        }

        @Override
        public String getDocumentationDescription() {
            return "Damage, smelting and fire of the Power Stone beam. Pair with a palladium:energy_beam ability (damage 0) for the visuals.";
        }
    }

    /** Scroll step for the beam damage. Use with an 'action' condition on a scroll key while aiming. */
    public static class BeamLevel extends Ability {
        public static final PalladiumProperty<Integer> DELTA = new IntegerProperty("delta").configurable("How far to move along the beam damage steps (+1 or -1)");

        public BeamLevel() {
            this.withProperty(ICON, new ItemIcon(Items.REDSTONE_TORCH));
            this.withProperty(DELTA, 1);
            this.withProperty(HIDDEN_IN_GUI, true);
            this.withProperty(HIDDEN_IN_BAR, true);
        }

        @Override
        public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
            if (enabled && entity.level() instanceof ServerLevel level) {
                int next = Math.max(0, Math.min(BEAM_DAMAGE.length - 1, beamLevel(entity) + entry.getProperty(DELTA)));
                BEAM_LEVEL.put(entity.getUUID(), next);
                level.playSound(null, entity.blockPosition(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.8F, 0.8F + next * 0.05F);
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("Power beam: " + (int) BEAM_DAMAGE[next] + " damage per hit"), true);
                }
            }
        }

    }

    // ----------------------------------------------------------------- meteor storm

    /** Power + Space: the Power Stone shatters a distant moon, the Space Stone pulls the fragments onto the target. */
    public static class MeteorStorm extends StoneAbilities.Action {
        private static final int METEORS = 24;

        public MeteorStorm() {
            super(Items.FIRE_CHARGE);
        }

        @Override
        void run(LivingEntity entity, ServerLevel level) {
            if (!StoneBoost.has(entity, InfinityStone.POWER) || !StoneBoost.has(entity, InfinityStone.SPACE)) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("The meteor storm needs both the Power and the Space Stone."), true);
                }
                return;
            }
            Vec3 target = StoneUtil.rayEnd(entity, 150);
            level.playSound(null, entity.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 2.0F, 0.6F);
            for (int i = 0; i < METEORS; i++) {
                int delay = 10 + i * 4 + level.random.nextInt(4);
                double x = target.x + (level.random.nextDouble() - 0.5) * 28;
                double z = target.z + (level.random.nextDouble() - 0.5) * 28;
                StoneUtil.later(delay, () -> {
                    LargeFireball meteor = new LargeFireball(level, entity, 0, -1, 0, 3);
                    meteor.setPos(x, target.y + 70, z);
                    meteor.setDeltaMovement(0, -1.6, 0);
                    meteor.xPower = 0;
                    meteor.yPower = -0.1;
                    meteor.zPower = 0;
                    level.addFreshEntity(meteor);
                });
            }
        }

        @Override
        public String getDocumentationDescription() {
            return "Calls down a rain of meteors on the targeted spot. Needs both the Power and the Space Stone.";
        }
    }
}
