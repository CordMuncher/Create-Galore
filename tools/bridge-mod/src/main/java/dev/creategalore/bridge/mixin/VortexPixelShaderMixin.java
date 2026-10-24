package dev.creategalore.bridge.mixin;

import com.maxenonyme.vortexengine.light.PixelLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Create Deep Seas' Vortex Engine names a variable "packed" in its PIXEL light style shader (vortex_pixel.fsh), and
 * "packed" is a reserved word in GLSL. Strict compilers (NVIDIA, Mesa) refuse the shader on every start ("Couldn't
 * load the Vortex pixel light shader") and the PIXEL light style draws no coloured light at all. Load the shader from
 * the Bridge's own copy (assets/create_galore_bridge/shaders/core/vortex_pixel.*), the same code with that one
 * variable renamed.
 */
@Mixin(value = PixelLights.class, remap = false)
public abstract class VortexPixelShaderMixin {
    @ModifyArg(
        method = "onRegisterShaders",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;fromNamespaceAndPath(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
        index = 0)
    private static String create_galore_bridge$fixedShader(String namespace) {
        return "create_galore_bridge";
    }
}
