package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Compile-only stub of the client interface. */
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);
}
