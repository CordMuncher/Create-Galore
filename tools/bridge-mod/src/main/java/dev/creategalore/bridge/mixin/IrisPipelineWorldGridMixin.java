package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.grid.WorldGrid;
import java.util.Set;
import net.irisshaders.iris.gl.image.GlImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The block grid: filled into the pack's images at the start of each frame, before its first pass (see {@link WorldGrid}). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisPipelineWorldGridMixin {
    @Shadow @Final private Set<GlImage> customImages;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void create_galore_bridge$worldGrid(CallbackInfo ci) {
        WorldGrid.beginFrame(this, this::create_galore_bridge$gridImage);
    }

    @Unique
    private int create_galore_bridge$gridImage(String name) {
        for (GlImage image : customImages) {
            if (name.equals(image.getName())) {
                return image.getId();
            }
        }
        return 0;
    }
}
