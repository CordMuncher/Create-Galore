package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every later flow of water and lava. See {@link FluidFlowLoadedMixin}. Lava that was told to harden later
 * ({@link FluidMixingLoadedMixin}) does that here first, once its neighbours are loaded.
 */
@Mixin(ServerLevel.class)
public abstract class FluidTickLoadedMixin {
    @WrapOperation(method = "tickFluid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/material/FluidState;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void create_galore_bridge$flowWhenNeighboursReady(FluidState fluid, Level level, BlockPos pos, Operation<Void> original) {
        if (!ChunkLoadGuard.readyAround(level, pos, ChunkLoadGuard.FLUID_REACH)) {
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        } else if (!(ChunkLoadGuard.takeMixLater(level, pos) && FluidInteractionRegistry.canInteract(level, pos))) {
            original.call(fluid, level, pos);
        }
    }
}
