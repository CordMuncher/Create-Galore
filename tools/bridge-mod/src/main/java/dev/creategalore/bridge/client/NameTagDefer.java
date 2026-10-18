package dev.creategalore.bridge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Name tags under an Iris shader pack. Bliss and Photon draw the world deferred, so the tag's see-through text and its
 * plate go through their lighting: the text comes out black and the plate a solid black box wherever there is a block
 * behind it (Bliss-Shader#346, photon#569). While a pack is on, name tags are written into buffers of their own during
 * the level pass and drawn after Iris has finished the frame, with the game's own text shaders and the scene's depth,
 * so they look the way they do without shaders. In Iris's shadow pass they are skipped (no ghost tags in shadows).
 * Without Iris, or with shaders off, nothing changes.
 */
public final class NameTagDefer {
    private static final MethodHandle PACK_IN_USE;
    private static final MethodHandle SHADOW_PASS;
    private static final MethodHandle RENDERING_LEVEL;
    private static final ThreadLocal<Boolean> SKIP_EXTENSION;
    private static final Object IRIS_API;

    static {
        MethodHandle packInUse = null, shadowPass = null, renderingLevel = null;
        ThreadLocal<Boolean> skipExtension = null;
        Object api = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            packInUse = lookup.findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadowPass = lookup.findVirtual(apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            Class<?> state = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            renderingLevel = lookup.findStaticGetter(state, "isRenderingLevel", boolean.class);
            @SuppressWarnings("unchecked")
            ThreadLocal<Boolean> skip = (ThreadLocal<Boolean>) state.getField("skipExtension").get(null);
            skipExtension = skip;
        } catch (Throwable notThere) {
            packInUse = null;
        }
        PACK_IN_USE = packInUse;
        SHADOW_PASS = shadowPass;
        RENDERING_LEVEL = renderingLevel;
        SKIP_EXTENSION = skipExtension;
        IRIS_API = api;
    }

    /** Buffers for the tags of one frame, in the order their render types first showed up (plate, see-through, text). */
    private static final Map<RenderType, ByteBufferBuilder> MEMORY = new HashMap<>();
    private static final Map<RenderType, BufferBuilder> PENDING = new LinkedHashMap<>();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final MultiBufferSource BUFFERS = NameTagDefer::buffer;

    private NameTagDefer() {
    }

    /** True while Iris draws its shadow map: name tags are left out of it. */
    public static boolean inShadowPass() {
        if (PACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) SHADOW_PASS.invoke(IRIS_API);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The buffers a name tag should draw into: ours during a shader pack's level pass, otherwise the ones it was given. */
    public static MultiBufferSource redirect(MultiBufferSource given) {
        if (PACK_IN_USE == null) {
            return given;
        }
        try {
            if (!(boolean) PACK_IN_USE.invoke(IRIS_API) || !(boolean) RENDERING_LEVEL.invoke() || (boolean) SHADOW_PASS.invoke(IRIS_API)) {
                return given;
            }
        } catch (Throwable t) {
            return given;
        }
        if (PENDING.isEmpty()) {
            MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
            PROJECTION.set(RenderSystem.getProjectionMatrix());
        }
        return BUFFERS;
    }

    private static VertexConsumer buffer(RenderType type) {
        BufferBuilder builder = PENDING.get(type);
        if (builder == null) {
            ByteBufferBuilder memory = MEMORY.computeIfAbsent(type, t -> new ByteBufferBuilder(t.bufferSize()));
            // Plain vanilla vertices: Iris would otherwise widen text to its own format, which only its shaders read.
            SKIP_EXTENSION.set(true);
            try {
                builder = new BufferBuilder(memory, type.mode(), type.format());
            } finally {
                SKIP_EXTENSION.set(false);
            }
            PENDING.put(type, builder);
        }
        return builder;
    }

    /** Draws the frame's name tags over the finished image. Called right after the level is rendered, before the hand. */
    public static void flush() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(MODEL_VIEW);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            for (Map.Entry<RenderType, BufferBuilder> entry : PENDING.entrySet()) {
                RenderType type = entry.getKey();
                MeshData mesh = entry.getValue().build();
                if (mesh != null) {
                    if (type.sortOnUpload()) {
                        mesh.sortQuads(MEMORY.get(type), RenderSystem.getVertexSorting());
                    }
                    type.draw(mesh);
                }
            }
        } finally {
            PENDING.clear();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
