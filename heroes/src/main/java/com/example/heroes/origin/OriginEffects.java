package com.example.heroes.origin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.SuperpowerUtil;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the character sheet does in the game.
 * <ul>
 * <li>STR: melee damage (+0.5 per point of modifier) and attack knockback</li>
 * <li>DEX: movement speed (+2% per point) and a chance to dodge attacks (3% per point, up to 25%)</li>
 * <li>CON: maximum health (+2 per point) and +1 per level above the first</li>
 * <li>INT: more XP (+5% per point) and faster ability cooldowns (+3% per point)</li>
 * <li>WIS: harmful effects wear off faster (-10% duration per point, see the LivingEntity mixin) and a warning when danger is near</li>
 * <li>CHA: cheaper (or dearer) villager trades (see the Villager mixin) and hostile mobs sometimes lose interest in you</li>
 * <li>Race and subtype: attribute bonuses and traits such as slower hunger (Player mixin) and faster natural healing</li>
 * </ul>
 */
public final class OriginEffects {
    private static final String PREFIX = "heroes_origin";
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private OriginEffects() {
    }

    /** Removes every attribute modifier this system added, then adds the current ones. Safe to call at any time. */
    public static void apply(ServerPlayer player) {
        clear(player);
        Sheet sheet = OriginApi.get(player);
        if (sheet == null) {
            return;
        }
        Race raceForHeight = sheet.raceDef();
        if (sheet.height <= 0 && raceForHeight != null && raceForHeight.heightMax > 0) {
            sheet.rollHeight(player.getRandom()); // characters created before heights existed get one now
            OriginData.get(player.server).setDirty();
        }
        boolean morphed = !sheet.morphKind.isEmpty();
        OriginScale.setHeight(player, morphed && sheet.morphHeight > 0 ? sheet.morphHeight : sheet.height);
        ResourceLocation own = sheet.raceDef() == null ? null : sheet.raceDef().models.get(sheet.gender);
        ResourceLocation extra = morphed && sheet.morphKind.equals("model") ? new ResourceLocation(sheet.morphTarget) : null;
        syncModel(player, own, extra, morphed);
        int str = sheet.mod(Ability5e.STR), dex = sheet.mod(Ability5e.DEX), con = sheet.mod(Ability5e.CON);
        add(player, Attributes.MAX_HEALTH, "con", con * 2.0 + (sheet.level() - 1), AttributeModifier.Operation.ADDITION);
        add(player, Attributes.ATTACK_DAMAGE, "str", str * 0.5, AttributeModifier.Operation.ADDITION);
        add(player, Attributes.ATTACK_KNOCKBACK, "str_kb", str * 0.1, AttributeModifier.Operation.ADDITION);
        add(player, Attributes.MOVEMENT_SPEED, "dex", dex * 0.02, AttributeModifier.Operation.MULTIPLY_BASE);
        Race race = sheet.raceDef();
        if (race != null) {
            int i = 0;
            for (Race.AttributeBonus bonus : race.attributes) {
                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(bonus.attribute());
                if (attribute != null) {
                    add(player, attribute, "race" + i++, bonus.amount(), operation(bonus.operation()));
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void clear(ServerPlayer player) {
        if (OriginApi.get(player) == null) {
            OriginScale.setHeight(player, 0);
        }
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getName().startsWith(PREFIX)) {
                    instance.removeModifier(modifier.getId());
                }
            }
        }
    }

    /**
     * Wears the model power for this race and gender (plus, while shapeshifted into another race's model, that model's power)
     * and takes off every other race model. The marker power {@code races:morphed} is on while shapeshifted, so a shapeshifter's
     * own model turns itself off.
     */
    public static void syncModel(ServerPlayer player, ResourceLocation own, ResourceLocation extra, boolean morphed) {
        for (Race race : Races.all()) {
            for (ResourceLocation id : race.models.values()) {
                boolean wanted = id.equals(own) || id.equals(extra);
                boolean has = PowerUtil.hasPower(player, id);
                if (wanted && !has) {
                    SuperpowerUtil.addSuperpower(player, id);
                } else if (!wanted && has) {
                    SuperpowerUtil.removeSuperpower(player, id);
                }
            }
        }
        boolean marked = PowerUtil.hasPower(player, com.example.heroes.origin.morph.Morph.MARKER);
        if (morphed && !marked) {
            SuperpowerUtil.addSuperpower(player, com.example.heroes.origin.morph.Morph.MARKER);
        } else if (!morphed && marked) {
            SuperpowerUtil.removeSuperpower(player, com.example.heroes.origin.morph.Morph.MARKER);
        }
    }

    private static void add(ServerPlayer player, Attribute attribute, String key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || amount == 0) {
            return;
        }
        String name = PREFIX + "_" + key;
        UUID id = UUID.nameUUIDFromBytes((name + ":" + BuiltInRegistries.ATTRIBUTE.getKey(attribute)).getBytes());
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    private static AttributeModifier.Operation operation(String name) {
        return switch (name) {
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
    }

    // ------------------------------------------------------------------ per-tick effects

    public static void tick(ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null) {
            return;
        }
        if (player.tickCount % 200 == 0) {
            apply(player); // also restores the modifiers after a respawn
        }
        // Race and subtype traits: faster natural healing.
        double regen = sheet.trait("regen_hp_per_10s");
        if (regen > 0 && player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth() && player.tickCount % 200 < 20) {
            player.heal((float) regen);
        }
        // INT: ability cooldowns tick down a little faster.
        int intMod = sheet.mod(Ability5e.INT);
        if (intMod > 0) {
            for (AbilityInstance instance : AbilityUtil.getInstances(player)) {
                if (instance.cooldown > 0 && player.getRandom().nextFloat() < 0.03F * intMod) {
                    instance.cooldown--;
                }
            }
        }
        if (player.tickCount % 20 == 0) {
            perceive(player, sheet);
        }
        if (player.tickCount % 40 == 0) {
            charm(player, sheet);
        }
    }

    /** WIS: with a modifier of +2 or more you sense hostile creatures close by. */
    private static void perceive(ServerPlayer player, Sheet sheet) {
        int wis = sheet.mod(Ability5e.WIS);
        if (wis < 2) {
            return;
        }
        double range = 8 + 2 * wis;
        long now = player.level().getGameTime();
        boolean near = !player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() != player && !m.hasLineOfSight(player)).isEmpty();
        if (near && now - LAST_WARNING.getOrDefault(player.getUUID(), -1000L) > 300) {
            LAST_WARNING.put(player.getUUID(), now);
            player.displayClientMessage(Component.literal("§7You sense something hostile close by."), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.4F, 1.0F);
        }
    }

    /** CHA: hostile mobs that have not been provoked sometimes lose interest in a charming player. */
    private static void charm(ServerPlayer player, Sheet sheet) {
        int cha = sheet.mod(Ability5e.CHA);
        if (cha <= 0) {
            return;
        }
        List<Mob> mobs = player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                m -> m instanceof Enemy && m.getTarget() == player && m.getLastHurtByMob() != player);
        for (Mob mob : mobs) {
            if (player.getRandom().nextFloat() < 0.08F * cha) {
                mob.setTarget(null);
            }
        }
    }

    /** DEX: a chance to dodge an attack outright. Returns true when the hit is avoided. */
    public static boolean dodges(ServerPlayer player) {
        Sheet sheet = OriginApi.get(player);
        if (sheet == null) {
            return false;
        }
        int dex = sheet.mod(Ability5e.DEX);
        if (dex > 0 && player.getRandom().nextFloat() < Math.min(0.25F, 0.03F * dex)) {
            player.displayClientMessage(Component.literal("§bDodged!"), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F);
            return true;
        }
        return false;
    }
}
