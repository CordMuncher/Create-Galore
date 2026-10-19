package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create Deep Seas reads the world's water around every ship each tick (its High Seas "is this boat afloat" check
 * runs for airships too) and asks for the chunk without loading it. But the server still waits when that chunk has
 * a ticket and is mid-generation, which is the case under a flyover airship crossing new land (1 to 3 s freezes,
 * over and over). Take the chunk only if it is ready now; otherwise there is no water there yet, as for an
 * unloaded chunk. Only on the server thread: its background compartment scans get chunks handed over as before.
 */
@Mixin(value = CompartmentTracker.class, remap = false)
public abstract class SubmarineFluidNoWaitMixin {
    @WrapOperation(method = "realFluidState(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private static ChunkAccess create_galore_bridge$readyChunkOnly(Level level, int x, int z, ChunkStatus status, boolean load,
                                                                  Operation<ChunkAccess> original) {
        if (!load && level instanceof ServerLevel server && server.getServer().isSameThread()) return level.getChunkSource().getChunkNow(x, z);
        return original.call(level, x, z, status, load);
    }
}
