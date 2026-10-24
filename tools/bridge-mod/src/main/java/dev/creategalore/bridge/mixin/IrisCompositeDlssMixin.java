package dev.creategalore.bridge.mixin;

import java.util.Set;

import dev.creategalore.bridge.dlss.Dlss;
import net.irisshaders.iris.gl.image.GlImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: runs DLSS right after the shader pack's TAA pass, while that pass's framebuffer is still bound (see {@link Dlss}). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer", remap = false)
public abstract class IrisCompositeDlssMixin {
    @Shadow @Final private Set<GlImage> customImages;
    @Unique private String create_galore_bridge$passName;

    // The second pushGroup in renderAll names each pass ("composite4") as it starts.
    @ModifyArg(method = "renderAll", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V", ordinal = 1), index = 1)
    private String create_galore_bridge$rememberPass(String name) {
        create_galore_bridge$passName = name;
        return name;
    }

    @Inject(method = "renderAll", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V", shift = At.Shift.AFTER))
    private void create_galore_bridge$afterPass(CallbackInfo ci) {
        if (Dlss.shaderActive()) {
            Dlss.afterPass(create_galore_bridge$passName, this::create_galore_bridge$imageId);
        }
    }

    @Unique
    private int create_galore_bridge$imageId(String name) {
        for (GlImage image : customImages) {
            if (name.equals(image.getName())) {
                return image.getId();
            }
        }
        return 0;
    }
}
