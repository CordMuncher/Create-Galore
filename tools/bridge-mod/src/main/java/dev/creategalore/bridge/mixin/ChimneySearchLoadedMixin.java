package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.forge.world.WorldSearch;
import dev.creategalore.bridge.ChunkLoadGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced Chimneys follows every smoke source (campfires, furnaces and the like, also the ones in villages and
 * camps that world generation placed) through up to 81 connected chimney blocks every few seconds, reading blocks in
 * whatever chunk the search reaches. At the edge of the loaded area that chunk can still be generating, and the server
 * thread waited for it: freezes of 2 to 6 s while exploring. A block in a chunk that isn't ready now ends the search
 * there for this round, as if nothing were connected; the next round, a few seconds later, sees it once it's loaded.
 */
@Mixin(value = WorldSearch.TileNeighbors.class, remap = false)
public abstract class ChimneySearchLoadedMixin {
    @Inject(method = {"isValidPath", "isValidBlock"}, at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$loadedOnly(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (((ChimneySearchLevelAccessor) this).create_galore_bridge$level() instanceof Level level && !ChunkLoadGuard.ready(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
