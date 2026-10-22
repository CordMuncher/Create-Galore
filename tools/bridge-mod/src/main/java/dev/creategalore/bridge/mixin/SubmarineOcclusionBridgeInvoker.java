package dev.creategalore.bridge.mixin;

import com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Deep Seas' own sampler count and texture unit limit, for SubmarineHullUnitsMixin. */
@Mixin(value = SodiumWaterOcclusionBridge.class, remap = false)
public interface SubmarineOcclusionBridgeInvoker {
    @Invoker("samplerUnits")
    static int create_galore_bridge$samplerUnits(int program) {
        throw new AssertionError();
    }

    @Invoker("maxTextureUnits")
    static int create_galore_bridge$maxTextureUnits() {
        throw new AssertionError();
    }
}
