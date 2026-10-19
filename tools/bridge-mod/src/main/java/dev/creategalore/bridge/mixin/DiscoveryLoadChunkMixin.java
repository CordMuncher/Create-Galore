package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import me.corvino.aeronauticsdiscovery.assembly.steps.LoadChunkStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** See {@link ChunkLoadGuard}: Discovery's assembly step waits for its chunks, so their forced load needn't block. */
@Mixin(value = LoadChunkStep.class, remap = false)
public abstract class DiscoveryLoadChunkMixin {
    @WrapOperation(
        method = "forceTickets",
        at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/world/chunk/TicketController;forceChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IIZZ)Z"))
    private boolean create_galore_bridge$deferLoad(TicketController controller, ServerLevel level, BlockPos owner, int x, int z,
                                                   boolean add, boolean ticking, Operation<Boolean> original) {
        ChunkLoadGuard.deferForcedLoads = true;
        try {
            return original.call(controller, level, owner, x, z, add, ticking);
        } finally {
            ChunkLoadGuard.deferForcedLoads = false;
        }
    }
}
