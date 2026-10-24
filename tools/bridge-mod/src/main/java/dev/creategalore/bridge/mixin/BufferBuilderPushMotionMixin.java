package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.creategalore.bridge.dlss.EntityMotion;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: vertices Sodium writes in bulk (its fast paths for entity models and item quads) get where they were last
 * frame too (see {@link EntityMotion}); Sodium's push leaves the attribute unwritten.
 */
@Mixin(value = BufferBuilder.class, priority = 1100)
public abstract class BufferBuilderPushMotionMixin {
    @Shadow
    @Final
    private VertexFormat format;
    @Shadow
    @Final
    private int vertexSize;
    @Shadow
    private long vertexPointer;

    @Dynamic("Sodium's VertexBufferWriter.push, which leaves vertexPointer on the last vertex it wrote")
    @Inject(method = "push", at = @At("RETURN"), remap = false)
    private void create_galore_bridge$prevPosPushed(MemoryStack stack, long source, int count, VertexFormat sourceFormat, CallbackInfo ci) {
        VertexFormatElement element = EntityMotion.formatElement();
        if (element == null || count <= 0 || !format.contains(element)) {
            return;
        }
        int offset = format.getOffset(element);
        long vertex = vertexPointer - (long) (count - 1) * vertexSize;
        for (int i = 0; i < count; i++, vertex += vertexSize) {
            EntityMotion.write(vertex + offset, MemoryUtil.memGetFloat(vertex), MemoryUtil.memGetFloat(vertex + 4), MemoryUtil.memGetFloat(vertex + 8));
        }
    }
}
