package com.example.heroes.symbiote.ability;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.threetag.palladium.power.IPowerHolder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.util.icon.ItemIcon;

/** Passive danger sense: the symbiote warns its host when hostile creatures or other players are close. Always on. */
public class SenseAbility extends Ability {
    private static final double RANGE = 14;

    public SenseAbility() {
        this.withProperty(ICON, new ItemIcon(Items.ENDER_EYE));
        this.withProperty(HIDDEN_IN_GUI, true);
        this.withProperty(HIDDEN_IN_BAR, true);
    }

    @Override
    public void tick(LivingEntity entity, AbilityInstance entry, IPowerHolder holder, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        boolean danger = !player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                e -> e != player && e.isAlive() && !SymbioteHost.isHost(e)
                        && (e instanceof Enemy || (e instanceof Player p && !p.isCreative() && !p.isSpectator()))).isEmpty();
        // Warn once when danger appears, then again only after it has been gone for a while.
        long now = player.level().getGameTime();
        if (danger && now - lastWarn(player) > 200) {
            player.displayClientMessage(Component.literal("\u00a78The symbiote shivers: danger close."), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.8F);
            lastWarn.put(player.getUUID(), now);
        } else if (!danger && now - lastWarn(player) > 200) {
            lastWarn.remove(player.getUUID());
        }
    }

    private static final java.util.Map<java.util.UUID, Long> lastWarn = new java.util.concurrent.ConcurrentHashMap<>();

    private static long lastWarn(Player player) {
        return lastWarn.getOrDefault(player.getUUID(), -1000L);
    }

    @Override
    public String getDocumentationDescription() {
        return "Danger sense for symbiote hosts.";
    }
}
