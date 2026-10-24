package dev.creategalore.bridge.dlss;

import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.opengl.GL20C;

/**
 * DLAA motion vectors for things that move as a whole, which the pack's vertex shaders cannot see moving: the
 * per-frame state behind them (see {@link PhotonPatcher}'s cg_motion.glsl for the shader side).
 *
 * Sable's ships draw their blocks through the pack's terrain program with the ship's pose in the model-view matrix.
 * For each ship draw this sets cg_object_motion, the matrix that takes a vertex from where it is now (relative to the
 * camera) to where the ship had it last frame, and clears it after. Create's machines and contraptions through
 * Colorwheel get last frame's Flywheel time ({@link #flywheelTickDelta()}) and model matrices ({@link ColorwheelMotion}).
 *
 * Everything is kept per rendered frame ({@link #frame()}); something not drawn last frame has no motion of its own.
 */
public final class ObjectMotion {
    static final String OBJECT_MOTION_UNIFORM = "cg_object_motion";
    /** More than this many ticks between frames is a hitch or a pause: no motion from Flywheel's clock. */
    private static final double MAX_TICK_DELTA = 10.0;

    private static long frame;
    private static boolean ticksKnown;
    private static double renderTicks;
    private static float tickDelta;

    private static final Int2IntOpenHashMap LOCATIONS = new Int2IntOpenHashMap();
    private static final Map<Object, PoseTrack> SHIPS = new WeakHashMap<>();
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final float[] FLOATS = new float[16];
    private static final float[] ZERO = new float[16];

    static {
        LOCATIONS.defaultReturnValue(Integer.MIN_VALUE);
    }

    private ObjectMotion() {}

    /** Start of a level frame (with a shader pack loaded). {@code levelTicks} is the level renderer's tick count. */
    static void beginFrame(int levelTicks) {
        frame++;
        double ticks = levelTicks + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double delta = ticks - renderTicks;
        tickDelta = ticksKnown && delta >= 0.0 && delta <= MAX_TICK_DELTA ? (float) delta : 0.0f;
        renderTicks = ticks;
        ticksKnown = true;
    }

    /** Shaders (re)loaded: GL programs are new, uniform locations with them. */
    static void reset() {
        LOCATIONS.clear();
        ticksKnown = false;
    }

    /** This rendered frame's number. */
    public static long frame() {
        return frame;
    }

    /** Ticks of Flywheel's clock (flw_renderTicks) since last frame. */
    public static float flywheelTickDelta() {
        return tickDelta;
    }

    /**
     * A ship is about to draw its blocks with {@code program} bound, at {@code pose} (position, orientation, rotation
     * point) for this frame. Sets cg_object_motion for it; {@link #endShipDraw} clears it again.
     */
    public static void beginShipDraw(Object ship, int program, Vector3dc position, Quaterniondc orientation, Vector3dc rotationPoint,
            double camX, double camY, double camZ) {
        int location = location(program);
        if (location < 0) {
            return;
        }
        PoseTrack track = SHIPS.computeIfAbsent(ship, k -> new PoseTrack());
        track.advance(frame, position, orientation, rotationPoint);
        if (!track.moved()) {
            return;
        }
        // A ship point L is at R (L - C) + P in the world, R the orientation, C the rotation point, P the position.
        // From where it is now relative to the camera, s: last frame's is R' R^-1 (s - (P - cam)) + R' (C - C') + P' - cam.
        Quaterniond turn = new Quaterniond(track.prevOrientation).mul(new Quaterniond(track.orientation).conjugate());
        Vector3d now = new Vector3d(track.position).sub(camX, camY, camZ);
        Vector3d then = new Quaterniond(track.prevOrientation).transform(new Vector3d(track.rotationPoint).sub(track.prevRotationPoint))
                .add(track.prevPosition).sub(camX, camY, camZ);
        Vector3d translation = then.sub(turn.transform(now, new Vector3d()));
        MATRIX.rotation(new Quaternionf(turn));
        MATRIX.setTranslation((float) translation.x, (float) translation.y, (float) translation.z);
        GL20C.glUniformMatrix4fv(location, false, MATRIX.get(FLOATS));
    }

    /** The ship's blocks are drawn: whatever draws next with this program does not move with it. */
    public static void endShipDraw(int program) {
        int location = location(program);
        if (location >= 0) {
            GL20C.glUniformMatrix4fv(location, false, ZERO);
        }
    }

    private static int location(int program) {
        int location = LOCATIONS.get(program);
        if (location == Integer.MIN_VALUE) {
            location = GL20C.glGetUniformLocation(program, OBJECT_MOTION_UNIFORM);
            LOCATIONS.put(program, location);
        }
        return location;
    }

    /** Where a ship was drawn this frame and the frame before. */
    private static final class PoseTrack {
        private long frame = Long.MIN_VALUE;
        private boolean hasPrevious;
        final Vector3d position = new Vector3d();
        final Quaterniond orientation = new Quaterniond();
        final Vector3d rotationPoint = new Vector3d();
        final Vector3d prevPosition = new Vector3d();
        final Quaterniond prevOrientation = new Quaterniond();
        final Vector3d prevRotationPoint = new Vector3d();

        void advance(long now, Vector3dc pos, Quaterniondc rot, Vector3dc point) {
            if (frame == now) {
                return;
            }
            hasPrevious = frame == now - 1;
            frame = now;
            prevPosition.set(position);
            prevOrientation.set(orientation);
            prevRotationPoint.set(rotationPoint);
            position.set(pos);
            orientation.set(rot);
            rotationPoint.set(point);
        }

        boolean moved() {
            return hasPrevious && (!position.equals(prevPosition) || !orientation.equals(prevOrientation)
                    || !rotationPoint.equals(prevRotationPoint));
        }
    }
}
