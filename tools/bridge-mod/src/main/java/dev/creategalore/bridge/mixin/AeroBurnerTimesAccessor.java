package dev.creategalore.bridge.mixin;

import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads a burner's flame clock and strength, the values Aeronautics hands its flame shader, for BurnerFlame. */
@Mixin(value = HotAirBurnerBlockEntity.class, remap = false)
public interface AeroBurnerTimesAccessor {
    @Accessor("lastRenderTime")
    double create_galore_bridge$lastRenderTime();

    @Accessor("renderTime")
    double create_galore_bridge$renderTime();

    @Invoker("getFlameIntensity")
    float create_galore_bridge$flameIntensity(float partialTicks);
}
