package com.fiskheroes.ability;

import com.fiskheroes.FiskUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.IntegerProperty;
import net.threetag.palladium.util.property.PalladiumProperty;

/** Turns the weather to thunder and rains lightning around the holder. Use with an 'action' condition. */
public class StormAbility extends Ability {
    public static final PalladiumProperty<Integer> BOLTS = new IntegerProperty("bolts").configurable("Number of lightning bolts");
    public static final PalladiumProperty<Float> RADIUS = new FloatProperty("radius").configurable("Radius the bolts land in");
    public static final PalladiumProperty<Float> DAMAGE = new FloatProperty("damage").configurable("Shock damage around each bolt");

    public StormAbility() {
        this.withProperty(ICON, new ItemIcon(Items.TRIDENT));
        this.withProperty(BOLTS, 10);
        this.withProperty(RADIUS, 20F);
        this.withProperty(DAMAGE, 8F);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ServerLevel overworld = level.getServer().overworld();
        overworld.setWeatherParameters(0, 6000, true, true);
        level.playSound(null, entity.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.6F);
        int bolts = entry.getProperty(BOLTS);
        double radius = entry.getProperty(RADIUS);
        float damage = entry.getProperty(DAMAGE);
        for (int i = 0; i < bolts; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 4 + level.random.nextDouble() * (radius - 4);
            double x = entity.getX() + Math.cos(angle) * dist;
            double z = entity.getZ() + Math.sin(angle) * dist;
            FiskUtil.later(5 + i * 4, () -> {
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                LightningStrikeAbility.strike(level, entity, new Vec3(x, y, z), damage);
            });
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Summons a thunderstorm of lightning bolts around the holder.";
    }
}
