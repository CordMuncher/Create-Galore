package dev.creategalore.bridge.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.Arrays;
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
 * <li>[1]: xyz how fast it goes, blocks a second; w a ship's draft (how deep its hull goes under the surface, blocks;
 * 0 for anything else);</li>
 * <li>[2]: its outline at the surface: half its length along its heading, half its width, its heading (x, z);</li>
 * <li>[3]: x how round the outline's ends are (1 for a mob or a boat, 0.5 for a ship's hull); y and z for a ship, how
 * far (blocks) its sides close in towards a pointed end at the heading's front and back end (0 for a square end);
 * w for a ship, how much water its hull pushes aside (blocks; 0 for anything else).</li>
 * </ul>
 * A ship's outline is the bounding rectangle of its hull at the water line (in the ship's own frame), and its ends'
 * taper, draft and displaced water come from the same look at its hull: each column of its blocks counts from its
 * lowest block up to the water line (the hull's inside counts as hull). That look is redone every two seconds or when
 * the water line moves to another layer, a few thousand blocks a frame, and the last one serves until it is done.
 */
public final class WaterWakes {
    public static final int MAX = 8;
    /** How far from the camera, across, anything counts (the pack's field reaches 32 blocks). */
    private static final double REACH = 30.0;

    private static final float[][] MOVERS = new float[MAX][16];
    private static int count;
    private static final Map<Object, Track> TRACKS = new WeakHashMap<>();
    private static final Map<ClientSubLevel, Footprint> FOOTPRINTS = new WeakHashMap<>();
    private static final Map<ClientSubLevel, Scan> SCANS = new WeakHashMap<>();
    /** How many blocks the hull looks may read each frame, over every ship. */
    private static final int SCAN_BUDGET = 8192;
    /** How long (ticks) a ship's hull look serves before it is redone. */
    private static final long RESCAN = 40;

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

    /**
     * A ship's hull at its water line, in the ship's own frame: its outline's middle and half sizes, how far its sides
     * close in at the +x and -x ends, its draft and how much water it pushes aside.
     */
    private record Footprint(int layer, long gameTime, double cx, double cz, double halfX, double halfZ,
            double taperPlus, double taperMinus, double draft, double volume) {
    }

    /** A look at a ship's hull under way: its columns one after another, from the lowest block up. */
    private static final class Scan {
        final int layer;
        final double line;
        final long gameTime;
        final int minX, minZ, maxX, maxZ, minY;
        final int sizeX, sizeZ;
        /** Each column's lowest block (ship frame), or Integer.MAX_VALUE where none is at or under the water line. */
        final int[] bottom;
        int next;

        Scan(int layer, double line, long gameTime, BoundingBox3ic plot) {
            this.layer = layer;
            this.line = line;
            this.gameTime = gameTime;
            minX = plot.minX();
            minZ = plot.minZ();
            maxX = Math.min(plot.maxX(), plot.minX() + 127);
            maxZ = Math.min(plot.maxZ(), plot.minZ() + 127);
            minY = Math.max(plot.minY(), layer - 63);
            sizeX = maxX - minX + 1;
            sizeZ = maxZ - minZ + 1;
            bottom = new int[sizeX * sizeZ];
        }

        boolean done() {
            return next >= bottom.length;
        }
    }

    /** Once a frame. */
    public static void update() {
        count = 0;
        budget = SCAN_BUDGET;
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
            m[7] = (float) f.draft;
            m[8] = (float) f.halfX;
            m[9] = (float) f.halfZ;
            m[10] = (float) (way.x / wayLength);
            m[11] = (float) (way.z / wayLength);
            m[12] = 0.5F;
            m[13] = (float) f.taperPlus;
            m[14] = (float) f.taperMinus;
            m[15] = (float) f.volume;
            found.add(new Candidate(across, m));
        }

        // Ships no longer about lose their looks.
        SCANS.keySet().removeIf(ClientSubLevel::isRemoved);
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

    /**
     * A ship's hull at its water line (its own frame): the last look at it while the next one, started every
     * {@link #RESCAN} ticks or when the water line moves to another layer, goes on a few columns a frame.
     */
    private static Footprint footprint(ClientLevel level, ClientSubLevel ship, Pose3dc pose, double x, double surface, double z) {
        Vec3 local = pose.transformPositionInverse(new Vec3(x, surface, z));
        int layer = (int) Math.floor(local.y);
        long time = level.getGameTime();
        Footprint f = FOOTPRINTS.get(ship);
        Scan scan = SCANS.get(ship);
        boolean stale = f == null || f.layer != layer || time - f.gameTime >= RESCAN || time < f.gameTime;
        if (stale && (scan == null || scan.layer != layer)) {
            BoundingBox3ic plot = ship.getPlot().getBoundingBox();
            if (plot == null) return f;
            scan = new Scan(layer, local.y, time, plot);
            SCANS.put(ship, scan);
        }
        if (scan != null) {
            budget = scan(level, scan, budget);
            if (scan.done()) {
                SCANS.remove(ship);
                Footprint done = finish(scan);
                if (done == null) {
                    FOOTPRINTS.remove(ship);
                } else {
                    FOOTPRINTS.put(ship, done);
                }
                return done;
            }
        }
        return f;
    }

    /** What the hull looks may still read this frame. */
    private static int budget;

    /** Goes on with a hull look within the budget; what is left of it. */
    private static int scan(ClientLevel level, Scan scan, int left) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Math.min(scan.layer, scan.minY + 63);
        while (!scan.done() && left > 0) {
            int i = scan.next++;
            int bx = scan.minX + i % scan.sizeX, bz = scan.minZ + i / scan.sizeX;
            int found = Integer.MAX_VALUE;
            for (int y = scan.minY; y <= top; y++) {
                left--;
                pos.set(bx, y, bz);
                if (!level.getBlockState(pos).isAir()) {
                    found = y;
                    break;
                }
            }
            scan.bottom[i] = found;
        }
        return left;
    }

    /**
     * A finished hull look: the outline of the columns whose blocks reach down to the water line, each row's width
     * along x for the ends' taper, the deepest column (draft) and the water all of them push aside.
     */
    private static Footprint finish(Scan scan) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int[] rowMin = new int[scan.sizeX], rowMax = new int[scan.sizeX];
        Arrays.fill(rowMin, Integer.MAX_VALUE);
        Arrays.fill(rowMax, Integer.MIN_VALUE);
        double draft = 0.0, volume = 0.0;
        for (int i = 0; i < scan.bottom.length; i++) {
            int b = scan.bottom[i];
            if (b == Integer.MAX_VALUE || b > scan.layer) continue;
            int rx = i % scan.sizeX, rz = i / scan.sizeX;
            double depth = Math.max(scan.line - b, 0.0);
            draft = Math.max(draft, depth);
            volume += depth;
            rowMin[rx] = Math.min(rowMin[rx], rz);
            rowMax[rx] = Math.max(rowMax[rx], rz);
            minX = Math.min(minX, rx);
            maxX = Math.max(maxX, rx);
            minZ = Math.min(minZ, rz);
            maxZ = Math.max(maxZ, rz);
        }
        if (minX == Integer.MAX_VALUE) return null;
        int widest = 0;
        for (int rx = minX; rx <= maxX; rx++) {
            if (rowMax[rx] >= rowMin[rx]) widest = Math.max(widest, rowMax[rx] - rowMin[rx] + 1);
        }
        // How far in from each end the rows reach nine tenths of the widest: the ends' taper (0 for a square end).
        double full = 0.9 * widest;
        int plus = maxX, minus = minX;
        while (plus > minX && (rowMax[plus] < rowMin[plus] || rowMax[plus] - rowMin[plus] + 1 < full)) plus--;
        while (minus < maxX && (rowMax[minus] < rowMin[minus] || rowMax[minus] - rowMin[minus] + 1 < full)) minus++;
        double taperPlus = widest >= 3 ? maxX - plus : 0.0;
        double taperMinus = widest >= 3 ? minus - minX : 0.0;
        return new Footprint(scan.layer, scan.gameTime, scan.minX + (minX + maxX + 1) * 0.5,
                scan.minZ + (minZ + maxZ + 1) * 0.5, (maxX - minX + 1) * 0.5, (maxZ - minZ + 1) * 0.5,
                taperPlus, taperMinus, draft, volume);
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
