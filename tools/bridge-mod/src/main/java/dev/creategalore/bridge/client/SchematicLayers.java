package dev.creategalore.bridge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Create draws a schematic's blocks into the chunk layers (solid, cutout, translucent) outside of chunk rendering. With
 * an Iris shader pack on, blocks drawn that way come out as a flat white shape (Create's placement preview, the
 * off-hand trace). The entity layers on the block atlas carry the same blocks through every pack's entity program, so
 * with a pack on, the schematic's layers are swapped for those. Create also bakes every schematic block at full block
 * light, which a pack draws as a block lit by a lamp (a white or orange glow at night), so with a pack on the blocks get
 * open sky's light instead: lit like the ground around them, day or night. Without a pack, nothing changes.
 */
public final class SchematicLayers {
    private static final MethodHandle PACK_IN_USE;
    private static final Object IRIS_API;

    static {
        MethodHandle packInUse = null;
        Object api = null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            packInUse = MethodHandles.publicLookup().findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class));
        } catch (Throwable notThere) {
            packInUse = null;
        }
        PACK_IN_USE = packInUse;
        IRIS_API = api;
    }

    private SchematicLayers() {
    }

    static boolean shadersOn() {
        if (PACK_IN_USE == null) return false;
        try {
            return (boolean) PACK_IN_USE.invoke(IRIS_API);
        } catch (Throwable e) {
            return false;
        }
    }

    /** The buffer to draw a schematic into: the same one without a shader pack, one on the entity layers with one. */
    public static SuperRenderTypeBuffer forShaders(SuperRenderTypeBuffer buffer) {
        return buffer instanceof EntityLayers || !shadersOn() ? buffer : new EntityLayers(buffer);
    }

    /** Open sky's light (sky 15, block 0) on every vertex, in place of the full block light Create bakes in. */
    static VertexConsumer skyLit(VertexConsumer consumer) {
        return new SkyLit(consumer);
    }

    static RenderType entityLayer(RenderType type) {
        if (type == RenderType.solid()) return RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS);
        if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) return RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);
        if (RenderType.chunkBufferLayers().contains(type)) return RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
        return type;
    }

    private record EntityLayers(SuperRenderTypeBuffer inner) implements SuperRenderTypeBuffer {
        @Override
        public VertexConsumer getEarlyBuffer(RenderType type) {
            return skyLit(inner.getEarlyBuffer(entityLayer(type)));
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return skyLit(inner.getBuffer(entityLayer(type)));
        }

        @Override
        public VertexConsumer getLateBuffer(RenderType type) {
            return skyLit(inner.getLateBuffer(entityLayer(type)));
        }

        @Override
        public void draw() {
            inner.draw();
        }

        @Override
        public void draw(RenderType type) {
            inner.draw(entityLayer(type));
        }
    }

    private record SkyLit(VertexConsumer inner) implements VertexConsumer {
        private static final int SKY = 15 << 4;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int block, int sky) {
            inner.setUv2(0, SKY);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
