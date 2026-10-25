package dev.creategalore.bridge.dlss;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DLAA motion vectors for what the game draws vertex by vertex every frame: entities (mobs, players and their armor,
 * dropped items, GeckoLib's models), block entities (chest lids, bells) and the hand. Their vertices are worked out on
 * the CPU, so no matrix in the shader knows where they were last frame; the vertices themselves carry it.
 *
 * Iris's entity vertex format gets one more attribute, cg_prev_pos at {@link #LOCATION} (see the Iris format mixin),
 * written for every vertex. While one thing draws, its vertices are counted in the order they are written: a thing
 * draws its model in the same order every frame, so its vertex n now is its vertex n last frame. w says what xyz is:
 * {@link #WORLD}, last frame's position relative to the camera now, in the same space as the vertex (entities and
 * block entities are drawn relative to the camera); {@link #HAND}, last frame's position in the vertex's own space
 * with this frame's model-view (the hand moves with the camera, its bobbing is in the model-view); 0, not known (not
 * drawn last frame, more vertices than last frame, a jump), which leaves it to the pack's reprojection. The patched
 * pack only takes exactly 2 or 3, so a vertex nobody wrote (left over memory) is never taken.
 */
public final class EntityMotion {
    public static final String ATTRIBUTE = "cg_prev_pos";
    /** The attribute's index in Iris's entity format, which is its attribute location; the patched pack declares it there. */
    public static final int LOCATION = 9;
    static final float WORLD = 2.0f;
    static final float HAND = 3.0f;
    /** Blocks between frames: a vertex further from where it was is not the same vertex. */
    private static final float MAX_JUMP = 4.0f;
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    /** Written into vertices by the BufferBuilder mixin, which the format only takes this attribute with. */
    public interface Writer {}

    private static VertexFormatElement element;
    private static boolean usable;
    private static boolean checked;

    private static long frame;
    private static boolean tracking;
    private static boolean cameraKnown;
    private static double camX;
    private static double camY;
    private static double camZ;
    private static float camDX;
    private static float camDY;
    private static float camDZ;

    private static final Map<Object, Track> TRACKS = new WeakHashMap<>();
    private static final Object HAND_KEY = new Object();
    private static final int MAX_DEPTH = 8;
    private static final Track[] STACK = new Track[MAX_DEPTH];
    private static final boolean[] STACK_HAND = new boolean[MAX_DEPTH];
    private static int depth;
    private static Track current;
    private static boolean handSpace;
    private static final Matrix4f HAND_VIEW = new Matrix4f();
    private static final Matrix4f HAND_VIEW_INVERSE = new Matrix4f();
    private static final Vector3f VEC = new Vector3f();

    private EntityMotion() {}

    /**
     * Iris's entity format is being built: the element to add to it, or null if this Bridge cannot fill it (then the
     * format stays as Iris has it, since a vertex missing an element stops the game).
     */
    public static VertexFormatElement element() {
        if (element == null && Writer.class.isAssignableFrom(BufferBuilder.class)) {
            int id = 0;
            while (id < 32 && VertexFormatElement.byId(id) != null) {
                id++;
            }
            if (id < 32) {
                element = VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
            } else {
                LOG.warn("DLAA: no vertex attribute left for entity motion, entities keep the camera's motion");
            }
        }
        return element;
    }

    /** The attribute's element once Iris's entity format has it, else null. */
    public static VertexFormatElement formatElement() {
        return element;
    }

    /** Start of a level frame (see {@link Dlss#beginFrame}): tracks this frame if DLAA hands over motion of its own. */
    static void beginFrame(boolean active, Vec3 camera) {
        frame++;
        if (!checked && element != null) {
            checked = true;
            int index = net.irisshaders.iris.vertices.IrisVertexFormats.ENTITY.getElements().indexOf(element);
            usable = index == LOCATION;
            if (!usable) {
                LOG.warn("DLAA: entity motion attribute at {} instead of {}, entities keep the camera's motion", index, LOCATION);
            }
        }
        tracking = active && usable && Dlss.objectMotion();
        if (cameraKnown) {
            camDX = (float) (camX - camera.x);
            camDY = (float) (camY - camera.y);
            camDZ = (float) (camZ - camera.z);
        }
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        cameraKnown = true;
        depth = 0;
        current = null;
    }

    /** End of the level frame: what draws after (screens, the inventory's player) is not tracked. */
    static void endFrame() {
        tracking = false;
        depth = 0;
        current = null;
    }

    /** An entity or block entity starts drawing (relative to the camera). Pair with {@link #end()}. */
    public static void begin(Object thing) {
        push(thing, false);
    }

    /** The hand starts drawing (the solid and the translucent pass each draw all of it). Pair with {@link #end()}. */
    public static void beginHand() {
        push(HAND_KEY, true);
        if (current != null && handSpace) {
            HAND_VIEW.set(RenderSystem.getModelViewMatrix());
            HAND_VIEW.invert(HAND_VIEW_INVERSE);
        }
    }

    private static void push(Object key, boolean hand) {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        if (depth < MAX_DEPTH) {
            STACK[depth] = current;
            STACK_HAND[depth] = handSpace;
        }
        depth++;
        if (!tracking || depth > MAX_DEPTH || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            current = null;
            return;
        }
        Track track = TRACKS.computeIfAbsent(key, k -> new Track());
        track.enter(frame);
        current = track;
        handSpace = hand;
    }

    public static void end() {
        if (depth == 0 || !RenderSystem.isOnRenderThread()) {
            return;
        }
        depth--;
        if (depth < MAX_DEPTH) {
            current = STACK[depth];
            handSpace = STACK_HAND[depth];
            STACK[depth] = null;
        } else {
            current = null;
        }
    }

    /** Writes the attribute at {@code pointer} for a vertex at x, y, z (as written to the buffer). */
    public static void write(long pointer, float x, float y, float z) {
        Track track = current;
        // Another thread building a buffer in this format (a mod baking a model) is no part of what draws now.
        if (track == null || !RenderSystem.isOnRenderThread()) {
            unknown(pointer);
            return;
        }
        if (handSpace) {
            HAND_VIEW.transformPosition(x, y, z, VEC);
            int i = track.add(VEC.x, VEC.y, VEC.z);
            if (i >= track.lastCount) {
                unknown(pointer);
                return;
            }
            HAND_VIEW_INVERSE.transformPosition(track.last[3 * i], track.last[3 * i + 1], track.last[3 * i + 2], VEC);
            put(pointer, VEC.x, VEC.y, VEC.z, HAND);
            return;
        }
        int i = track.add(x, y, z);
        if (i >= track.lastCount) {
            unknown(pointer);
            return;
        }
        float px = track.last[3 * i] + camDX;
        float py = track.last[3 * i + 1] + camDY;
        float pz = track.last[3 * i + 2] + camDZ;
        float dx = px - x;
        float dy = py - y;
        float dz = pz - z;
        if (dx * dx + dy * dy + dz * dz > MAX_JUMP * MAX_JUMP) {
            unknown(pointer);
            return;
        }
        put(pointer, px, py, pz, WORLD);
    }

    private static void unknown(long pointer) {
        put(pointer, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private static void put(long pointer, float x, float y, float z, float w) {
        MemoryUtil.memPutFloat(pointer, x);
        MemoryUtil.memPutFloat(pointer + 4, y);
        MemoryUtil.memPutFloat(pointer + 8, z);
        MemoryUtil.memPutFloat(pointer + 12, w);
    }

    /** One thing's vertex positions this frame and last frame, in drawing order. */
    private static final class Track {
        private long frame = Long.MIN_VALUE;
        float[] now = new float[3 * 64];
        int nowCount;
        float[] last = new float[3 * 64];
        int lastCount;

        /** Drawn again in the same frame (the hand's two passes, a mod drawing it twice): carries on counting. */
        void enter(long current) {
            if (frame == current) {
                return;
            }
            float[] swap = last;
            last = now;
            now = swap;
            lastCount = frame == current - 1 ? nowCount : 0;
            nowCount = 0;
            frame = current;
        }

        int add(float x, float y, float z) {
            int i = nowCount++;
            if (3 * i + 3 > now.length) {
                now = java.util.Arrays.copyOf(now, Math.max(now.length * 2, 3 * i + 3));
            }
            now[3 * i] = x;
            now[3 * i + 1] = y;
            now[3 * i + 2] = z;
            return i;
        }
    }
}
