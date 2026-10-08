package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A place to bond with a symbiote on purpose: much better odds than a meteor blob (5% death, 65% bond, 30% perfect host). */
public class BondingAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public BondingAltarBlock() {
        super(Properties.of().strength(50.0F, 1200.0F).sound(SoundType.DEEPSLATE).lightLevel(s -> 7).noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (SymbioteHost.isHost(player)) {
                serverPlayer.displayClientMessage(Component.literal("You already carry a symbiote."), true);
            } else {
                KlyntarEffects.startAltarBond(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
