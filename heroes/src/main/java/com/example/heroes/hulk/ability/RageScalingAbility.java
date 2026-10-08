package com.example.heroes.hulk.ability;

import com.example.heroes.hulk.HulkRage;
import com.example.heroes.stones.InfinityStone;
import com.example.heroes.stones.StoneBoost;
import com.example.heroes.hulk.HulkScale;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

import java.util.UUID;

/** Grows Hulk and boosts his stats as rage rises. Reverts everything when disabled or lost. */
public class RageScalingAbility extends Ability {
    private static final UUID HEALTH = UUID.fromString("5d3f1c4e-0a11-4c0e-9c1a-0e5a1b000001");
    private static final UUID DAMAGE = UUID.fromString("5d3f1c4e-0a11-4c0e-9c1a-0e5a1b000002");
    private static final UUID SPEED = UUID.fromString("5d3f1c4e-0a11-4c0e-9c1a-0e5a1b000003");
    private static final UUID KNOCKBACK = UUID.fromString("5d3f1c4e-0a11-4c0e-9c1a-0e5a1b000004");

    public RageScalingAbility() {
        this.withProperty(ICON, new ItemIcon(Items.SLIME_BALL));
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (entity.level().isClientSide) {
            return;
        }
        if (!enabled) {
            clear(entity);
            return;
        }
        float rage = HulkRage.fraction(entity);
        // 1.3x calm -> ~2x at 80% rage -> Titan (kaiju) size near max rage.
        float scale = rage < 0.8F ? 1.3F + 0.875F * rage : 2.0F + (rage - 0.8F) * 5F;
        HulkScale.set(entity, scale);
        // Immortal-style regeneration: heals faster the angrier he is.
        if (entity.tickCount % 20 == 0 && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal((0.5F + 3F * rage) * (float) StoneBoost.mult(entity, InfinityStone.SOUL, 2.0));
        }
        apply(entity, Attributes.MAX_HEALTH, HEALTH, 10 + 30 * rage, AttributeModifier.Operation.ADDITION);
        apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE, 3 + 9 * rage, AttributeModifier.Operation.ADDITION);
        apply(entity, Attributes.MOVEMENT_SPEED, SPEED, 0.1 + 0.3 * rage, AttributeModifier.Operation.MULTIPLY_TOTAL);
        apply(entity, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 0.3 + 0.7 * rage, AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!entity.level().isClientSide) {
            clear(entity);
        }
    }

    private static void clear(LivingEntity entity) {
        HulkScale.set(entity, 1F);
        remove(entity, Attributes.MAX_HEALTH, HEALTH);
        remove(entity, Attributes.ATTACK_DAMAGE, DAMAGE);
        remove(entity, Attributes.MOVEMENT_SPEED, SPEED);
        remove(entity, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK);
    }

    private static void apply(LivingEntity entity, Attribute attribute, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // Quantize so we don't rebuild the modifier every tick.
        double rounded = Math.round(amount * 100.0) / 100.0;
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.getAmount() == rounded) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, "Hulk rage", rounded, op));
        if (attribute == Attributes.MAX_HEALTH && entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static void remove(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
            if (attribute == Attributes.MAX_HEALTH && entity.getHealth() > entity.getMaxHealth()) {
                entity.setHealth(entity.getMaxHealth());
            }
        }
    }

    @Override
    public String getDocumentationDescription() {
        return "Scales size and stats with the 'rage' energy bar of the power.";
    }
}
