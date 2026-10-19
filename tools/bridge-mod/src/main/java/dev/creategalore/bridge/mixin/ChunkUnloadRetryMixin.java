package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chunk on its way out that isn't ready to save yet retries its unload over and over. If something needs that
 * chunk again first (a ticket brings it back), the retry keeps going even though the unload can no longer happen.
 * While the server stops, it runs these retries without a time limit, so one such chunk that is still being
 * generated made a test server spin forever on "Stopping the server" after airships had crossed new land.
 * Once the chunk has left the unload list, end its retry: the unload would do nothing anyway, and a new one is
 * scheduled if the chunk is dropped again.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkUnloadRetryMixin {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @WrapOperation(method = "lambda$scheduleUnload$12", remap = false, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkHolder;isReadyForSaving()Z"))
    private boolean create_galore_bridge$stopStaleRetry(ChunkHolder holder, Operation<Boolean> original,
                                                       @Local(argsOnly = true) long pos) {
        return pendingUnloads.get(pos) != holder || original.call(holder);
    }
}
