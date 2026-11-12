package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipShadowWater;
import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the ship hull test to the shader pack's terrain shadow programs, so the sea inside a hull stays out of the sun's
 * shadow map as it stays out of the view (see ShipShadowWater). Programs with geometry or tessellation stages, and
 * shapes it doesn't know, are left as the pack wrote them.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class IrisShadowHullPatchMixin {
    @Inject(method = "patchSodium", at = @At("RETURN"), cancellable = true)
    private static void create_galore_bridge$hullTest(String name, String vertex, String geometry, String tessControl, String tessEval,
                                                      String fragment, AlphaTest alpha,
                                                      Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
                                                      CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        if (name == null || !name.startsWith("shadow")) return;
        Map<PatchShaderType, String> shaders = cir.getReturnValue();
        if (shaders == null || shaders.get(PatchShaderType.GEOMETRY) != null || shaders.get(PatchShaderType.TESS_CONTROL) != null
                || shaders.get(PatchShaderType.TESS_EVAL) != null) return;
        String[] patched = ShipShadowWater.patch(shaders.get(PatchShaderType.VERTEX), shaders.get(PatchShaderType.FRAGMENT));
        if (patched == null) return;
        Map<PatchShaderType, String> copy = new HashMap<>(shaders);
        copy.put(PatchShaderType.VERTEX, patched[0]);
        copy.put(PatchShaderType.FRAGMENT, patched[1]);
        cir.setReturnValue(copy);
    }
}
