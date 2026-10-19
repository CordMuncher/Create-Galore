package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ChunkLoadGuard;
import io.github.bonsaistudi0s.crittersandcompanions.common.handler.LushCaveSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Critters and Companions picks lush-cave spawn spots up to 24 blocks from each player every 2 s and reads blocks
 * there, which generated a missing chunk on the server thread (2 to 3 s freezes when a player outran chunk loading).
 * Spots in chunks that aren't loaded are now passed over; the next try is 2 s later.
 */
@Mixin(value = LushCaveSpawnHandler.class, remap = false)
public abstract class LushCaveSpawnLoadedMixin {
    @Inject(method = "findGround", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$groundOnlyIfLoaded(ServerLevel level, BlockPos candidate, EntityType<?> type,
                                                               CallbackInfoReturnable<BlockPos> cir) {
        if (!ChunkLoadGuard.ready(level, candidate)) cir.setReturnValue(candidate);
    }

    @Inject(method = "isValidSpawnPosition", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$validOnlyIfLoaded(ServerLevel level, BlockPos pos, EntityType<?> type,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (!ChunkLoadGuard.ready(level, pos)) cir.setReturnValue(false);
    }
}
