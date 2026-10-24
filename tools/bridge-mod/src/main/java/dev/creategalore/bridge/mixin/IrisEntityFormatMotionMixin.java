package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.creategalore.bridge.dlss.EntityMotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** DLAA: Iris's entity vertex format carries where each vertex was last frame (see {@link EntityMotion}). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.vertices.IrisVertexFormats", remap = false)
public abstract class IrisEntityFormatMotionMixin {
    // The formats are built in order: terrain, entity, glyph, clouds.
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexFormat$Builder;build()Lcom/mojang/blaze3d/vertex/VertexFormat;", ordinal = 1))
    private static VertexFormat create_galore_bridge$entityMotion(VertexFormat.Builder builder) {
        VertexFormatElement element = EntityMotion.element();
        if (element != null) {
            builder.add(EntityMotion.ATTRIBUTE, element);
        }
        return builder.build();
    }
}
