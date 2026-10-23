package dev.creategalore.bridge;

import com.mojang.logging.LogUtils;
import dev.creategalore.bridge.mixin.ChunkMapVisibleInvoker;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * Route planning for Aeronautics Discovery's self-flying craft (the flyover planes, the patrols, the Soaring Trader).
 * Their autopilot held a heading and a cruise height and nothing else, so a plane met a sky island or a sky fortress
 * head on, crashed, and scattered loot meant for players who can already fly. Each pilot now looks along its course
 * for anything it would fly into and plans around it: over the top when a climb it can make in time clears it,
 * otherwise a banked turn onto the nearest heading that is clear, and it never sinks back onto something it is
 * passing over.
 *
 * <p>What lies ahead comes from two places, neither of which loads or generates a chunk:
 * <ul>
 * <li>Structure outlines. A chunk knows the structures that start in it (with every piece's box) from the first step
 *     of its generation, long before any of their blocks exist, and the server holds chunks at that step in a wide
 *     ring around everything loaded. Only chunks the server already holds are read.</li>
 * <li>A short look along the course over real blocks, in fully loaded chunks only (mountains, player builds,
 *     anything that isn't a structure).</li>
 * </ul>
 * Server thread only; one {@link State} per autopilot.
 */
public final class FlyoverSkyRoute {
    private static final Logger LOG = LogUtils.getLogger();
    /** Logs every manoeuvre: -Dcreate_galore.sky_route.debug=true */
    private static final boolean DEBUG = Boolean.getBoolean("create_galore.sky_route.debug");

    /** How far ahead a course is checked, in blocks. A plane covers this in about 13 s. */
    private static final double LOOKAHEAD = 224.0;
    /** Space kept between the craft's hull and anything it passes, in blocks. */
    private static final double MARGIN = 10.0;
    /** Structure outlines are read again after this many ticks, or sooner once the craft moved 32 blocks. */
    private static final int SCAN_TICKS = 10;
    /** Structure pieces per chunk, for chunks whose structure starts are worked out (they never change). */
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<double[][]>> KNOWN = new WeakHashMap<>();
    private static final int MAX_KNOWN_CHUNKS = 1 << 16;
    private static final double[][] NO_PIECES = new double[0][];
    /** Real blocks are looked at this far ahead, every few ticks. */
    private static final double BLOCK_LOOK = 160.0;
    private static final int BLOCK_LOOK_TICKS = 5;
    private static final int BLOCK_FAN_DEGREES = 30;
    private static final int BLOCK_FAN_STEP = 10;
    private static final int BLOCK_MEMORY_TICKS = 100;
    private static final int MAX_BLOCK_HITS = 96;
    /** The highest climb taken instead of a turn; a bigger one goes around. */
    private static final double MAX_CLIMB = 40.0;
    /** Climb angle asked of the pilot, and the share of it the craft is counted on to hold. */
    private static final double CLIMB_PITCH = Math.toRadians(18.0);
    private static final double CLIMB_GRADIENT = Math.tan(Math.toRadians(6.0));
    private static final double MAX_BANK = Math.toRadians(30.0);
    /** Headings tried for a turn, either side of the current one. */
    private static final int TURN_STEP_DEGREES = 10;
    private static final int TURN_MAX_DEGREES = 90;
    /** Back on its course when within this angle of it. */
    private static final double RETURN_DONE = Math.toRadians(4.0);
    /** A manoeuvre is held this long after the course clears, so the craft doesn't swing back into it. */
    private static final int HOLD_TICKS = 30;

    public static final class State {
        private final List<double[]> structures = new ArrayList<>();
        private final List<double[]> blocks = new ArrayList<>();
        private long scannedAt = Long.MIN_VALUE / 2;
        private long lookedAt = Long.MIN_VALUE / 2;
        private double scanX;
        private double scanZ;
        /** -1 or 1 while turning, 0 otherwise. */
        private int side;
        /** The height being climbed to, or NaN. */
        private double climbTo = Double.NaN;
        private int hold;
        /** The heading the craft had before its detour, NaN when it isn't on one. */
        private double courseX = Double.NaN;
        private double courseZ = Double.NaN;
        private String last = "";
    }

    public static AutopilotBias steer(State state, AutopilotContext context, AutopilotBias planned) {
        if (!(context.subLevel() instanceof ServerSubLevel ship)) return planned;
        RigidBodyHandle handle = RigidBodyHandle.of(ship);
        if (handle == null || !handle.isValid()) return planned;
        Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        double speed = Math.hypot(velocity.x, velocity.z);
        if (speed < 0.5) return planned;

        ServerLevel level = context.level();
        Vector3d at = context.worldPosition();
        double ux = velocity.x / speed;
        double uz = velocity.z / speed;
        BoundingBox3dc hull = ship.boundingBox();
        Craft craft = new Craft(at.x, at.y, at.z,
            Math.max(hull.maxX() - hull.minX(), hull.maxZ() - hull.minZ()) / 2.0 + MARGIN,
            Math.max(0.0, at.y - hull.minY()) + MARGIN,
            Math.max(0.0, hull.maxY() - at.y) + MARGIN);

        long now = level.getGameTime();
        if (now - state.scannedAt >= SCAN_TICKS || Math.hypot(at.x - state.scanX, at.z - state.scanZ) > 32.0) {
            scanStructures(level, craft, state.structures);
            state.scannedAt = now;
            state.scanX = at.x;
            state.scanZ = at.z;
        }
        if (now - state.lookedAt >= BLOCK_LOOK_TICKS) {
            lookAtBlocks(level, craft, ux, uz, now, state.blocks);
            state.lookedAt = now;
        }
        List<double[]> obstacles = new ArrayList<>(state.structures.size() + state.blocks.size());
        obstacles.addAll(state.structures);
        obstacles.addAll(state.blocks);

        double pitch = planned.pitch();
        double roll = planned.roll();
        String doing;

        // The highest thing the craft is over or about to be over: never sink onto it.
        double floor = floor(obstacles, craft, ux, uz);
        double ahead = firstHit(obstacles, craft, ux, uz, craft.y, LOOKAHEAD);
        if (ahead < 0.0 && !Double.isNaN(state.climbTo) && craft.y < state.climbTo && floor > craft.y - 1.0) {
            ahead = 0.0;
        }
        if (ahead >= 0.0) {
            state.hold = HOLD_TICKS;
            if (Double.isNaN(state.courseX)) {
                state.courseX = ux;
                state.courseZ = uz;
            }
            double top = topOfBlocking(obstacles, craft, ux, uz, LOOKAHEAD);
            double climb = top - craft.y;
            boolean canClimb = climb <= MAX_CLIMB
                && top + craft.above < level.getMaxBuildHeight() - 2
                && (!Double.isNaN(state.climbTo) || climb <= Math.max(ahead - 16.0, 0.0) * CLIMB_GRADIENT)
                && firstHit(obstacles, craft, ux, uz, top + 1.0, LOOKAHEAD) < 0.0;
            if (canClimb && state.side == 0) {
                state.climbTo = top;
                pitch = Math.max(pitch, CLIMB_PITCH);
                doing = "climbing to " + Math.round(top) + " over something " + Math.round(ahead) + " ahead";
            } else {
                state.climbTo = Double.NaN;
                int side = pickSide(state, obstacles, craft, ux, uz, context.roll());
                state.side = side == 0 ? (state.side == 0 ? 1 : state.side) : side;
                double urgency = Mth.clamp(1.0 - ahead / LOOKAHEAD, 0.0, 1.0);
                roll = state.side * MAX_BANK * (0.35 + 0.65 * urgency);
                doing = "turning " + (state.side > 0 ? "right" : "left") + " around something " + Math.round(ahead) + " ahead";
            }
        } else if (state.hold > 0) {
            state.hold--;
            if (state.side != 0) roll = 0.0;
            doing = "holding course";
        } else if (!Double.isNaN(state.courseX) && planned.roll() == 0.0) {
            // Around it: back onto the heading it had before the detour, once that heading is clear from here.
            double off = Math.atan2(ux * state.courseZ - uz * state.courseX, ux * state.courseX + uz * state.courseZ);
            if (Math.abs(off) < RETURN_DONE) {
                state.courseX = Double.NaN;
                state.side = 0;
                doing = "";
            } else if (firstHit(obstacles, craft, state.courseX, state.courseZ, craft.y, LOOKAHEAD) < 0.0) {
                roll = Mth.clamp(off, -MAX_BANK * 0.6, MAX_BANK * 0.6);
                doing = "turning back onto its course";
            } else {
                doing = "passing it";
            }
            state.climbTo = Double.NaN;
        } else {
            // A flight-path goal of its own (an orbit) steers it back, or there was no detour.
            state.side = 0;
            state.climbTo = Double.NaN;
            state.courseX = Double.NaN;
            doing = "";
        }
        if (floor > craft.y - 2.0 && pitch < 0.0) {
            pitch = 0.0;
            if (doing.isEmpty()) doing = "staying above something below";
        }
        String kind = doing.replaceAll(" \\d+.*", "");
        if (DEBUG && !kind.equals(state.last)) {
            LOG.info("Sky route {} at {} {} {}: {}", ship.getUniqueId(), Math.round(at.x), Math.round(at.y), Math.round(at.z),
                doing.isEmpty() ? "course clear" : doing);
        }
        state.last = kind;
        return pitch == planned.pitch() && roll == planned.roll() ? planned : new AutopilotBias(pitch, roll);
    }

    /** The craft's position and how far its hull (plus margin) reaches: sideways, below and above. */
    private record Craft(double x, double y, double z, double half, double below, double above) {}

    /** Picks the turn: the smallest one that clears, keeping to the side already chosen when both clear alike. */
    private static int pickSide(State state, List<double[]> obstacles, Craft craft, double ux, double uz, double currentRoll) {
        int prefer = state.side != 0 ? state.side : (int) Math.signum(currentRoll);
        double bestFar = -1.0;
        int bestSide = 0;
        for (int deg = TURN_STEP_DEGREES; deg <= TURN_MAX_DEGREES; deg += TURN_STEP_DEGREES) {
            boolean clearRight = firstHit(obstacles, craft, rotX(ux, uz, deg), rotZ(ux, uz, deg), craft.y, LOOKAHEAD) < 0.0;
            boolean clearLeft = firstHit(obstacles, craft, rotX(ux, uz, -deg), rotZ(ux, uz, -deg), craft.y, LOOKAHEAD) < 0.0;
            if (clearRight && clearLeft) return prefer != 0 ? prefer : 1;
            if (clearRight) return 1;
            if (clearLeft) return -1;
        }
        // Nothing clears within a quarter turn: go to the side whose obstacle is farthest off.
        for (int s = -1; s <= 1; s += 2) {
            double far = firstHit(obstacles, craft, rotX(ux, uz, s * TURN_MAX_DEGREES), rotZ(ux, uz, s * TURN_MAX_DEGREES), craft.y, LOOKAHEAD);
            if (far < 0.0) far = LOOKAHEAD;
            if (far > bestFar) {
                bestFar = far;
                bestSide = s;
            }
        }
        return bestSide;
    }

    // Same rotation as Discovery's own avoid goal, so a positive angle is the side a positive bank turns to.
    private static double rotX(double ux, double uz, double deg) {
        double a = Math.toRadians(deg);
        return ux * Math.cos(a) - uz * Math.sin(a);
    }

    private static double rotZ(double ux, double uz, double deg) {
        double a = Math.toRadians(deg);
        return ux * Math.sin(a) + uz * Math.cos(a);
    }

    /**
     * Distance along the heading to the first obstacle the craft would touch at height y, or -1. An obstacle the
     * craft is already inside of (its outline reaches around the craft, which then is in its empty space) is left out:
     * there is no way around that from here, and real blocks in it are still looked at.
     */
    private static double firstHit(List<double[]> obstacles, Craft craft, double dx, double dz, double y, double length) {
        double best = -1.0;
        for (double[] box : obstacles) {
            if (y <= box[1] - craft.above || y >= box[4] + craft.below) continue;
            if (craft.x > box[0] && craft.x < box[3] && craft.z > box[2] && craft.z < box[5] && craft.y > box[1] && craft.y < box[4]) continue;
            double t = enter(craft.x, craft.z, dx, dz, box[0] - craft.half, box[2] - craft.half, box[3] + craft.half, box[5] + craft.half, length);
            if (t >= 0.0 && (best < 0.0 || t < best)) best = t;
        }
        return best;
    }

    /** The top of everything in the way along the heading at the craft's height, plus the room under the hull. */
    private static double topOfBlocking(List<double[]> obstacles, Craft craft, double dx, double dz, double length) {
        double top = craft.y;
        for (double[] box : obstacles) {
            if (craft.y <= box[1] - craft.above || craft.y >= box[4] + craft.below) continue;
            if (enter(craft.x, craft.z, dx, dz, box[0] - craft.half, box[2] - craft.half, box[3] + craft.half, box[5] + craft.half, length) >= 0.0) {
                top = Math.max(top, box[4] + craft.below);
            }
        }
        return top;
    }

    /** The lowest height the craft may sink to: above anything under it or on its course within a short way. */
    private static double floor(List<double[]> obstacles, Craft craft, double dx, double dz) {
        double floor = Double.NEGATIVE_INFINITY;
        for (double[] box : obstacles) {
            if (box[4] + craft.below < craft.y - 24.0) continue;
            if (enter(craft.x, craft.z, dx, dz, box[0] - craft.half, box[2] - craft.half, box[3] + craft.half, box[5] + craft.half, 64.0) >= 0.0) {
                floor = Math.max(floor, box[4] + craft.below);
            }
        }
        return floor;
    }

    /** Slab test in the horizontal plane: where the segment from (x, z) enters the box, or -1. Inside counts as 0. */
    private static double enter(double x, double z, double dx, double dz, double minX, double minZ, double maxX, double maxZ, double length) {
        double t0 = 0.0;
        double t1 = length;
        if (Math.abs(dx) < 1e-9) {
            if (x <= minX || x >= maxX) return -1.0;
        } else {
            double a = (minX - x) / dx;
            double b = (maxX - x) / dx;
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        if (Math.abs(dz) < 1e-9) {
            if (z <= minZ || z >= maxZ) return -1.0;
        } else {
            double a = (minZ - z) / dz;
            double b = (maxZ - z) / dz;
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        return t0 <= t1 ? t0 : -1.0;
    }

    /**
     * Collects the pieces of every structure that starts within reach, from chunks the server holds whose structure
     * starts are worked out. Pieces well below the craft are skipped; a piece box is the structure's real extent, so a
     * floating island is one box and not the column under it.
     */
    private static void scanStructures(ServerLevel level, Craft craft, List<double[]> out) {
        out.clear();
        // A structure's pieces reach up to 8 chunks from the chunk it starts in.
        int radius = (int) Math.ceil(LOOKAHEAD / 16.0) + 9;
        int cx = Mth.floor(craft.x) >> 4;
        int cz = Mth.floor(craft.z) >> 4;
        double reach = LOOKAHEAD + craft.half + 16.0;
        Long2ObjectOpenHashMap<double[][]> known = KNOWN.computeIfAbsent(level, l -> new Long2ObjectOpenHashMap<>());
        if (known.size() > MAX_KNOWN_CHUNKS) known.clear();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                long key = ChunkPos.asLong(x, z);
                double[][] pieces = known.get(key);
                if (pieces == null) {
                    pieces = readPieces(level, key);
                    if (pieces == null) continue;
                    known.put(key, pieces);
                }
                for (double[] b : pieces) {
                    if (b[4] + craft.below < craft.y - 48.0) continue;
                    if (b[3] > craft.x - reach && b[0] < craft.x + reach && b[5] > craft.z - reach && b[2] < craft.z + reach) out.add(b);
                }
            }
        }
    }

    /**
     * The above-ground pieces of the structures starting in this chunk, or null while the server doesn't hold the chunk
     * or hasn't worked out its starts yet. Once worked out they never change, so the answer is kept.
     */
    private static double[][] readPieces(ServerLevel level, long key) {
        ChunkHolder holder = ((ChunkMapVisibleInvoker) level.getChunkSource().chunkMap).create_galore_bridge$visible(key);
        if (holder == null) return null;
        ChunkAccess chunk = holder.getLatestChunk();
        // From this step on a chunk's starts are final; before it a worldgen thread may still be writing them.
        if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) return null;
        List<StructureStart> starts;
        try {
            starts = List.copyOf(chunk.getAllStarts().values());
        } catch (RuntimeException e) {
            return null;
        }
        if (starts.isEmpty()) return NO_PIECES;
        List<double[]> pieces = new ArrayList<>();
        for (StructureStart start : starts) {
            if (!start.isValid() || start.getBoundingBox().maxY() < level.getSeaLevel()) continue;
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                if (b.maxY() < level.getSeaLevel()) continue;
                // A box from underground to the build limit isn't a shape (some mineshaft pieces claim the whole
                // column); whatever really stands there is still seen by the block look.
                if (b.minY() < level.getSeaLevel() && b.maxY() >= level.getMaxBuildHeight() - 1) continue;
                pieces.add(new double[] {b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1});
            }
        }
        return pieces.isEmpty() ? NO_PIECES : pieces.toArray(new double[0][]);
    }

    /**
     * Looks ahead in a fan for solid blocks, stopping at the first chunk that isn't fully loaded. A block found becomes
     * a column up to the top of the ground there, so the planner can tell whether a climb clears it, and is remembered
     * for a few seconds so a wall stays known while the craft turns along it.
     */
    private static void lookAtBlocks(ServerLevel level, Craft craft, double ux, double uz, long now, List<double[]> seen) {
        seen.removeIf(hit -> hit[6] < now);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int deg = -BLOCK_FAN_DEGREES; deg <= BLOCK_FAN_DEGREES; deg += BLOCK_FAN_STEP) {
            double dx = rotX(ux, uz, deg);
            double dz = rotZ(ux, uz, deg);
            for (double y : new double[] {craft.y - craft.below + MARGIN + 1.0, craft.y}) {
                LevelChunk chunk = null;
                for (double t = 2.0; t <= BLOCK_LOOK; t += 1.0) {
                    pos.set(craft.x + dx * t, y, craft.z + dz * t);
                    if (chunk == null || chunk.getPos().x != pos.getX() >> 4 || chunk.getPos().z != pos.getZ() >> 4) {
                        chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                        if (chunk == null) break;
                    }
                    if (!chunk.getBlockState(pos).blocksMotion()) continue;
                    double top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) + 1.0;
                    int x = pos.getX();
                    int z = pos.getZ();
                    seen.removeIf(hit -> hit[0] == x - 1.0 && hit[2] == z - 1.0);
                    seen.add(new double[] {x - 1.0, y - 48.0, z - 1.0, x + 2.0, Math.max(top, y + 1.0), z + 2.0, now + BLOCK_MEMORY_TICKS});
                    break;
                }
            }
        }
        while (seen.size() > MAX_BLOCK_HITS) seen.remove(0);
    }

    private FlyoverSkyRoute() {}
}
