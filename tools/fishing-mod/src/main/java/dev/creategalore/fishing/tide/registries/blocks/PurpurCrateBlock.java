/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.blocks;

import dev.creategalore.fishing.tide.registries.TideBlockEntities;
import dev.creategalore.fishing.tide.registries.blocks.entities.LootCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PurpurCrateBlock extends AbstractLootCrateBlock<LootCrateBlockEntity> {
    public PurpurCrateBlock(Properties properties) {
        super(properties, () -> TideBlockEntities.PURPUR_CRATE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LootCrateBlockEntity(TideBlockEntities.PURPUR_CRATE, pos, state);
    }
}
