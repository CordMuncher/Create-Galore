package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.creategalore.bridge.dlss.EntityMotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DLAA: each vertex written to a buffer in Iris's entity format gets where it was last frame (see {@link EntityMotion}).
 * Required: Iris's entity format only takes the attribute when this is in place ({@link EntityMotion.Writer}), as a
 * vertex missing an element stops the game.
 */
@Mixin(value = BufferBuilder.class, priority = 1100)
public abstract class BufferBuilderMotionMixin implements EntityMotion.Writer {
    @Shadow
    protected abstract long beginElement(VertexFormatElement element);

    @Inject(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"), require = 1)
    private void create_galore_bridge$prevPos(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
        VertexFormatElement element = EntityMotion.formatElement();
        if (element != null) {
            long pointer = beginElement(element);
            if (pointer != -1L) {
                EntityMotion.write(pointer, x, y, z);
            }
        }
    }
}
