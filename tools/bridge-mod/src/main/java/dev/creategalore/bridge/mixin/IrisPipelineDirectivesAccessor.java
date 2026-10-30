package dev.creategalore.bridge.mixin;

import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the shader pack's shaders.properties asked for, as it wrote it (see {@link dev.creategalore.bridge.client.ParticleOrdering}). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public interface IrisPipelineDirectivesAccessor {
    @Accessor(value = "packDirectives", remap = false)
    PackDirectives create_galore_bridge$packDirectives();
}
