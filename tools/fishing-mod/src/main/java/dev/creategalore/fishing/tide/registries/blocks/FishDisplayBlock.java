/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.blocks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import dev.creategalore.fishing.tide.registries.blocks.entities.FishDisplayBlockEntity;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.world.ItemInteractionResult;
import com.mojang.serialization.MapCodec;

import java.util.Map;

@SuppressWarnings({"deprecation", "unused"})
public class FishDisplayBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final Map<Direction, VoxelShape> SHAPES_1x1 = Maps.newEnumMap(
            ImmutableMap.of(
                    Direction.NORTH, Block.box(1, 2, 15, 15, 14, 16),
                    Direction.SOUTH, Block.box(1, 2, 0, 15, 14, 1),
                    Direction.EAST, Block.box(0, 2, 1, 1, 14, 15),
                    Direction.WEST, Block.box(15, 2, 1, 16, 14, 15)
            ));
    public static final Map<Direction, VoxelShape> SHAPES_2x1 = Maps.newEnumMap(
            ImmutableMap.of(
                    Direction.NORTH, Block.box(0, 2, 15, 16, 14, 16),
                    Direction.SOUTH, Block.box(0, 2, 0, 16, 14, 1),
                    Direction.EAST, Block.box(0, 2, 0, 1, 14, 16),
                    Direction.WEST, Block.box(15, 2, 0, 16, 14, 16)
            ));
    public static final Map<Direction, VoxelShape> SHAPES_3x1 = Maps.newEnumMap(
            ImmutableMap.of(
                    Direction.NORTH, Block.box(0, 1, 15, 16, 15, 16),
                    Direction.SOUTH, Block.box(0, 1, 0, 16, 15, 1),
                    Direction.EAST, Block.box(0, 1, 0, 1, 15, 16),
                    Direction.WEST, Block.box(15, 1, 0, 16, 15, 16)
            ));
    public static final EnumProperty<FishDisplayShape> SHAPE = EnumProperty.create(
            "fish_display_shape", FishDisplayShape.class, FishDisplayShape.values());

    public FishDisplayBlock(Properties properties) {
        super(properties);
        // Dry by default (Tide left the first state, which is waterlogged, so a /setblock mount stood in water).
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, FishDisplayShape.SHAPE_1x1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, SHAPE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishDisplayBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                Direction opposite = direction.getOpposite();
                blockState = blockState.setValue(FACING, opposite);
                if (blockState.canSurvive(context.getLevel(), context.getClickedPos()))
                    return blockState.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
            }
        }

        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return state.getOptionalValue(SHAPE).orElse(FishDisplayShape.SHAPE_1x1).getShape(state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState state2, LevelAccessor level, BlockPos pos, BlockPos pos2) {
        return dir.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, state2, level, pos, pos2);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston
                && level.getBlockEntity(pos) instanceof FishDisplayBlockEntity display
                && !display.isEmpty())
            popResource(level, pos, display.getDisplayStack());
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FishDisplayBlock::new);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                                       Level level, BlockPos pos,
                                                       Player player, InteractionHand hand,
                                                       BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FishDisplayBlockEntity display) {
            ItemStack displayed = stack.copyWithCount(1);
            if (display.setDisplayStack(displayed)) {
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.COD_FLOP, SoundSource.BLOCKS, 1.0f, 1.0f);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                                        BlockPos pos, Player player,
                                                        BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FishDisplayBlockEntity display) {
            if (!display.isEmpty()) {
                ItemStack stack = display.takeDisplayStack();
                if (!player.addItem(stack)) player.drop(stack, false);
                level.playSound(null, pos, SoundEvents.COD_FLOP, SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof FishDisplayBlockEntity display) {
            if (!display.isEmpty()) return display.getDisplayStack().copy();
        }
        return super.getCloneItemStack(level, pos, state);
    }

}
