package com.example.hulk.ability;

import com.example.hulk.HulkEffects;
import com.example.hulk.HulkRage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Seismic stomp: a wide quake that cracks stone and flings chunks of ground into the air. */
public class StompQuakeAbility extends Ability {
    private static final int MAX_FLUNG_BLOCKS = 40;

    public StompQuakeAbility() {
        this.withProperty(ICON, new ItemIcon(Items.POINTED_DRIPSTONE));
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        float rage = HulkRage.fraction(entity);
        float tier = HulkRage.tier(rage).multiplier;
        double radius = (6 + 6 * rage) * tier;
        Vec3 origin = entity.position();

        HulkEffects.blast(level, entity, origin, radius, (3 + 6 * rage) * tier, 0.9 * tier, 0.9);
        HulkEffects.ring(level, origin, radius);
        HulkEffects.boom(level, origin, 0.6F);

        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            // Fling a limited number of surface blocks, nearest first, so big quakes can't lag the server.
            int r = (int) radius;
            BlockPos center = entity.blockPosition().below();
            int flung = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 0, r))) {
                if (flung >= MAX_FLUNG_BLOCKS) {
                    break;
                }
                double dist = pos.distSqr(center);
                if (dist > r * r || level.random.nextFloat() > 0.25F) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (state.isAir() || hardness < 0 || hardness > 3.0F + 2.0F * rage * tier
                        || !level.getBlockState(pos.above()).isAir() || state.hasBlockEntity()) {
                    continue;
                }
                FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
                block.dropItem = false;
                block.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.4, 0.5 + level.random.nextDouble() * 0.6 * tier,
                        (level.random.nextDouble() - 0.5) * 0.4);
                flung++;
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Stomp quake: knocks enemies up and flings ground blocks. Use with an 'action' condition.";
    }
}
