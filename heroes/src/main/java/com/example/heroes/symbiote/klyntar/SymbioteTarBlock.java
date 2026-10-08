package com.example.heroes.symbiote.klyntar;

import com.example.heroes.symbiote.SymbioteBlobEntity;
import com.example.heroes.symbiote.SymbioteCreature;
import com.example.heroes.symbiote.SymbioteEntities;
import com.example.heroes.symbiote.SymbioteHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pools of living tar: things sink, move at a crawl and are slowly eaten, and now and then the tar rears up as a blob. */
public class SymbioteTarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);

    public SymbioteTarBlock() {
        super(Properties.of().strength(100.0F).noCollission().noLootTable().sound(SoundType.SLIME_BLOCK).randomTicks());
    }

    public static boolean immune(Entity entity) {
        return entity instanceof SymbioteCreature || (entity instanceof LivingEntity living && SymbioteHost.isHost(living))
                || (entity instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (immune(entity) || !(entity instanceof LivingEntity living)) {
            return;
        }
        entity.makeStuckInBlock(state, new Vec3(0.35, 0.2, 0.35));
        if (!level.isClientSide && entity.tickCount % 20 == 0) {
            living.hurt(level.damageSources().magic(), 1.0F);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(40) == 0 && level.getBlockState(pos.above()).isAir()
                && level.getEntitiesOfClass(SymbioteBlobEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(14)).isEmpty()) {
            SymbioteBlobEntity blob = SymbioteEntities.SYMBIOTE_BLOB.create(level);
            if (blob != null) {
                blob.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, random.nextFloat() * 360F, 0F);
                level.addFreshEntity(blob);
                level.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
        }
    }
}
