package com.fiskheroes.ability;

import com.fiskheroes.FiskUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;
import net.threetag.palladium.util.property.FloatProperty;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.StringProperty;

/** Calls a lightning bolt down where you look, with a shock that hurts everything around it. Use with an 'action' condition. */
public class LightningStrikeAbility extends Ability {
    public static final PalladiumProperty<Float> RANGE = new FloatProperty("range").configurable("How far you can strike");
    public static final PalladiumProperty<Float> DAMAGE = new FloatProperty("damage").configurable("Extra damage to everything within 3 blocks of the bolt");
    public static final PalladiumProperty<String> REQUIRES = new StringProperty("requires_item").configurable("Item that must be in a hand (empty for none)");

    public LightningStrikeAbility() {
        this.withProperty(ICON, new ItemIcon(Items.LIGHTNING_ROD));
        this.withProperty(RANGE, 80F);
        this.withProperty(DAMAGE, 10F);
        this.withProperty(REQUIRES, "");
    }

    /** Whether the holder has the item the ability asks for (or it asks for none). */
    static boolean hasRequiredItem(LivingEntity entity, String id) {
        if (id.isEmpty()) {
            return true;
        }
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return entity.getMainHandItem().is(item) || entity.getOffhandItem().is(item);
    }

    static void strike(ServerLevel level, LivingEntity caster, Vec3 at, float damage) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at);
            if (caster instanceof ServerPlayer player) {
                bolt.setCause(player);
            }
            level.addFreshEntity(bolt);
        }
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(3), e -> e != caster && e.isAlive())) {
            other.hurt(FiskUtil.attack(level, caster), damage);
        }
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!hasRequiredItem(entity, entry.getProperty(REQUIRES))) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.fiskheroes.need_item",
                        BuiltInRegistries.ITEM.get(new ResourceLocation(entry.getProperty(REQUIRES))).getDescription()), true);
            }
            return;
        }
        LivingEntity target = FiskUtil.lookedAtLiving(entity, entry.getProperty(RANGE));
        Vec3 at = target != null ? target.position() : FiskUtil.rayEnd(entity, entry.getProperty(RANGE));
        strike(level, entity, at, entry.getProperty(DAMAGE));
    }

    @Override
    public String getDocumentationDescription() {
        return "Strikes lightning where the holder looks.";
    }
}
