package dev.creategalore.bridge.mixin;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;
import dev.creategalore.bridge.client.CostMeter;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cost meter: times each shadow composite pass. Iris keeps no names for them, so they are taken from the programs
 * the passes are built from, in the order Iris builds them (a pass for each valid program, or a compute-only pass).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowCompositeRenderer", remap = false)
public abstract class IrisShadowCompositeCostMixin {
    @Unique private final List<String> create_galore_bridge$names = new ArrayList<>();
    @Unique private int create_galore_bridge$pass;
    @Unique private boolean create_galore_bridge$open;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void create_galore_bridge$costNames(CallbackInfo ci, @Local(argsOnly = true) ProgramSource[] sources,
                                                @Local(argsOnly = true) ComputeSource[][] computes) {
        for (int i = 0; i < sources.length; i++) {
            ProgramSource source = sources[i];
            if (source != null && source.isValid()) {
                create_galore_bridge$names.add(source.getName());
            } else if (computes.length > 0 && computes[i] != null) {
                String name = "shadowcomp";
                for (ComputeSource compute : computes[i]) {
                    if (compute != null) {
                        name = compute.getName();
                        break;
                    }
                }
                create_galore_bridge$names.add(name);
            }
        }
    }

    @Inject(method = "renderAll", at = @At("HEAD"))
    private void create_galore_bridge$costStart(CallbackInfo ci) {
        create_galore_bridge$pass = 0;
        create_galore_bridge$open = false;
    }

    // The loop over the passes takes the next one.
    @Inject(method = "renderAll", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;", ordinal = 0, shift = At.Shift.AFTER))
    private void create_galore_bridge$costPass(CallbackInfo ci) {
        if (create_galore_bridge$open) {
            CostMeter.pop();
        }
        int i = create_galore_bridge$pass++;
        CostMeter.push(i < create_galore_bridge$names.size() ? create_galore_bridge$names.get(i) : "shadowcomp?" + i);
        create_galore_bridge$open = true;
    }

    @Inject(method = "renderAll", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;end()V"))
    private void create_galore_bridge$costEnd(CallbackInfo ci) {
        if (create_galore_bridge$open) {
            CostMeter.pop();
            create_galore_bridge$open = false;
        }
    }
}
