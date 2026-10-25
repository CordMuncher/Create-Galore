package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lava turning to stone where water touches it, at the edge of the loaded area. When water flows onto a lava seam
 * (deep caves, where world generation leaves water and lava side by side), the first lava block hardens, that wakes
 * the next one along the seam, and so on, all in one go. A long seam walked straight past the reach of
 * {@link FluidFlowLoadedMixin} into a chunk that was still generating, and the server thread waited for it: a 3.9 s
 * freeze while exploring (pack 0.4.83). Now a lava block whose neighbours aren't all loaded yet waits for its next
 * tick (a second and a half for lava) and hardens then, the way it would have; the seam carries on from there once the
 * chunk is ready. Everywhere else lava and water mix exactly as before.
 */
@Mixin(LiquidBlock.class)
public abstract class FluidMixingLoadedMixin {
    @WrapOperation(method = {"onPlace", "neighborChanged"}, at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry;canInteract(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean create_galore_bridge$mixWhenNeighboursReady(Level level, BlockPos pos, Operation<Boolean> original) {
        if (level instanceof ServerLevel && !ChunkLoadGuard.readyAround(level, pos, ChunkLoadGuard.MIXING_REACH)) {
            // Not mixing now: the block schedules its fluid tick as usual, and FluidTickLoadedMixin mixes it then.
            ChunkLoadGuard.mixLater(level, pos);
            return false;
        }
        return original.call(level, pos);
    }
}
