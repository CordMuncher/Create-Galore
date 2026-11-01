package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.WaterWakes;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wakes for shader packs: cg_wake_count and cg_wake0 to cg_wake7, what moves through the water's surface near the
 * camera (see {@link WaterWakes}), for every program. Worked out once a frame, when Iris starts one. Packs that do not
 * use them never see them.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisWaterWakesUniformsMixin {
    /** The pipelines' frame notifiers already told to work the wakes out (one per pipeline, shared by its programs). */
    @Unique
    private static final Set<FrameUpdateNotifier> create_galore_bridge$notified = Collections.newSetFromMap(new WeakHashMap<>());

    @Inject(method = "addNonDynamicUniforms", at = @At("TAIL"))
    private static void create_galore_bridge$wakeUniforms(UniformHolder uniforms, IdMap idMap, PackDirectives directives,
                                                          FrameUpdateNotifier updateNotifier, CallbackInfo ci) {
        if (create_galore_bridge$notified.add(updateNotifier)) updateNotifier.addListener(WaterWakes::update);
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "cg_wake_count", WaterWakes::count);
        for (int i = 0; i < WaterWakes.MAX; i++) {
            int index = i;
            uniforms.uniformMatrixFromArray(UniformUpdateFrequency.PER_FRAME, "cg_wake" + i, () -> WaterWakes.mover(index));
        }
    }
}
