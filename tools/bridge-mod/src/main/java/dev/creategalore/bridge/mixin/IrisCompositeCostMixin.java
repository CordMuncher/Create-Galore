package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.CostMeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cost meter: times each composite pass (begin, prepare, deferred, composite) by its program name. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer", remap = false)
public abstract class IrisCompositeCostMixin {
    // The second pushGroup in renderAll names each pass ("composite4") as it starts.
    @ModifyArg(method = "renderAll", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V", ordinal = 1), index = 1)
    private String create_galore_bridge$costPassStart(String name) {
        CostMeter.push(name);
        return name;
    }

    // The pass's popGroup: the first for a compute-only pass, the second for one that draws.
    @Inject(method = "renderAll", at = {
            @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;popGroup()V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;popGroup()V", ordinal = 1)})
    private void create_galore_bridge$costPassEnd(CallbackInfo ci) {
        CostMeter.pop();
    }
}
