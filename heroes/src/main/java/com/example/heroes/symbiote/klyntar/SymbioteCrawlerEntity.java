package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteCreature;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/** A fast, wall-climbing symbiote spider: weak alone, dangerous in a pack. */
public class SymbioteCrawlerEntity extends Spider implements SymbioteCreature {
    public SymbioteCrawlerEntity(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }
}
