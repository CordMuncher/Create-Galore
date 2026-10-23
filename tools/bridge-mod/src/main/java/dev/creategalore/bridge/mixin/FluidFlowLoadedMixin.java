package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Water (and lava) flowing at the edge of the loaded area. When it flowed over a chunk's edge it woke the blocks
 * around where it landed, and when the next chunk was still being generated (players sailing into new terrain: chunks
 * there start ticking a moment before their neighbours are done), the server thread stopped and waited for it: freezes
 * of 2 to 28 s, one after another, while exploring by sea. Both the first flow of freshly generated water and every
 * later flow now wait their turn instead: when a chunk within {@link ChunkLoadGuard#FLUID_REACH} blocks isn't ready,
 * the flow is tried again after the fluid's normal delay (a quarter second for water). Everywhere else water flows
 * exactly as before.
 */
@Mixin(LevelChunk.class)
public abstract class FluidFlowLoadedMixin {
    @WrapOperation(method = "postProcessGeneration", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void create_galore_bridge$flowWhenNeighboursReady(FluidState fluid, Level level, BlockPos pos, Operation<Void> original) {
        if (level instanceof ServerLevel && !ChunkLoadGuard.readyAround(level, pos, ChunkLoadGuard.FLUID_REACH)) {
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        } else {
            original.call(fluid, level, pos);
        }
    }
}
