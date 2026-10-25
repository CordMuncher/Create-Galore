package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.Dlss;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: cg_dlss_active (1 while DLSS takes the pack's frame), cg_dlss_jitter_x/y (this frame's jitter in pixels) and
 * cg_dlss_object_motion (0 while testing without the motion of things that move by themselves),
 * cg_dlss_residual_albedo (1 in the residual test view), taau_render_scale (the render size over the window's, per
 * axis, when the pack upscales) and cg_mip_bias (the textures' mip bias) for every program and for the pack's custom
 * uniforms (see {@link Dlss}). Packs that do not use them never see them.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisDlssUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"))
    private static void create_galore_bridge$dlssUniforms(UniformHolder uniforms, IdMap idMap, PackDirectives directives,
                                                          FrameUpdateNotifier updateNotifier, CallbackInfo ci) {
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_dlss_active", (FloatSupplier) () -> Dlss.shaderActive() ? 1.0f : 0.0f)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_dlss_jitter_x", (FloatSupplier) Dlss::jitterX)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_dlss_jitter_y", (FloatSupplier) Dlss::jitterY)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_dlss_object_motion", (FloatSupplier) () -> Dlss.objectMotion() ? 1.0f : 0.0f)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_dlss_residual_albedo", (FloatSupplier) () -> Dlss.residualAlbedo() ? 1.0f : 0.0f)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "taau_render_scale", Dlss::renderScale)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cg_mip_bias", (FloatSupplier) Dlss::mipBias);
    }
}
