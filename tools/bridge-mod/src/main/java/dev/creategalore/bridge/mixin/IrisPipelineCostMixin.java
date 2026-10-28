package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.CostMeter;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cost meter: follows Iris's gbuffer phases, and rereads the pack's cost meter groups when a shader pack loads.
 * Iris leaves a phase only when the next one starts or removePhaseIfNeeded runs (setPhase(NONE) just asks for that).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisPipelineCostMixin {
    @Shadow private boolean shouldRemovePhase;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void create_galore_bridge$costPipeline(CallbackInfo ci) {
        CostMeter.onPipelineCreated();
    }

    @Inject(method = "setPhase", at = @At("HEAD"))
    private void create_galore_bridge$costPhase(WorldRenderingPhase phase, CallbackInfo ci) {
        if (phase != WorldRenderingPhase.NONE) {
            CostMeter.phase(phase.ordinal(), phase.name());
        }
    }

    @Inject(method = "removePhaseIfNeeded", at = @At("HEAD"))
    private void create_galore_bridge$costNoPhase(CallbackInfo ci) {
        if (shouldRemovePhase) {
            CostMeter.phase(0, null);
        }
    }
}
