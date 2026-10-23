package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.PollutedWater;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium draws every fluid with its own renderer, so Pollution of the Realms' fix for the vanilla one never ran: a
 * polluted patch on the sea took clean water next to it as empty, sank its surface to the floor of the block at the
 * edges and dragged the clean water's corners down with it. Seen from above that was a sunken square with see-through
 * gaps around it. Sodium's surface heights now treat water and polluted water as one fluid, as the vanilla fix does.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumPollutedWaterHeightMixin {
    @Redirect(method = "fluidHeight(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/Fluid;isSame(Lnet/minecraft/world/level/material/Fluid;)Z"))
    private boolean create_galore_bridge$waterJoinsPolluted(Fluid self, Fluid other) {
        return self.isSame(other)
                || self.is(PollutedWater.FLUID_TAG) && other.is(FluidTags.WATER)
                || other.is(PollutedWater.FLUID_TAG) && self.is(FluidTags.WATER);
    }
}
