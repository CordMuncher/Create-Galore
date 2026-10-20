/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harvesting a full hive with shears sometimes drops the Honeycomb Fishing Rod, as in Tide. */
@Mixin(BeehiveBlock.class)
public class BeehiveBlockMixin {
    @Inject(method = "dropHoneycomb", at = @At("TAIL"))
    private static void dropHoneycomb(Level level, BlockPos pos, CallbackInfo ci) {
        if (level.getRandom().nextFloat() < 0.05f)
            Block.popResource(level, pos, new ItemStack(TideItems.HONEYCOMB_FISHING_ROD));
    }
}
