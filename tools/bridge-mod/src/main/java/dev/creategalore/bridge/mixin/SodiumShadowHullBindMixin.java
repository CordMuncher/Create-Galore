package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipShadowWater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands each terrain pass's program the nearby ship hulls once it is bound (see ShipShadowWater). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShadowHullBindMixin {
    @Inject(method = "begin", at = @At("TAIL"))
    private void create_galore_bridge$hulls(@Coerce Object pass, CallbackInfo ci) {
        ShipShadowWater.beginTerrain();
    }
}
