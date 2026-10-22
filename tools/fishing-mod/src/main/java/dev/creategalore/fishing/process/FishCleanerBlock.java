package dev.creategalore.fishing.process;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Fish Cleaner: a Cleaning Table with a saw, for a fish line. Fish come in from the back, the sides or the top (a
 * belt, chute, funnel or hopper) and leave from the front (onto a belt, a depot, into a chest, or onto the ground). A
 * fish too big to use whole ({@link FishCleanerBlockEntity#needsCleaning}) is cut up as the Cleaning Table cuts it;
 * every other item passes through untouched. It faces away from the player who placed it, and a shaft runs through it
 * side to side. An empty hand takes back whatever is inside.
 */
public class FishCleanerBlock extends HorizontalKineticBlock implements IBE<FishCleanerBlockEntity> {
    /** The casing and the two rails on top, as the model draws them. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 13, 16), Block.box(2, 13, 2, 14, 14, 14));

    public FishCleanerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection());
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<FishCleanerBlockEntity> getBlockEntityClass() {
        return FishCleanerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FishCleanerBlockEntity> getBlockEntityType() {
        return FishingRegistry.FISH_CLEANER_ENTITY.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FishCleanerBlockEntity cleaner) || cleaner.isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            for (ItemStack stack : cleaner.takeAll()) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
