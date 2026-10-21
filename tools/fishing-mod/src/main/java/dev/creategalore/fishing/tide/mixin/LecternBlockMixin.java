/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.network.messages.OpenJournalMsg;
import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LecternBlock.class)
public class LecternBlockMixin {
    @Inject(at = @At(value = "HEAD"), method = "openScreen", cancellable = true)
    public void openItemGui(Level level, BlockPos pos, Player player, CallbackInfo ci) {
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern
                && player instanceof ServerPlayer serverPlayer
                && lectern.getBook().is(TideItems.FISHING_JOURNAL)) {
            Tide.NETWORK.sendToPlayer(new OpenJournalMsg(), serverPlayer);
            player.awardStat(Stats.INTERACT_WITH_LECTERN);
            ci.cancel();
        }
    }
}
