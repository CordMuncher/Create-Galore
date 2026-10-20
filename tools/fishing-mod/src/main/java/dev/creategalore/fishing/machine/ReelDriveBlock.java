package dev.creategalore.fishing.machine;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Basic and Geared Reel Drives: a casing with a shaft running through it side to side, and a rod bracket on top
 * pointing the way the drive faces (away from the player who placed it). Shafts connect on both sides, so a row of
 * drives along a jetty or a ship's rail runs off one shaft. Right-click with a rod to set it in, with an empty hand
 * to take it out; the Geared drive also takes bait (or a Live-Bait Bucket) into its bait slot, and an empty hand while
 * sneaking takes that back out.
 */
public class ReelDriveBlock extends HorizontalKineticBlock implements IBE<ReelDriveBlockEntity> {
    private final boolean geared;

    public ReelDriveBlock(Properties properties, boolean geared) {
        super(properties);
        this.geared = geared;
    }

    public boolean geared() {
        return geared;
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
    public Class<ReelDriveBlockEntity> getBlockEntityClass() {
        return ReelDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ReelDriveBlockEntity> getBlockEntityType() {
        return FishingRegistry.REEL_DRIVE_ENTITY.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof DriveMount be && DriveUse.use(be, player, hand)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof DriveMount be && DriveUse.use(be, player, InteractionHand.MAIN_HAND)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof DriveMount be && be.mounted().pulsing() ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DriveMount be ? be.mounted().comparator(be.mounted().hook()) : 0;
    }
}
