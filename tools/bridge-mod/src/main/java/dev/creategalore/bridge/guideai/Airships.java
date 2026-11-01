package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Airships laid out by the server from a few wishes (open or closed, beds, chests, speed, ceiling, wood, colour), so
 * the book never wires 700 blocks by hand. The layout follows the airship rules the Field Guide teaches: a streamlined
 * envelope with the gondola close under it; the engines inside the gondola; one shaft up through the belly to the
 * envelope's axis, where the fans push in line with the envelope's middle; one Steering Wheel swinging a Wooden
 * Propeller on a Swivel Bearing under the tail; a Throttle Lever on an Analog Transmission; one Hot Air Burner under
 * the belly. A balloon hangs with its weight straight under the centre of its air, and a compact airship's lift sits
 * only a couple of blocks above its weight, so one block of gondola tips it several degrees: the layout is tried with
 * the fans at the nose, the stern or both, envelopes of several sizes, and the gondola, fins, swivel and Physics
 * Assembler in every place they can go, each worked out with the vehicle check's own sums (Sable's masses, the
 * balloon's air, propeller thrust and drag), and the smallest that does what was asked and hangs level is kept.
 */
final class Airships {
    /** The vehicle check's sums: weight per mass, balloon lift per m3, Sable's damping, envelope drag, burner fill. */
    private static final double G = 11, LIFT = 16.5, DAMP = 0.09, ENVELOPE_DRAG = 0.33, BURNER = 500, ENGINE = 2048;
    private static final int RPM = 128;
    /** Hangs this level or better fore and aft and side to side, in degrees (the vehicle check notes 1.5 and refuses 5). */
    private static final double LEVEL = 1.0;
    static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "bamboo", "crimson", "warped");
    static final List<String> COLOURS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime",
            "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    private Airships() {
    }

    /**
     * The gondola's half width: 3 (seven wide, one row of rooms along each wall), or 4 (nine wide, two rows of rooms
     * each side, reached over the inner one) when one row each side would run longer than four blocks.
     */
    static int halfWidth(Spec spec) {
        return rooms(spec, 3).stream().mapToInt(Room::end).max().orElse(0) > 4 ? 4 : 3;
    }

    /** Portable Engines side by side across the gondola, inside its walls. */
    static int maxEngines(Spec spec) {
        return 2 * halfWidth(spec) - 1;
    }

    /** What the player asked for. */
    record Spec(boolean enclosed, int beds, int storage, double speed, int ceiling, String wood, String colour) {
    }

    /** A laid-out airship: model cells from 0, its Rotation Speed Controller's setting, and what was chosen and why. */
    record Layout(Map<BlockPos, BlockState> cells, int sx, int sy, int sz, Map<BlockPos, Float> speeds, String summary) {
    }

    enum Fan {
        STERN, NOSE, BOTH
    }

    /** One layout's choices: envelope width and length, fans, engines, and the places that trim it. */
    record Knobs(int w, int l, Fan fan, int r, int engines, int gz0, int fins, int swivelIn, int steer, int ax, int az) {
        Knobs with(int gz0, int fins, int swivelIn, int steer) {
            return new Knobs(w, l, fan, r, engines, gz0, fins, swivelIn, steer, ax, az);
        }

        Knobs assembler(int x, int z) {
            return new Knobs(w, l, fan, r, engines, gz0, fins, swivelIn, steer, x, z);
        }

        Knobs withEngines(int n) {
            return new Knobs(w, l, fan, r, n, gz0, fins, swivelIn, steer, ax, az);
        }
    }

    /**
     * What a layout does, by the vehicle check's sums: mass; where the centre of the balloon's air sits from the centre
     * of mass (along the keel, across it, above it); air, envelope blocks, top speed, ceiling and the drive's stress.
     */
    record Numbers(double mass, double along, double across, double above, int air, int airtight, double speed,
                   double ceiling, double stress) {
        double pitch() {
            return Math.toDegrees(Math.atan2(along, above));
        }

        double list() {
            return Math.toDegrees(Math.atan2(across, above));
        }

        double tilt() {
            return Math.abs(pitch()) + Math.abs(list());
        }

        boolean level() {
            return Math.abs(pitch()) <= LEVEL && Math.abs(list()) <= LEVEL;
        }
    }

    /** A layout and its numbers. */
    record Try(Knobs k, Built b, Numbers n) {
    }

    /** The cells of one layout (in its own frame, x = 0 on the keel), with the places later steps need. */
    record Built(Map<BlockPos, BlockState> cells, BlockPos rsc, BlockPos assembler, int floorZ0, int floorZ1, int burners) {
    }

    // ---------- the spec ----------

    static JsonObject definition() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("enclosed", WorldTools.prop("boolean", "A closed cabin; false for an open deck (open_by_request), only if asked."));
        p.add("beds", WorldTools.prop("integer", "Beds, 0 to 4."));
        p.add("storage", WorldTools.prop("integer", "Chests, 0 to 8."));
        p.add("speed", WorldTools.prop("number", "Wanted top speed, blocks/s (8 usable, 11 fast)."));
        p.add("ceiling", WorldTools.prop("integer", "Wanted ceiling, y (200 clears mountains)."));
        p.add("wood", WorldTools.prop("string", "Gondola wood, such as spruce."));
        p.add("colour", WorldTools.prop("string", "Envelope dye colour."));
        o.add("properties", p);
        JsonArray req = new JsonArray();
        p.keySet().forEach(req::add);
        o.add("required", req);
        o.addProperty("additionalProperties", false);
        JsonObject a = new JsonObject();
        a.addProperty("type", "array");
        a.addProperty("description", "Empty, or one airship the server lays out and balances (envelope, gondola, rooms, "
                + "engines, drive, steering) in place of layers and blocks.");
        a.add("items", o);
        return a;
    }

    static Spec spec(JsonObject o, List<String> problems) {
        boolean enclosed = !o.has("enclosed") || !o.get("enclosed").isJsonPrimitive() || o.get("enclosed").getAsBoolean();
        int beds = Diagrams.intOr(o, "beds", 1), storage = Diagrams.intOr(o, "storage", 0);
        double speed = o.has("speed") && o.get("speed").isJsonPrimitive() ? o.get("speed").getAsDouble() : 9;
        int ceiling = Diagrams.intOr(o, "ceiling", 200);
        String wood = GuideText.string(o, "wood").strip().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_planks", "");
        String colour = GuideText.string(o, "colour").strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace("grey", "gray");
        if (wood.isEmpty()) wood = "spruce";
        if (colour.isEmpty()) colour = "white";
        if (beds < 0 || beds > 4) problems.add("airship beds must be 0 to 4");
        if (storage < 0 || storage > 8) problems.add("airship storage must be 0 to 8 chests");
        if (speed < 3 || speed > 16) problems.add("airship speed must be 3 to 16 blocks/s");
        if (ceiling < 100 || ceiling > 280) problems.add("airship ceiling must be y 100 to 280");
        if (!WOODS.contains(wood)) problems.add("airship wood must be one of " + String.join(", ", WOODS));
        if (!COLOURS.contains(colour)) problems.add("airship colour must be one of " + String.join(", ", COLOURS));
        return new Spec(enclosed, beds, storage, speed, ceiling, wood, colour);
    }

    // ---------- the search ----------

    /** Lays out the airship, or says why it can't (problems). */
    static Layout design(Spec spec, List<String> problems) {
        Parts parts = new Parts(spec);
        if (parts.missing != null) {
            problems.add(parts.missing);
            return null;
        }
        // Sizes: for each envelope width, fan and fan size, the shortest envelope that reaches the ceiling (with a
        // little to spare, since trimming moves things), and the next two longer: a block of length moves the air's
        // centre about half a block, between two gondola places. The gondola sits roughly under the middle for now.
        List<Try> sized = new ArrayList<>();
        for (int w : new int[] {9, 11, 13, 15})
            for (Fan fan : Fan.values())
                for (int r = 3; r <= 8; r++) {
                    int longest = Math.min((int) (2.6 * w), 28), found = 0;
                    for (int l = Math.max(w + 6, (int) Math.ceil(1.5 * w)); l <= longest && found < 3; l++) {
                        Try t = sizing(spec, parts, w, l, fan, r);
                        if (t == null) continue;
                        if (found > 0 || t.n.ceiling() >= spec.ceiling() + 4 || l == longest) {
                            sized.add(t);
                            found++;
                        }
                    }
                }
        if (sized.isEmpty()) {
            problems.add("no airship layout fits a " + Models.MAX_SIZE + "-block model for these wishes; ask for fewer beds or chests");
            return null;
        }
        // What does what was asked, cheapest first: in envelope (wool and drag), then engines (fuel), then fan size;
        // else the nearest misses.
        List<Try> meets = sized.stream().filter(t -> meets(spec, t.n)).sorted(Comparator.comparingInt((Try t) -> t.n.airtight())
                .thenComparingInt(t -> t.k.engines()).thenComparingInt(t -> t.k.fan() == Fan.BOTH ? 2 : 1)
                .thenComparingInt(t -> t.k.r())).toList();
        List<Try> pool = new ArrayList<>(meets.stream().limit(12).toList());
        sized.stream().filter(t -> !meets(spec, t.n)).sorted(Comparator.comparingDouble((Try t) -> -shortfall(spec, t.n))).limit(3)
                .forEach(pool::add);
        Try best = null;
        for (Try t : pool) {
            Try trimmed = trim(spec, parts, t);
            if (trimmed == null) continue;
            if (best == null || better(spec, trimmed, best)) best = trimmed;
            if (rank(spec, best) == 0) break;
        }
        if (best == null) {
            problems.add("no airship layout for these wishes holds its burner's air; ask for a lower ceiling or fewer beds or chests");
            return null;
        }
        return finish(spec, best, !meets(spec, best.n));
    }

    /** 0: does what was asked and hangs level; 1: does it; 2: hangs level; 3: neither. */
    private static int rank(Spec spec, Try t) {
        boolean m = meets(spec, t.n), level = t.n.level();
        return m && level ? 0 : m ? 1 : level ? 2 : 3;
    }

    private static boolean better(Spec spec, Try a, Try b) {
        int ra = rank(spec, a), rb = rank(spec, b);
        if (ra != rb) return ra < rb;
        if (ra >= 2 && Math.abs(shortfall(spec, a.n) - shortfall(spec, b.n)) > 0.02) return shortfall(spec, a.n) > shortfall(spec, b.n);
        if (a.n.airtight() != b.n.airtight()) return a.n.airtight() < b.n.airtight();
        return a.n.tilt() < b.n.tilt();
    }

    private static boolean meets(Spec spec, Numbers n) {
        return n.ceiling() >= spec.ceiling() && n.speed() >= spec.speed() && n.stress() <= maxEngines(spec) * ENGINE;
    }

    /** How near a layout comes to what was asked, 1 when it does it all. */
    private static double shortfall(Spec spec, Numbers n) {
        return Math.min(1, n.speed() / spec.speed()) + Math.min(1, (n.ceiling() - 63) / (spec.ceiling() - 63));
    }

    /** One envelope and fan, with the gondola about under the middle and as many engines as the fans need. */
    private static Try sizing(Spec spec, Parts parts, int w, int l, Fan fan, int r) {
        int gz0 = Math.max(0, (int) Math.round(0.4 * l) - 6);
        Knobs k = new Knobs(w, l, fan, r, 2, gz0, 1, 0, 1, 1, -1);
        Try t = evaluate(spec, parts, k);
        if (t == null) return null;
        int engines = (int) Math.ceil(t.n.stress() / ENGINE);
        if (engines > maxEngines(spec)) return null;
        if (engines != 2) t = evaluate(spec, parts, k.withEngines(Math.max(2, engines)));
        return t;
    }

    /**
     * Trims a sized layout: every gondola position, fin set, swivel position and steering side, then the Physics
     * Assembler (one heavy block anywhere in the floor) for the last fraction of a degree.
     */
    private static Try trim(Spec spec, Parts parts, Try sized) {
        Knobs k0 = sized.k;
        // The gondola positions that come within a few degrees (one block tips a compact airship several), then every
        // fin set, swivel position and steering side at each.
        List<Integer> near = new ArrayList<>();
        for (int gz0 = 0; gz0 <= k0.l(); gz0++) {
            Try t = evaluate(spec, parts, k0.with(gz0, 1, 0, 1));
            if (t != null && Math.abs(t.n.pitch()) <= 9) near.add(gz0);
        }
        Try best = null;
        for (int gz0 : near)
            for (int fins = 0; fins <= 2; fins++)
                for (int swivelIn = 0; swivelIn <= 4; swivelIn++)
                    for (int steer : new int[] {1, -1}) {
                        Try t = evaluate(spec, parts, k0.with(gz0, fins, swivelIn, steer));
                        if (t == null || Math.abs(t.n.pitch()) > 6 || t.n.stress() > k0.engines() * ENGINE) continue;
                        t = assembler(spec, parts, t);
                        if (t == null) continue;
                        if (best == null || t.n.tilt() < best.n.tilt() - 0.05
                                || Math.abs(t.n.tilt() - best.n.tilt()) <= 0.05 && t.k.swivelIn() < best.k.swivelIn())
                            best = t;
                    }
        return best;
    }

    /** The assembler's best place: a block of floor anywhere it can go, by its own weight's pull (no air changes); null if none. */
    private static Try assembler(Spec spec, Parts parts, Try t) {
        BlockPos at = t.b.assembler();
        double mass = t.n.mass(), m = Parts.mass(parts.assembler);
        // Moving it by (dx, dz) moves the centre of mass by m x d / mass, and the lift sits that much the other way. It
        // goes only where nothing stands on it, so the crew can reach it from the floor.
        double bestTilt = t.b.cells().containsKey(at.above()) ? Double.MAX_VALUE : t.n.tilt();
        int bx = at.getX(), bz = at.getZ(), hw = halfWidth(spec);
        for (int x = -hw + 1; x <= hw - 1; x++)
            for (int z = t.b.floorZ0(); z <= t.b.floorZ1(); z++) {
                if (x == 0) continue;
                BlockPos q = new BlockPos(x, 0, z);
                BlockState s = t.b.cells().get(q);
                if (!q.equals(at) && (s == null || s != parts.planks) || t.b.cells().containsKey(q.above())) continue;
                double along = t.n.along() - m * (z - at.getZ()) / mass, across = t.n.across() - m * (x - at.getX()) / mass;
                double tilt = Math.abs(Math.toDegrees(Math.atan2(along, t.n.above()))) + Math.abs(Math.toDegrees(Math.atan2(across, t.n.above())));
                if (tilt < bestTilt - 1e-6) {
                    bestTilt = tilt;
                    bx = x;
                    bz = z;
                }
            }
        if (bestTilt == Double.MAX_VALUE) return null;
        if (bx == at.getX() && bz == at.getZ()) return t;
        Try moved = evaluate(spec, parts, t.k.assembler(bx, bz));
        return moved == null ? t : moved;
    }

    private static Try evaluate(Spec spec, Parts parts, Knobs k) {
        Built b = lay(spec, parts, k);
        if (b == null || !fits(b.cells())) return null;
        Numbers n = numbers(b.cells(), k);
        return n == null ? null : new Try(k, b, n);
    }

    /** Whether it fits in a model (at most MAX_SIZE a side). */
    private static boolean fits(Map<BlockPos, BlockState> c) {
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos p : c.keySet()) {
            lo[0] = Math.min(lo[0], p.getX());
            lo[1] = Math.min(lo[1], p.getY());
            lo[2] = Math.min(lo[2], p.getZ());
            hi[0] = Math.max(hi[0], p.getX());
            hi[1] = Math.max(hi[1], p.getY());
            hi[2] = Math.max(hi[2], p.getZ());
        }
        for (int i = 0; i < 3; i++) if (hi[i] - lo[i] + 1 > Models.MAX_SIZE) return false;
        return true;
    }

    // ---------- the layout ----------

    /** The blocks this layout's wishes and knobs give, x = 0 on the keel, the envelope from z = 0, the floor at y = 0. */
    static Built lay(Spec spec, Parts parts, Knobs k) {
        Map<BlockPos, BlockState> c = new HashMap<>();
        int w = k.w(), l = k.l(), half = (w - 1) / 2, hw = halfWidth(spec);
        int ye0 = 5, ya = ye0 + half, ye1 = ye0 + w - 1;
        Envelope env = Envelope.of(w, l, ye0);
        for (int x = -half; x <= half; x++)
            for (int y = ye0; y <= ye1; y++)
                for (int z = 0; z < l; z++)
                    if (env.skin(x, y, z)) c.put(new BlockPos(x, y, z), parts.envelope);
        // The gondola's rows from the front: rail or wall, helm, the pilot's row (doors), rooms, burner, engines, chain
        // drives, speed controller, the shaft up, rail or wall.
        int gz0 = k.gz0();
        int zh = gz0 + 1, zp = gz0 + 2, zr = gz0 + 3;
        List<Room> rooms = rooms(spec, hw);
        int roomRows = rooms.stream().mapToInt(Room::end).max().orElse(0);
        int zb = zr + roomRows, ze = zb + 1, zc = ze + 1, zs = ze + 2, zk = ze + 3, gz1 = zk + 1, zt = gz1 + 1;
        int x0 = -hw, x1 = hw;
        for (int x = x0; x <= x1; x++)
            for (int z = gz0; z <= gz1; z++) c.put(new BlockPos(x, 0, z), parts.planks);
        if (spec.enclosed()) {
            for (int y = 1; y <= 3; y++) {
                for (int z = gz0; z <= gz1; z++) {
                    c.put(new BlockPos(x0, y, z), parts.planks);
                    c.put(new BlockPos(x1, y, z), parts.planks);
                }
                for (int x = x0; x <= x1; x++) {
                    c.put(new BlockPos(x, y, gz0), parts.planks);
                    c.put(new BlockPos(x, y, gz1), parts.planks);
                }
            }
            for (int x = x0; x <= x1; x++)
                for (int z = gz0; z <= gz1; z++) c.put(new BlockPos(x, 4, z), parts.planks);
            // Windows ahead and down the front of each side, so the pilot sees out.
            for (int y = 2; y <= 3; y++) {
                for (int x = x0 + 1; x < x1; x++) c.put(new BlockPos(x, y, gz0), parts.paneX);
                for (int z = gz0 + 1; z < zp; z++) {
                    c.put(new BlockPos(x0, y, z), parts.paneZ);
                    c.put(new BlockPos(x1, y, z), parts.paneZ);
                }
                for (int z = zp + 1; z < zb; z += 2) {
                    c.put(new BlockPos(x0, y, z), parts.paneZ);
                    c.put(new BlockPos(x1, y, z), parts.paneZ);
                }
            }
            c.put(new BlockPos(x0, 1, zp), parts.door(Direction.EAST, false, true));
            c.put(new BlockPos(x0, 2, zp), parts.door(Direction.EAST, true, true));
            c.put(new BlockPos(x1, 1, zp), parts.door(Direction.WEST, false, false));
            c.put(new BlockPos(x1, 2, zp), parts.door(Direction.WEST, true, false));
        } else {
            for (int z = gz0; z <= gz1; z++) {
                c.put(new BlockPos(x0, 1, z), parts.fence);
                c.put(new BlockPos(x1, 1, z), parts.fence);
            }
            for (int x = x0; x <= x1; x++) {
                c.put(new BlockPos(x, 1, gz0), parts.fence);
                c.put(new BlockPos(x, 1, gz1), parts.fence);
            }
            // A gap each side to board by, and a post of planks where the steering shaft leaves the back.
            c.remove(new BlockPos(x0, 1, zp));
            c.remove(new BlockPos(x1, 1, zp));
            c.put(new BlockPos(0, 1, gz1), parts.planks);
            for (int end : new int[] {gz0, gz1}) {
                int dir = end == gz0 ? 1 : -1;
                for (int side : new int[] {-1, 1}) post(c, env, hw, side, end, dir, parts);
            }
        }
        // The helm: the wheel on the keel looking toward the bow, its shaft down into the floor and back along it to the
        // steering trunk behind the gondola; the Throttle Lever beside it on a Redstone Link to the transmission.
        c.put(new BlockPos(0, 1, zh), parts.wheel);
        c.put(new BlockPos(0, 0, zh), parts.gearboxX);
        c.put(new BlockPos(-1, 1, zh), parts.throttle);
        c.put(new BlockPos(-2, 1, zh), parts.linkOut);
        int ax = k.ax(), az = k.az() < 0 ? zp : k.az();
        if (az < gz0 + 1 || az > gz1 - 1 || ax == 0 || Math.abs(ax) > hw - 1) return null;
        BlockPos assembler = new BlockPos(ax, 0, az);
        for (int z = zh + 1; z < zt; z++) c.put(new BlockPos(0, 0, z), parts.shaft(Direction.Axis.Z));
        c.put(assembler, parts.assembler);
        for (Room room : rooms) room.place(c, zr, parts);
        // The burner under the belly, its column clear up into the air, and its lever; an envelope holding more air
        // than one burner fills (500 m3) gets a pair, one each side of the keel.
        int[] burnerXs = env.interior() > BURNER + 20 ? new int[] {-2, 2} : new int[] {0};
        for (int bx : burnerXs) {
            c.put(new BlockPos(bx, 1, zb), parts.burner);
            if (bx == 0) c.put(new BlockPos(1, 1, zb), parts.lever);
            else c.put(new BlockPos(bx / 2, 1, zb), parts.wallLever(bx < 0 ? Direction.EAST : Direction.WEST));
            int y = 2;
            while (y <= ye1 && !env.interior(bx, y, zb)) {
                c.remove(new BlockPos(bx, y, zb));
                y++;
            }
            if (y > ye1) return null;
        }
        int y;
        // The engine room: Portable Engines facing aft into a row of chain drives, which turn as one; the middle one
        // drives the speed controller, whose large cogwheel turns the throttle's transmission and the shaft up.
        // Side by side about the keel, an even number leaving the keel's chain drive between them.
        int span = k.engines() / 2;
        if (span > hw - 1) return null;
        for (int x = -span; x <= span; x++)
            if (x != 0 || k.engines() % 2 == 1) c.put(new BlockPos(x, 1, ze), parts.engine);
        for (int x = -span; x <= span; x++) c.put(new BlockPos(x, 1, zc), parts.chainZ);
        BlockPos rsc = new BlockPos(0, 1, zs);
        c.put(rsc, parts.rsc);
        c.put(new BlockPos(0, 2, zs), parts.largeCogX);
        c.put(new BlockPos(-1, 2, zs), parts.gearboxZ);
        c.put(new BlockPos(-1, 3, zs), parts.transmission);
        c.put(new BlockPos(-2, 3, zs), parts.linkIn);
        c.put(new BlockPos(-1, 3, zk), parts.cogY);
        for (y = 4; y < ya; y++) shaft(c, env, new BlockPos(-1, y, zk), Direction.Axis.Y, parts);
        // The line along the envelope's axis, to the fans and to the swivel's propeller.
        int tail = -1, nose = l;
        for (int z = 0; z < l; z++)
            if (env.inside(0, ya, z)) {
                tail = Math.max(tail, z);
                nose = Math.min(nose, z);
            }
        // The swivel hangs from a gearbox in the line, so that gearbox sits inside the balloon, not in its skin.
        int zsw = -1;
        for (int z = tail; z > zt + 1; z--) {
            int low = env.low(0, z);
            if (low != Integer.MIN_VALUE && low - 2 >= 3 && env.interior(0, ya, z)) {
                zsw = z;
                break;
            }
        }
        if (zsw < 0) return null;
        zsw -= k.swivelIn();
        if (zsw <= zt + 1) return null;
        // The shaft's top turns into the line with gearboxes and cogwheels, which would let the air out in the skin.
        boolean sealed = k.fan() == Fan.STERN ? env.interior(-1, ya, zk) && env.interior(0, ya, zk)
                : env.interior(-1, ya, zk) && env.interior(-1, ya, zk + 1) && env.interior(0, ya, zk + 1);
        if (!sealed) return null;
        int z0;
        if (k.fan() == Fan.STERN) {
            c.put(new BlockPos(-1, ya, zk), parts.gearboxZ);
            c.put(new BlockPos(0, ya, zk), parts.gearboxY);
            z0 = zk + 1;
        } else {
            // The shaft's top turns the line both ways along x = -1; aft it steps onto the axis with two cogwheels,
            // ahead it steps back onto it just past the burner, whose column must stay clear to the envelope's top.
            c.put(new BlockPos(-1, ya, zk), parts.gearboxX);
            c.put(new BlockPos(-1, ya, zk + 1), parts.cogZ);
            c.put(new BlockPos(0, ya, zk + 1), parts.cogZ);
            z0 = zk + 2;
            int zj = -1;
            for (int z = zb - 1; z >= nose; z--)
                if (env.interior(0, ya, z) && env.interior(-1, ya, z)) {
                    zj = z;
                    break;
                }
            if (zj < 0) return null;
            for (int z = zj + 1; z < zk; z++) shaft(c, env, new BlockPos(-1, ya, z), Direction.Axis.Z, parts);
            c.put(new BlockPos(-1, ya, zj), parts.cogZ);
            c.put(new BlockPos(0, ya, zj), parts.cogZ);
            for (int z = nose; z < zj; z++) shaft(c, env, new BlockPos(0, ya, z), Direction.Axis.Z, parts);
            fan(c, new BlockPos(0, ya, nose - 1), Direction.NORTH, k.r(), parts);
        }
        int zend = k.fan() == Fan.NOSE ? zsw + 1 : tail + 1;
        for (int z = z0; z < zend; z++) {
            if (z == zsw) c.put(new BlockPos(0, ya, z), parts.gearboxX);
            else shaft(c, env, new BlockPos(0, ya, z), Direction.Axis.Z, parts);
        }
        if (k.fan() != Fan.NOSE) fan(c, new BlockPos(0, ya, tail + 1), Direction.SOUTH, k.r(), parts);
        // The swivel: a shaft down from the axis through the belly, the bearing under it swinging a gearbox and a
        // Wooden Propeller pushing aft; the wheel's line swings it through a cogwheel beside it.
        int ysw = env.low(0, zsw) - 1;
        for (y = ya - 1; y > ysw; y--) shaft(c, env, new BlockPos(0, y, zsw), Direction.Axis.Y, parts);
        c.put(new BlockPos(0, ysw, zsw), parts.swivel);
        c.put(new BlockPos(0, ysw - 1, zsw), parts.gearboxX);
        c.put(new BlockPos(0, ysw - 1, zsw + 1), parts.woodenProp);
        // The wheel's line steps aside behind the gondola, one block off the keel, and runs aft just under the belly
        // (outside the envelope, so it takes none of the air) to a gearbox under the swivel's steering cogwheel.
        int sx = k.steer(), yLine = ysw - 1;
        for (int z = zt; z <= zsw; z++) {
            int low = env.low(sx, z);
            if (low != Integer.MIN_VALUE) yLine = Math.min(yLine, low - 1);
        }
        if (yLine < 2) return null;
        c.put(new BlockPos(0, 0, zt), parts.gearboxY);
        c.put(new BlockPos(sx, 0, zt), parts.gearboxZ);
        for (y = 1; y < yLine; y++) c.put(new BlockPos(sx, y, zt), parts.shaft(Direction.Axis.Y));
        c.put(new BlockPos(sx, yLine, zt), parts.gearboxX);
        for (int z = zt + 1; z < zsw; z++) c.put(new BlockPos(sx, yLine, z), parts.shaft(Direction.Axis.Z));
        c.put(new BlockPos(sx, yLine, zsw), parts.gearboxX);
        for (y = yLine + 1; y < ysw; y++) c.put(new BlockPos(sx, y, zsw), parts.shaft(Direction.Axis.Y));
        c.put(new BlockPos(sx, ysw, zsw), parts.cogY);
        // Fins of Symmetric Sails: one upright over the tail, and with 2 a flat one out each side.
        int zf = tail - 2;
        if (k.fins() >= 1) {
            int top = env.high(0, zf);
            for (int z = zf - 1; z <= zf + 1; z++)
                for (int yy = top + 1; yy <= top + 2; yy++) c.putIfAbsent(new BlockPos(0, yy, z), parts.finUp);
        }
        if (k.fins() >= 2)
            for (int side : new int[] {-1, 1}) {
                int edge = env.edge(side, ya, zf);
                for (int z = zf - 1; z <= zf + 1; z++)
                    for (int d = 1; d <= 2; d++) c.putIfAbsent(new BlockPos(edge + side * d, ya, z), parts.finFlat);
            }
        return new Built(c, rsc, assembler, gz0 + 1, gz1 - 1, burnerXs.length);
    }

    /** A shaft along an axis, encased in envelope where it passes through the skin so the balloon stays sealed. */
    private static void shaft(Map<BlockPos, BlockState> c, Envelope env, BlockPos p, Direction.Axis axis, Parts parts) {
        boolean skin = c.get(p) == parts.envelope;
        c.put(p, skin ? parts.encased(axis) : parts.shaft(axis));
    }

    /** A Propeller Bearing facing out along the keel, its casing and a cross of sails r long each way. */
    private static void fan(Map<BlockPos, BlockState> c, BlockPos bearing, Direction facing, int r, Parts parts) {
        c.put(bearing, parts.bearing(facing));
        BlockPos hub = bearing.relative(facing);
        c.put(hub, parts.casing);
        for (int d = 1; d <= r; d++) {
            c.put(hub.offset(d, 0, 0), parts.sail(facing));
            c.put(hub.offset(-d, 0, 0), parts.sail(facing));
            c.put(hub.offset(0, d, 0), parts.sail(facing));
            c.put(hub.offset(0, -d, 0), parts.sail(facing));
        }
    }

    /**
     * A fence post from the rail up to the envelope at one end of the gondola, as near its corner as the envelope
     * above allows (Super Glue holds the gondola to the envelope through it).
     */
    private static void post(Map<BlockPos, BlockState> c, Envelope env, int hw, int side, int end, int dir, Parts parts) {
        for (int in = 0; in <= 4; in++)
            for (int x = hw; x >= 1; x--) {
                int z = end + dir * in, px = side * x;
                int low = env.low(px, z);
                if (low == Integer.MIN_VALUE || low > 2 + 8) continue;
                for (int y = 1; y < low; y++) c.put(new BlockPos(px, y, z), parts.fence);
                return;
            }
    }

    /** A bed (two cells along the keel) or a chest against one side wall, or a bed on the keel; rows from the first room row. */
    private record Room(boolean bed, int x, int row) {
        int end() {
            return row + (bed ? 2 : 1);
        }

        void place(Map<BlockPos, BlockState> c, int zr, Parts parts) {
            if (bed) {
                c.put(new BlockPos(x, 1, zr + row), parts.bed(true));
                c.put(new BlockPos(x, 1, zr + row + 1), parts.bed(false));
            } else c.put(new BlockPos(x, 1, zr + row), parts.chest(x < 0 ? Direction.EAST : Direction.WEST));
        }
    }

    /**
     * Beds and chests in rows along the side walls (one row each side when the gondola is seven wide, two when nine),
     * beds first, each into the shortest row, the wall's row first; one bed alone goes on the keel.
     */
    private static List<Room> rooms(Spec spec, int hw) {
        List<Room> rooms = new ArrayList<>();
        if (spec.beds() == 1 && spec.storage() == 0) {
            rooms.add(new Room(true, 0, 0));
            return rooms;
        }
        int[] xs = hw >= 4 ? new int[] {-(hw - 1), hw - 1, -(hw - 2), hw - 2} : new int[] {-(hw - 1), hw - 1};
        int[] fill = new int[xs.length];
        for (int i = 0; i < spec.beds() + spec.storage(); i++) {
            boolean bed = i < spec.beds();
            int row = 0;
            for (int j = 1; j < xs.length; j++) if (fill[j] < fill[row]) row = j;
            rooms.add(new Room(bed, xs[row], fill[row]));
            fill[row] += bed ? 2 : 1;
        }
        return rooms;
    }

    /**
     * The streamlined envelope's shape, as build_model's streamlined_shell draws it (tail toward +z), worked out once
     * per size: which cells are inside it and which are its skin (inside, with any neighbour outside).
     */
    private static final class Envelope {
        private static final Map<Long, Envelope> SIZES = new java.util.concurrent.ConcurrentHashMap<>();
        final int ax, bx, ay, by, az, bz;
        private final boolean[][][] in, skin;
        private int interior;

        static Envelope of(int w, int l, int ye0) {
            return SIZES.computeIfAbsent(((long) w << 32) | ((long) l << 16) | ye0, key -> new Envelope(w, l, ye0));
        }

        private Envelope(int w, int l, int ye0) {
            int half = (w - 1) / 2;
            ax = -half;
            bx = half;
            ay = ye0;
            by = ye0 + w - 1;
            az = 0;
            bz = l - 1;
            in = new boolean[w + 2][w + 2][l + 2];
            skin = new boolean[w][w][l];
            for (int x = ax - 1; x <= bx + 1; x++)
                for (int y = ay - 1; y <= by + 1; y++)
                    for (int z = az - 1; z <= bz + 1; z++)
                        in[x - ax + 1][y - ay + 1][z - az + 1] = Models.inStreamlined(x, y, z, ax, bx, ay, by, az, bz, "south");
            for (int x = ax; x <= bx; x++)
                for (int y = ay; y <= by; y++)
                    for (int z = az; z <= bz; z++) {
                        if (!inside(x, y, z)) continue;
                        boolean out = false;
                        for (int dx = -1; dx <= 1 && !out; dx++)
                            for (int dy = -1; dy <= 1 && !out; dy++)
                                for (int dz = -1; dz <= 1 && !out; dz++) if (!inside(x + dx, y + dy, z + dz)) out = true;
                        skin[x - ax][y - ay][z - az] = out;
                        if (!out) interior++;
                    }
        }

        /** How many cells lie inside the skin. */
        int interior() {
            return interior;
        }

        boolean inside(int x, int y, int z) {
            if (x < ax - 1 || x > bx + 1 || y < ay - 1 || y > by + 1 || z < az - 1 || z > bz + 1) return false;
            return in[x - ax + 1][y - ay + 1][z - az + 1];
        }

        boolean skin(int x, int y, int z) {
            return x >= ax && x <= bx && y >= ay && y <= by && z >= az && z <= bz && skin[x - ax][y - ay][z - az];
        }

        boolean interior(int x, int y, int z) {
            return inside(x, y, z) && !skin(x, y, z);
        }

        /** The lowest envelope cell over (x, z), or MIN_VALUE. */
        int low(int x, int z) {
            for (int y = ay; y <= by; y++) if (inside(x, y, z)) return y;
            return Integer.MIN_VALUE;
        }

        int high(int x, int z) {
            for (int y = by; y >= ay; y--) if (inside(x, y, z)) return y;
            return ay;
        }

        /** The outermost envelope cell toward side (-1 or 1) at height y and z. */
        int edge(int side, int y, int z) {
            int e = 0;
            for (int x = 0; side > 0 ? x <= bx : x >= ax; x += side) if (inside(x, y, z)) e = x;
            return e;
        }
    }

    // ---------- the sums ----------

    /** The vehicle check's sums for one layout (see VehicleCheck: hang, air, terminal speed, ceiling, drive). */
    static Numbers numbers(Map<BlockPos, BlockState> c, Knobs k) {
        double m = 0, mx = 0, my = 0, mz = 0;
        int airtight = 0;
        double stress = 0, force = 0, overAir = 0;
        double p0 = VehicleCheck.pressure(100);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        BlockPos burner = null;
        int burners = 0;
        for (Map.Entry<BlockPos, BlockState> e : c.entrySet()) {
            BlockPos p = e.getKey();
            BlockState s = e.getValue();
            double mass = Parts.mass(s);
            m += mass;
            mx += mass * (p.getX() + 0.5);
            my += mass * (p.getY() + 0.5);
            mz += mass * (p.getZ() + 0.5);
            if (Parts.airtight(s)) airtight++;
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
            String id = Parts.id(s);
            if (id.equals("aeronautics:adjustable_burner")) {
                burner = p;
                burners++;
            }
            if (id.equals("aeronautics:propeller_bearing")) {
                int sails = 4 * k.r();
                double f = 0.2 * Math.pow(sails, 1.5) * RPM;
                force += f * p0;
                overAir += f * p0 / (0.05 * Math.sqrt(sails) * RPM);
                stress += 2.0 * sails * RPM;
            } else if (id.equals("aeronautics:wooden_propeller")) {
                force += RPM * p0;
                overAir += RPM * p0 / (0.1 * RPM);
                stress += 4.0 * RPM;
            } else {
                stress += Parts.impact(s) * RPM;
            }
        }
        if (burner == null || m <= 0) return null;
        mx /= m;
        my /= m;
        mz /= m;
        Set<BlockPos> air = air(c, burner, minX, minY, minZ, maxX, maxY, maxZ);
        if (air.isEmpty()) return null;
        double ax = 0, ay = 0, az = 0;
        for (BlockPos a : air) {
            ax += a.getX() + 0.5;
            ay += a.getY() + 0.5;
            az += a.getZ() + 0.5;
        }
        ax /= air.size();
        ay /= air.size();
        az /= air.size();
        double filled = Math.min(air.size(), BURNER * burners), above = ay - my;
        double need = G * m / (LIFT * filled);
        double ceiling = Math.min(280, 63 - Math.log(need) / 0.004 - above);
        double linear = DAMP * m + ENVELOPE_DRAG * p0 * airtight;
        double speed = force <= 0 ? 0 : force / (linear + overAir);
        // Along the keel, toward the stern (+z) positive: lift there leaves the bow down.
        return new Numbers(m, az - mz, ax - mx, above, air.size(), airtight, speed, ceiling, stress);
    }

    /** The balloon's air, filled the way the vehicle check (and Aeronautics) fills it from the burner. */
    private static Set<BlockPos> air(Map<BlockPos, BlockState> c, BlockPos burner, int minX, int minY, int minZ, int maxX, int maxY,
                                     int maxZ) {
        BlockPos up = burner.above();
        while (up.getY() <= maxY + 1 && !solid(c, up)) up = up.above();
        BlockState roof = c.get(up);
        if (roof == null || !Parts.airtight(roof)) return Set.of();
        BlockPos seed = up.below();
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(seed));
        cells.add(seed);
        Direction[] spread = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            for (Direction d : spread) {
                BlockPos n = p.relative(d);
                if (cells.contains(n)) continue;
                BlockState s = c.get(n);
                if (s != null && Parts.airtight(s)) continue;
                if (n.getX() < minX || n.getX() > maxX || n.getY() < minY || n.getY() > maxY || n.getZ() < minZ || n.getZ() > maxZ)
                    return Set.of();
                cells.add(n);
                todo.add(n);
            }
        }
        int bottom = seed.getY();
        while (true) {
            Set<BlockPos> layer = new HashSet<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            boolean open = false;
            for (BlockPos p : cells)
                if (p.getY() == bottom) {
                    BlockPos below = p.below();
                    BlockState s = c.get(below);
                    if ((s == null || !Parts.airtight(s)) && layer.add(below)) q.add(below);
                }
            while (!q.isEmpty() && !open) {
                BlockPos p = q.poll();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (layer.contains(n)) continue;
                    BlockState s = c.get(n);
                    if (s != null && Parts.airtight(s)) continue;
                    if (n.getX() < minX || n.getX() > maxX || n.getY() < minY || n.getY() > maxY || n.getZ() < minZ || n.getZ() > maxZ) {
                        open = true;
                        break;
                    }
                    layer.add(n);
                    q.add(n);
                }
            }
            if (open || layer.isEmpty()) break;
            cells.addAll(layer);
            bottom--;
        }
        cells.removeIf(c::containsKey);
        return cells;
    }

    private static boolean solid(Map<BlockPos, BlockState> c, BlockPos p) {
        BlockState s = c.get(p);
        return s != null && !s.getCollisionShape(One.of(s), BlockPos.ZERO).isEmpty();
    }

    // ---------- the result ----------

    private static Layout finish(Spec spec, Try t, boolean missed) {
        Map<BlockPos, BlockState> cells = t.b.cells();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cells.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        BlockPos shift = new BlockPos(-minX, -minY, -minZ);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        cells.forEach((p, s) -> out.put(p.offset(shift), s));
        int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
        Map<BlockPos, Float> speeds = Map.of(t.b.rsc().offset(shift), (float) RPM);
        Knobs k = t.k;
        Numbers n = t.n;
        BlockPos rsc = t.b.rsc().offset(shift);
        StringBuilder s = new StringBuilder("Laid out by the server from airship (for you: tell the player what it is and how "
                + "to build and fly it):\n");
        s.append(String.format(Locale.ROOT, "- A %d x %d x %d %s envelope (streamlined, %d blocks, %d m3 of air) over a %d x %d %s "
                        + "%s gondola close under it; %d x %d x %d in all, bow toward -z.\n", k.w(), k.w(), k.l(),
                spec.colour().replace('_', ' '), n.airtight(), n.air(), 2 * halfWidth(spec) + 1, t.b.floorZ1() - t.b.floorZ0() + 3,
                spec.wood().replace('_', ' '), spec.enclosed() ? "closed" : "open", sx, sy, sz));
        String fans = switch (k.fan()) {
            case STERN -> "a " + 4 * k.r() + "-sail fan behind the tail";
            case NOSE -> "a " + 4 * k.r() + "-sail fan ahead of the nose";
            case BOTH -> "two " + 4 * k.r() + "-sail fans, one ahead of the nose and one behind the tail";
        };
        s.append(String.format(Locale.ROOT, "- %s on the envelope's axis, driven by %d Portable Engines in the gondola through "
                        + "the Rotation Speed Controller at %d,%d,%d (set it to %d RPM) and one shaft up through the belly; "
                        + "the engines' %.0f stress units cover the %.0f the propellers and swivel draw.\n",
                Character.toUpperCase(fans.charAt(0)) + fans.substring(1), k.engines(), rsc.getX(), rsc.getY(), rsc.getZ(), RPM,
                k.engines() * ENGINE, n.stress()));
        s.append("- Flown with one Steering Wheel (it swings the Wooden Propeller on the Swivel Bearing under the tail) and one "
                + "Throttle Lever (on the Analog Transmission by Redstone Links: give both links the same two frequency items); "
                + (t.b.burners() > 1 ? "a lever beside each of its two Hot Air Burners turns" : "a lever beside the Hot Air Burner turns")
                + " the lift on and off.\n");
        if (spec.beds() + spec.storage() > 0)
            s.append(String.format(Locale.ROOT, "- %s aboard.\n", rooms(spec.beds(), spec.storage())));
        s.append(String.format(Locale.ROOT, "- Worked out: about %.1f blocks/s, ceiling about y %.0f, hanging %.1f° fore and aft and "
                        + "%.1f° side to side (the full check below has the exact figures).\n", n.speed(), n.ceiling(),
                Math.abs(n.pitch()), Math.abs(n.list())));
        s.append(String.format(Locale.ROOT, "- Why this one: of the envelopes tried (9 to 15 wide, fans at the nose, the tail or both), "
                        + "the smallest, then the fewest engines, that %s; its lift centres only %.1f blocks above its weight, so it was balanced by trying "
                        + "every place for the gondola, fins, swivel and Physics Assembler.\n",
                missed ? String.format(Locale.ROOT, "comes nearest to %.0f blocks/s and y %d (no layout up to 15 wide with %d engines "
                        + "reaches both: tell the player what it does instead)", spec.speed(), spec.ceiling(), maxEngines(spec))
                        : String.format(Locale.ROOT, "reaches %.0f blocks/s and y %d", spec.speed(), spec.ceiling()), n.above()));
        double tip = Math.toDegrees(Math.atan2(40.0 / n.mass(), n.above()));
        s.append(String.format(Locale.ROOT, "- Keep its heavy parts where they are when you revise it (from_draft): moving 4 mass "
                + "(an engine, a bed, a gearbox and a shaft) 10 blocks tips it about %.0f°. Light trim (a slab, a pane, a "
                + "banner) changes little; check every version.\n", tip));
        if (!spec.enclosed()) s.append("- Open deck, as asked: above y 128 the wind chills whoever is aboard and above y 192 "
                + "it freezes them, so tell the player to sew Windproof Lining onto each armour piece first.\n");
        return new Layout(out, sx, sy, sz, speeds, s.toString());
    }

    private static String rooms(int beds, int storage) {
        List<String> r = new ArrayList<>();
        if (beds > 0) r.add(beds + " bed" + (beds == 1 ? "" : "s"));
        if (storage > 0) r.add(storage + " chest" + (storage == 1 ? "" : "s"));
        return String.join(" and ", r);
    }

    // ---------- blocks ----------

    /** The block states a layout uses, in the wished-for wood and colour. */
    static final class Parts {
        private static final Map<BlockState, Double> MASS = new HashMap<>();
        final String missing;
        final BlockState envelope, planks, fence, paneX, paneZ, wheel, throttle, linkOut, linkIn, assembler, burner, lever,
                engine, chainZ, rsc, largeCogX, gearboxX, gearboxY, gearboxZ, transmission, cogY, cogZ, swivel, woodenProp,
                casing, finUp, finFlat;
        private final Map<String, BlockState> cache = new HashMap<>();
        private final Spec spec;

        Parts(Spec spec) {
            this.spec = spec;
            String w = spec.wood(), col = spec.colour();
            envelope = get("aeronautics:" + col + "_envelope");
            planks = get("minecraft:" + w + "_planks");
            fence = get("minecraft:" + w + "_fence");
            paneX = get("minecraft:glass_pane[east=true,west=true]");
            paneZ = get("minecraft:glass_pane[north=true,south=true]");
            wheel = get("simulated:steering_wheel[facing=north]");
            throttle = get("simulated:throttle_lever[facing=north,inverted=true]");
            linkOut = get("create:redstone_link[facing=up,receiver=false]");
            linkIn = get("create:redstone_link[facing=west,receiver=true]");
            assembler = get("simulated:physics_assembler[facing=north]");
            burner = get("aeronautics:adjustable_burner");
            lever = get("minecraft:lever[face=floor,facing=north]");
            engine = get("simulated:red_portable_engine[facing=south]");
            chainZ = get("create:encased_chain_drive[axis=z]");
            rsc = get("create:rotation_speed_controller[axis=z]");
            largeCogX = get("create:large_cogwheel[axis=x]");
            gearboxX = get("create:gearbox[axis=x]");
            gearboxY = get("create:gearbox[axis=y]");
            gearboxZ = get("create:gearbox[axis=z]");
            transmission = get("simulated:analog_transmission[axis=y]");
            cogY = get("create:cogwheel[axis=y]");
            cogZ = get("create:cogwheel[axis=z]");
            swivel = get("simulated:swivel_bearing[facing=down]");
            woodenProp = get("aeronautics:wooden_propeller[facing=south]");
            casing = get("create:andesite_casing");
            finUp = coloured("simulated:%s_symmetric_sail[axis=x]");
            finFlat = coloured("simulated:%s_symmetric_sail[axis=y]");
            String gone = null;
            for (Map.Entry<String, BlockState> e : cache.entrySet()) if (e.getValue() == null) gone = e.getKey();
            missing = gone == null ? null : "the airship layout needs " + gone + ", which this server doesn't have";
        }

        BlockState shaft(Direction.Axis a) {
            return get("create:shaft[axis=" + a.getName() + "]");
        }

        BlockState encased(Direction.Axis a) {
            return get("aeronautics:" + spec.colour() + "_envelope_encased_shaft[axis=" + a.getName() + "]");
        }

        BlockState bearing(Direction f) {
            return get("aeronautics:propeller_bearing[facing=" + f.getName() + "]");
        }

        BlockState sail(Direction f) {
            return coloured("create:%s_sail[facing=" + f.getName() + "]");
        }

        BlockState bed(boolean head) {
            return coloured("minecraft:%s_bed[facing=north,part=" + (head ? "head" : "foot") + "]");
        }

        BlockState chest(Direction f) {
            return get("minecraft:chest[facing=" + f.getName() + "]");
        }

        /** A lever on the side of the block behind it, pointing f (away from that block). */
        BlockState wallLever(Direction f) {
            return get("minecraft:lever[face=wall,facing=" + f.getName() + "]");
        }

        BlockState door(Direction f, boolean upper, boolean left) {
            return get("minecraft:" + spec.wood() + "_door[facing=" + f.getName() + ",half=" + (upper ? "upper" : "lower") + ",hinge="
                    + (left ? "left" : "right") + "]");
        }

        /** The block in the wished colour, else white. */
        private BlockState coloured(String pattern) {
            BlockState s = Models.state(String.format(Locale.ROOT, pattern, spec.colour()));
            return s != null ? s : get(String.format(Locale.ROOT, pattern, "white"));
        }

        private BlockState get(String raw) {
            if (cache.containsKey(raw)) return cache.get(raw);
            BlockState s = Models.state(raw);
            cache.put(raw, s);
            return s;
        }

        static String id(BlockState s) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        }

        static boolean airtight(BlockState s) {
            return VehicleCheck.isAirtight(s);
        }

        static double mass(BlockState s) {
            return MASS.computeIfAbsent(s, st -> VehicleCheck.mass(One.of(st), BlockPos.ZERO, st));
        }

        static double impact(BlockState s) {
            try {
                return com.simibubi.create.api.stress.BlockStressValues.getImpact(s.getBlock());
            } catch (RuntimeException | LinkageError e) {
                return 0;
            }
        }
    }

    /** A world of one block at the origin, for the per-block sums (Sable's mass reads the block's own shape). */
    private record One(BlockState state) implements BlockGetter {
        static One of(BlockState s) {
            return new One(s);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }
}
