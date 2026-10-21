package dev.creategalore.fishing.machine;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The Fuel Reel Drive: copper casing with an engine and an exhaust stack at the back, a rod bracket on top. Takes a
 * rod like the other drives; furnace fuel goes into its fuel slot and a bucket of liquid fuel into its tank by hand,
 * bait into its bait slot. Sneaking with an empty hand takes the bait and fuel back out.
 */
public class FuelReelDriveBlock extends HorizontalDirectionalBlock implements IBE<FuelReelDriveBlockEntity>, IWrenchable {
    public static final MapCodec<FuelReelDriveBlock> CODEC = simpleCodec(FuelReelDriveBlock::new);

    public FuelReelDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public Class<FuelReelDriveBlockEntity> getBlockEntityClass() {
        return FuelReelDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FuelReelDriveBlockEntity> getBlockEntityType() {
        return FishingRegistry.FUEL_REEL_DRIVE_ENTITY.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FuelReelDriveBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (DriveUse.use(be, player, hand)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (FluidUtil.getFluidHandler(stack).isPresent()) {
            return FluidUtil.interactWithFluidHandler(player, hand, be.tank()) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (DriveUse.insert(be.fuelSlot(), 0, stack, player, level.isClientSide)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FuelReelDriveBlockEntity be)) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && DriveUse.takeAll(be.fuelSlot(), player, level.isClientSide)) {
            DriveUse.takeAll(be.drive().bait(), player, level.isClientSide);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return DriveUse.use(be, player, InteractionHand.MAIN_HAND) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
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

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        IBE.onRemove(state, level, pos, newState);
    }
}
