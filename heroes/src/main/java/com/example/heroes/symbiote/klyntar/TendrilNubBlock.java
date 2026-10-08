package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small black tendril sprouting from the ground. Step on it and it grabs you and drags you towards the hive. */
public class TendrilNubBlock extends Block {
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 7, 13);

    public TendrilNubBlock() {
        super(Properties.of().strength(0.5F).noCollission().noOcclusion().sound(SoundType.SCULK).noLootTable());
        registerDefaultState(stateDefinition.any().setValue(ARMED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !state.getValue(ARMED) || !(entity instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || SymbioteHost.isHost(player)) {
            return;
        }
        level.setBlock(pos, state.setValue(ARMED, false), 3);
        level.scheduleTick(pos, this, 200);
        KlyntarEffects.startDrag(player, (ServerLevel) level, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ARMED)) {
            level.setBlock(pos, state.setValue(ARMED, true), 3); // re-arms
        }
    }
}
