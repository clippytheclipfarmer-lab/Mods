package com.example.heroes.mixin;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A ringing bell is a sonic attack: it hurts every symbiote within 16 blocks. */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
    @Inject(method = "attemptToRing(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("RETURN"))
    private void heroes$bellHurtsSymbiotes(Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && level instanceof ServerLevel serverLevel) {
            SymbioteHost.sonicHit(serverLevel, Vec3.atCenterOf(pos), 16.0, 2.0F);
        }
    }
}
