package com.example.heroes.stones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The themed container of a stone (the Orb, the Tesseract, ...). It can be placed in the world; a filled one glows
 * and holds its stone. Right-click a filled one to take the stone out, an empty one with the stone to put it back.
 * Broken, it drops itself with the stone still inside.
 */
public class StoneContainerBlock extends Block {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public final InfinityStone stone;
    private final VoxelShape shape;

    public StoneContainerBlock(InfinityStone stone, VoxelShape shape) {
        super(Properties.of().strength(3.0F, 1200.0F).noOcclusion().sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(FILLED) ? 12 : 0));
        this.stone = stone;
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FILLED, false).setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        boolean filled = stack.getTag() != null && stack.getTag().getBoolean("Filled");
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(FILLED, filled);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(FILLED)) {
            if (!level.isClientSide) {
                if (stone == InfinityStone.SOUL && level instanceof net.minecraft.server.level.ServerLevel serverLevel && !SoulSacrifice.pay(serverLevel, pos, player)) {
                    return InteractionResult.CONSUME;
                }
                level.setBlock(pos, state.setValue(FILLED, false), 3);
                player.getInventory().placeItemBackInInventory(new ItemStack(StoneItems.get(stone)));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.getItem() == StoneItems.get(stone)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(FILLED, true), 3);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(StoneContainers.stack(stone, state.getValue(FILLED)));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return StoneContainers.stack(stone, state.getValue(FILLED));
    }
}
