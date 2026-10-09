package com.fiskheroes.ability;

import com.fiskheroes.FiskHeroes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Makes the holder sick while kryptonite is in their inventory or lying within a few blocks. Superman's powers key off that sickness. */
public class KryptoniteWeaknessAbility extends Ability {
    private static final ResourceLocation KRYPTONITE = new ResourceLocation(FiskHeroes.MOD_ID, "kryptonite");

    public KryptoniteWeaknessAbility() {
        this.withProperty(ICON, new ItemIcon(Items.EMERALD));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || entity.level().isClientSide || entity.tickCount % 10 != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Item kryptonite = BuiltInRegistries.ITEM.get(KRYPTONITE);
        if (kryptonite == Items.AIR) {
            return;
        }
        boolean near = entity instanceof Player player && (player.getInventory().countItem(kryptonite) > 0)
                || !level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(6), item -> item.getItem().is(kryptonite)).isEmpty();
        if (near) {
            entity.addEffect(new MobEffectInstance(FiskHeroes.KRYPTONITE_POISONING, 60, 0, false, true, true));
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Kryptonite makes the holder sick.";
    }
}
