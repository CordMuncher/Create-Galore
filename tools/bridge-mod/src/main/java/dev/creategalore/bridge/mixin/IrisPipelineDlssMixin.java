package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.Dlss;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: rereads its settings when a shader pack loads, picks each frame's jitter before the pack's uniforms update, and
 * marks where the level frame ends.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisPipelineDlssMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void create_galore_bridge$dlssPipeline(CallbackInfo ci) {
        Dlss.onPipelineCreated();
    }

    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void create_galore_bridge$dlssFrame(CallbackInfo ci) {
        Dlss.beginFrame(SystemTimeUniforms.COUNTER.getAsInt());
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"))
    private void create_galore_bridge$dlssFrameEnd(CallbackInfo ci) {
        Dlss.endFrame();
    }
}
