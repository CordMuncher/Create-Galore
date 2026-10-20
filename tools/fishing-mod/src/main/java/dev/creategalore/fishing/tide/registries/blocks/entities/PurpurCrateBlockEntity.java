/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.blocks.entities;

import dev.creategalore.fishing.tide.registries.TideBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class PurpurCrateBlockEntity extends LootCrateBlockEntity {
    public PurpurCrateBlockEntity(BlockPos pos, BlockState state) {
        super(TideBlockEntities.PURPUR_CRATE, pos, state);
    }
}
