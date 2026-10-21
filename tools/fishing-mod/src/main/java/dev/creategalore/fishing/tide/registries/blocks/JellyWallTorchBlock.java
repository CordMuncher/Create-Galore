/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.blocks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class JellyWallTorchBlock extends WallTorchBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public JellyWallTorchBlock(Properties properties) {
        super(ParticleTypes.END_ROD, properties);

    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateBuilder) {
        super.createBlockStateDefinition(stateBuilder);
        stateBuilder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placementState = super.getStateForPlacement(context);
        if (placementState == null) return null;
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        if (fluidstate.is(Fluids.WATER)) return placementState.setValue(WATERLOGGED, true);
        else return placementState.setValue(WATERLOGGED, false);
    }

    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState blockState) {
        return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
    }
}
