package com.fiskheroes.ability;

import com.fiskheroes.FiskUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

/** Shoots a blob of web: it glues what it hits (a creature or a spot on a wall) for a while. Use with an 'action' condition. */
public class WebShotAbility extends Ability {
    public static final PalladiumProperty<Float> RANGE = new FloatProperty("range").configurable("How far the web flies");
    public static final PalladiumProperty<Integer> WEB_TICKS = new IntegerProperty("web_ticks").configurable("How long the web lasts");

    public WebShotAbility() {
        this.withProperty(ICON, new ItemIcon(Items.COBWEB));
        this.withProperty(RANGE, 28F);
        this.withProperty(WEB_TICKS, 160);
    }

    /** Puts a temporary cobweb at the position if it is free. */
    static void web(ServerLevel level, BlockPos pos, int ticks) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || !level.getBlockState(pos).isAir()) {
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
        FiskUtil.later(ticks, () -> {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                level.removeBlock(pos, false);
            }
        });
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double range = entry.getProperty(RANGE);
        int ticks = entry.getProperty(WEB_TICKS);
        LivingEntity target = FiskUtil.lookedAtLiving(entity, range);
        Vec3 from = entity.getEyePosition().subtract(0, 0.3, 0);
        Vec3 end;
        if (target != null) {
            end = target.position().add(0, target.getBbHeight() / 2, 0);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks / 2, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks / 2, 1));
            web(level, target.blockPosition(), ticks);
            web(level, target.blockPosition().above(), ticks);
        } else {
            BlockHitResult hit = FiskUtil.rayBlock(entity, range);
            end = hit.getType() == HitResult.Type.MISS ? FiskUtil.lookEnd(entity, range) : hit.getLocation();
            if (hit.getType() == HitResult.Type.BLOCK) {
                web(level, hit.getBlockPos().relative(hit.getDirection()), ticks);
            }
        }
        int steps = (int) Math.max(4, from.distanceTo(end) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(end, i / (double) steps);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 1.0F, 1.8F);
    }

    @Override
    public String getDocumentationDescription() {
        return "Shoots a web that slows what it hits and leaves a temporary cobweb.";
    }
}
