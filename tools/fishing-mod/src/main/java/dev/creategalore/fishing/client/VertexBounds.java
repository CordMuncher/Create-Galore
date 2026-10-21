package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * A buffer source that draws nothing and remembers the box around every vertex sent to it: render something into it
 * to learn how big it really is (in the pose stack's space), then size and centre it properly.
 */
final class VertexBounds implements MultiBufferSource, VertexConsumer {
    final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    private boolean any;

    boolean any() {
        return any;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        any = true;
        min[0] = Math.min(min[0], x);
        min[1] = Math.min(min[1], y);
        min[2] = Math.min(min[2], z);
        max[0] = Math.max(max[0], x);
        max[1] = Math.max(max[1], y);
        max[2] = Math.max(max[2], z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }
}
