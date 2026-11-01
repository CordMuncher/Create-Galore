package dev.creategalore.bridge.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What moves through the water's surface near the camera, for shader packs that draw wakes: players, mobs, boats and
 * Sable's ships whose outline crosses the surface, nearest first, at most {@link #MAX}. Worked out once a frame (Iris's
 * frame notifier) and handed to the pack as uniforms (see IrisWaterWakesUniformsMixin): cg_wake_count, and cg_wake0
 * to cg_wake7, a mat4 each:
 * <ul>
 * <li>[0]: where, from the camera (y the water's surface under it), and how much of it cuts the surface (0..1);</li>
 * <li>[1]: how fast it goes, blocks a second;</li>
 * <li>[2]: its outline at the surface: half its length along its heading, half its width, its heading (x, z);</li>
 * <li>[3]: x how round the outline's ends are (1 for a mob or a boat, 0.5 for a ship's hull).</li>
 * </ul>
 * A ship's outline is the bounding rectangle of its blocks in the layer at the water line (in the ship's own frame),
 * looked up again every half second or when the water line moves to another layer.
 */
public final class WaterWakes {
    public static final int MAX = 8;
    /** How far from the camera, across, anything counts (the pack's field reaches 32 blocks). */
    private static final double REACH = 30.0;

    private static final float[][] MOVERS = new float[MAX][16];
    private static int count;
    private static final Map<Object, Track> TRACKS = new WeakHashMap<>();
    private static final Map<ClientSubLevel, Footprint> FOOTPRINTS = new WeakHashMap<>();

    private WaterWakes() {
    }

    public static int count() {
        return count;
    }

    public static float[] mover(int i) {
        return MOVERS[i];
    }

    private record Candidate(double distance, float[] data) {
    }

    /** Where something was last frame, and how fast it went (smoothed). */
    private static final class Track {
        double x, y, z;
        long time;
        double vx, vy, vz;
    }

    /** A ship's outline at its water line, in the ship's own frame. */
    private record Footprint(int layer, long gameTime, double cx, double cz, double halfX, double halfZ) {
    }

    /** Once a frame. */
    public static void update() {
        count = 0;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        long now = System.nanoTime();
        List<Candidate> found = new ArrayList<>();

        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity || e instanceof Boat) || e.isSpectator() || e.getVehicle() instanceof Boat) continue;
            Vec3 at = e.getPosition(partial);
            double across = Math.hypot(at.x - camera.x, at.z - camera.z);
            if (across > REACH) continue;
            AABB box = e.getBoundingBox().move(at.subtract(e.position()));
            Double surface = surface(level, at.x, at.z, box.maxY + 1.0, box.minY - 1.0);
            if (surface == null) continue;
            double strength = clamp((surface - box.minY + 0.05) / 0.3) * clamp((box.maxY - surface + 0.05) / 0.3);
            if (strength <= 0.0) continue;
            double[] v = velocity(e, at.x, at.y, at.z, now);
            float[] m = new float[16];
            m[0] = (float) (at.x - camera.x);
            m[1] = (float) (surface - camera.y);
            m[2] = (float) (at.z - camera.z);
            m[3] = (float) strength;
            m[4] = (float) v[0];
            m[5] = (float) v[1];
            m[6] = (float) v[2];
            if (e instanceof Boat) {
                double yaw = Math.toRadians(e.getViewYRot(partial));
                m[8] = 1.1F;
                m[9] = 0.7F;
                m[10] = (float) -Math.sin(yaw);
                m[11] = (float) Math.cos(yaw);
            } else {
                float r = Math.max(e.getBbWidth() * 0.5F, 0.25F);
                m[8] = r;
                m[9] = r;
                m[10] = 1.0F;
                m[11] = 0.0F;
            }
            m[12] = 1.0F;
            found.add(new Candidate(across, m));
        }

        for (ClientSubLevel ship : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (ship.isRemoved()) continue;
            BoundingBox3dc bounds = ship.boundingBox();
            double nearX = Math.max(bounds.minX(), Math.min(camera.x, bounds.maxX()));
            double nearZ = Math.max(bounds.minZ(), Math.min(camera.z, bounds.maxZ()));
            double across = Math.hypot(nearX - camera.x, nearZ - camera.z);
            if (across > REACH) continue;
            Pose3dc pose = ship.renderPose();
            double px = pose.position().x(), pz = pose.position().z();
            Double surface = surface(level, px, pz, bounds.maxY(), bounds.minY() - 2.0);
            if (surface == null || surface <= bounds.minY() || surface >= bounds.maxY()) continue;
            Footprint f = footprint(level, ship, pose, px, surface, pz);
            if (f == null) continue;
            Vec3 middle = pose.transformPosition(new Vec3(f.cx, f.layer + 0.5, f.cz));
            Vec3 way = pose.transformNormal(new Vec3(1.0, 0.0, 0.0));
            double wayLength = Math.hypot(way.x, way.z);
            if (wayLength < 0.3) continue; // standing on end: no outline at the surface worth a wake
            double[] v = velocity(ship, middle.x, middle.y, middle.z, now);
            float[] m = new float[16];
            m[0] = (float) (middle.x - camera.x);
            m[1] = (float) (surface - camera.y);
            m[2] = (float) (middle.z - camera.z);
            m[3] = (float) clamp((surface - bounds.minY()) / 0.5);
            m[4] = (float) v[0];
            m[5] = (float) v[1];
            m[6] = (float) v[2];
            m[8] = (float) f.halfX;
            m[9] = (float) f.halfZ;
            m[10] = (float) (way.x / wayLength);
            m[11] = (float) (way.z / wayLength);
            m[12] = 0.5F;
            found.add(new Candidate(across, m));
        }

        found.sort(Comparator.comparingDouble(Candidate::distance));
        for (Candidate c : found) {
            if (count == MAX) break;
            System.arraycopy(c.data, 0, MOVERS[count], 0, 16);
            count++;
        }
    }

    /** How fast a thing goes, from where it was last frame, smoothed a little. */
    private static double[] velocity(Object key, double x, double y, double z, long now) {
        Track t = TRACKS.get(key);
        if (t == null) {
            t = new Track();
            TRACKS.put(key, t);
        } else {
            double dt = (now - t.time) * 1.0e-9;
            if (dt > 0.0) {
                double rx = (x - t.x) / dt, ry = (y - t.y) / dt, rz = (z - t.z) / dt;
                if (dt > 2.0 || rx * rx + ry * ry + rz * rz > 60.0 * 60.0) {
                    t.vx = t.vy = t.vz = 0.0; // a teleport or a long pause, not a dash
                } else {
                    // Smoothed over about a tenth of a second, whatever the frame rate.
                    double k = 1.0 - Math.exp(-dt / 0.1);
                    t.vx += (rx - t.vx) * k;
                    t.vy += (ry - t.vy) * k;
                    t.vz += (rz - t.vz) * k;
                }
            }
        }
        t.x = x;
        t.y = y;
        t.z = z;
        t.time = now;
        return new double[] {t.vx, t.vy, t.vz};
    }

    /** The bounding rectangle of a ship's blocks in the layer at its water line (its own frame), kept a while. */
    private static Footprint footprint(ClientLevel level, ClientSubLevel ship, Pose3dc pose, double x, double surface, double z) {
        Vec3 local = pose.transformPositionInverse(new Vec3(x, surface, z));
        int layer = (int) Math.floor(local.y);
        long time = level.getGameTime();
        Footprint f = FOOTPRINTS.get(ship);
        if (f != null && f.layer == layer && time - f.gameTime < 10 && time >= f.gameTime) return f;
        BoundingBox3ic plot = ship.getPlot().getBoundingBox();
        if (plot == null) return null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        // The water line's layer, else the one under it (a hull that only just dips in).
        for (int y = layer; y >= layer - 1 && minX == Integer.MAX_VALUE; y--) {
            if (y < plot.minY() || y > plot.maxY()) continue;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int x1 = Math.min(plot.maxX(), plot.minX() + 127), z1 = Math.min(plot.maxZ(), plot.minZ() + 127);
            for (int bx = plot.minX(); bx <= x1; bx++) {
                for (int bz = plot.minZ(); bz <= z1; bz++) {
                    pos.set(bx, y, bz);
                    if (level.getBlockState(pos).isAir()) continue;
                    minX = Math.min(minX, bx);
                    maxX = Math.max(maxX, bx);
                    minZ = Math.min(minZ, bz);
                    maxZ = Math.max(maxZ, bz);
                }
            }
        }
        if (minX == Integer.MAX_VALUE) {
            FOOTPRINTS.remove(ship);
            return null;
        }
        f = new Footprint(layer, time, (minX + maxX + 1) * 0.5, (minZ + maxZ + 1) * 0.5, (maxX - minX + 1) * 0.5,
                (maxZ - minZ + 1) * 0.5);
        FOOTPRINTS.put(ship, f);
        return f;
    }

    /**
     * The top of the world's own water in a column, between top and bottom, or null when there is none or the water
     * goes on above the top (whatever is there is under the surface, not through it).
     */
    private static Double surface(ClientLevel level, double x, double z, double top, double bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int high = (int) Math.floor(top), low = (int) Math.floor(bottom);
        for (int y = high; y >= low; y--) {
            pos.set(Math.floor(x), y, Math.floor(z));
            // The world's water, not the dry hold Create Deep Seas reports inside a hull.
            FluidState fluid = CompartmentTracker.realFluidState(level, pos);
            if (!fluid.is(FluidTags.WATER)) continue;
            return y == high ? null : y + (double) fluid.getHeight(level, pos);
        }
        return null;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
