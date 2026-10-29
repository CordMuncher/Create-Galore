package dev.creategalore.bridge.guideai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Whether a vehicle would work and be usable, worked out from its blocks with the ship mods' own force rules: no
 * simulation, just the sums those rules come to. Masses and buoyant volumes come from Sable's own block properties;
 * buoyancy is Sable's 10.5 per submerged block plus High Seas' 16.5 per submerged hold cell (a boat), and 16.5 x the open
 * part of a slab, stair or other partial block beside the hold (the Bridge's HighSeasHoldFloorsMixin); lift is
 * Aeronautics' 16.5 per filled m3 of balloon times air pressure and Levitite's 10 mass per block (hover only); thrust
 * is the Boat Engine's 36 x mass, a Submarine Propeller's 3 x RPM (x16 / x81 in a 2x2 / 3x3 square), a propeller's
 * 1.0 x RPM or a Propeller Bearing's 0.2 x sails^1.5 x RPM (each only up to its airflow speed), a thruster's rated
 * force with 10 clear blocks behind it, an Offroad wheel's 1.75 x RPM; drag is Sable's 0.09 x mass, water's 1.7 per
 * wet block, High Seas' hull drag, 0.33 per envelope block. From those: does it float or lift, does it stay upright,
 * its top speed, turn rate, climb and ceiling, and whether those are fast enough to be of use.
 */
final class VehicleCheck {
    private static final double G = 11, WATER = 10.5, HOLD = 16.5, DAMP = 0.09, WATER_DRAG = 1.7, ENVELOPE_DRAG = 0.33;
    /** Usable thresholds, blocks a second and degrees a second: below the first a vehicle is of no real use. */
    private static final double BOAT_BAD = 2, BOAT_SLOW = 4, SUB_BAD = 1, SUB_SLOW = 2, AIR_BAD = 2.5, AIR_SLOW = 5,
            CAR_BAD = 3, CAR_SLOW = 6, AIRSHIP_GOOD = 8, TURN_BAD = 3, TURN_SLOW = 8, CLIMB_BAD = 0.5, CLIMB_SLOW = 1, CEILING_BAD = 100;
    /** A hull in water turns about half as fast as its turning force alone gives (measured; it slides sideways). */
    private static final double TURN_SLIP = 0.45;
    /** Resting trim or list, degrees: from the first it shows, from the second it is a fault. */
    private static final double TRIM_NOTE = 1.5, TRIM_BAD = 5;
    /** The height an airship is judged at for lifting off (above most ground, below most mountains). */
    private static final double TAKEOFF_Y = 100;

    private static final TagKey<Block> AIRTIGHT = tag("aeronautics:airtight"), LEVITITE = tag("aeronautics:levitite"),
            SAILS = tag("create:windmill_sails"), HIGH_SEAS_SAILS = tag("create_high_seas:sails"),
            STORAGE_BLOCKS = tag("c:storage_blocks");

    private VehicleCheck() {
    }

    private record Part(BlockPos pos, BlockState state, String id, double mass, double vol, boolean shell) {
        double cx() {
            return pos.getX() + 0.5;
        }

        double cy() {
            return pos.getY() + 0.5;
        }

        double cz() {
            return pos.getZ() + 0.5;
        }
    }

    /** A force along one horizontal or vertical direction, at a block. */
    private record Thrust(BlockPos at, Direction dir, double force, double airflow, String what, boolean reversible) {
    }

    /**
     * Null when the blocks hold no vehicle part. {@code rpm} gives each kinetic block's speed (a model's from its
     * sources, a real build's as it turns now); {@code model} says whether these are a design rather than a real build.
     */
    static BuildCheck.Report check(Map<BlockPos, BlockState> blocks, BlockGetter getter, Map<BlockPos, Float> rpm, boolean model) {
        try {
            return run(blocks, getter == null ? EmptyBlockGetter.INSTANCE : getter, rpm == null ? Map.of() : rpm, model);
        } catch (RuntimeException | LinkageError e) {
            return new BuildCheck.Report("(The vehicle check couldn't run on these blocks: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    private static BuildCheck.Report run(Map<BlockPos, BlockState> blocks, BlockGetter getter, Map<BlockPos, Float> rpm, boolean model) {
        boolean sub = false, water = false, air = false, land = false;
        for (BlockState s : blocks.values()) {
            String id = id(s);
            if (id.equals("create_submarine:oxygene_diffuser") || id.endsWith(":hull_controller") || id.equals("create_submarine:ballast_tank")) sub = true;
            if (id.equals("create_high_seas:boat_engine") || id.equals("create_submarine:submarine_propeller") || id.equals("create_submarine:water_thruster")
                    || id.equals("aeronauticswinds:keel") || id.equals("aeronauticswinds:rudder") || id.equals("create_high_seas:rudder")
                    || s.is(HIGH_SEAS_SAILS)) water = true;
            if (id.equals("aeronautics:adjustable_burner") || id.equals("aeronautics:steam_vent") || s.is(LEVITITE)) air = true;
            if (id.equals("offroad:wheel_mount")) land = true;
        }
        boolean props = blocks.values().stream().anyMatch(s -> isPropeller(id(s)) || isThruster(id(s)) || liftScalar(s) > 0);
        if (!sub && !water && !air && !land && !props) return null;

        List<Part> parts = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockState s = e.getValue();
            double mass = mass(getter, e.getKey(), s);
            if (mass <= 0 && s.getCollisionShape(getter, e.getKey()).isEmpty()) continue;
            parts.add(new Part(e.getKey(), s, id(s), mass, volume(s), !interior(blocks, getter, e.getKey())));
        }
        if (parts.isEmpty()) return null;
        double m = 0, cx = 0, cy = 0, cz = 0;
        for (Part p : parts) {
            m += p.mass;
            cx += p.mass * p.cx();
            cy += p.mass * p.cy();
            cz += p.mass * p.cz();
        }
        if (m <= 0) return null;
        cx /= m;
        cy /= m;
        cz /= m;
        Body body = new Body(parts, m, cx, cy, cz, blocks, driveAxis(parts));

        List<String> faults = new ArrayList<>(), notes = new ArrayList<>();
        StringBuilder out = new StringBuilder("How this vehicle floats, flies and handles, worked out from its blocks (estimates)"
                + BuildCheck.FOR_YOU + ":\n");
        out.append(String.format(Locale.ROOT, "- Mass %.0f (Sable mass units; weight %.0f), %d blocks, %d x %d x %d.\n", m, G * m,
                parts.size(), body.lenX, body.height, body.lenZ));
        if (air) air(body, rpm, out, faults, notes);
        else if (sub) sub(body, rpm, out, faults, notes);
        else if (water) boat(body, rpm, out, faults, notes, false);
        else if (land) land(body, rpm, out, faults, notes);
        else if (props) {
            // Propellers or wings alone: a boat if one of them is a water propeller, else judged as an aircraft.
            air(body, rpm, out, faults, notes);
        }
        if (getter instanceof net.minecraft.server.level.ServerLevel level) drive(body, rpm, level, faults);
        pieces(blocks, model, faults, notes);
        if (model) ballast(blocks, getter, faults);
        boatEngines(blocks, getter, faults);
        controls(blocks, getter, faults);
        PlayerCheck.vehicle(blocks, body.ceiling, air || land || (props && !water && !sub), faults, notes);
        boolean redstone = blocks.values().stream().anyMatch(s -> s.isSignalSource() || id(s).contains("throttle_lever"));
        if (redstone) {
            boolean wire = blocks.values().stream().anyMatch(s -> s.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)
                    || s.is(net.minecraft.world.level.block.Blocks.REPEATER) || id(s).contains("redstone_link"));
            List<String> unpowered = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
                String id = id(e.getValue());
                if (wire || !(id.equals("aeronautics:adjustable_burner") || isThruster(id))) continue;
                boolean near = false;
                for (BlockPos q : BlockPos.betweenClosed(e.getKey().offset(-2, -2, -2), e.getKey().offset(2, 2, 2))) {
                    BlockState s = blocks.get(q);
                    if (s != null && (s.isSignalSource() || id(s).contains("throttle_lever"))) near = true;
                }
                if (!near) unpowered.add(e.getValue().getBlock().getName().getString() + " at " + xyz(e.getKey()));
            }
            if (!unpowered.isEmpty()) notes.add("no lever, Throttle Lever or wire reaches " + String.join(", ", unpowered)
                    + "; each needs a redstone signal to work, so give it one");
        }
        if (!redstone && blocks.values().stream().anyMatch(s -> id(s).equals("aeronautics:adjustable_burner") || isThruster(id(s))))
            faults.add("nothing gives its burners or thrusters a redstone signal, and they do nothing without one: put a Throttle "
                    + "Lever or lever on board next to them (the signal strength sets how hard they work)");
        if (model && blocks.values().stream().noneMatch(s -> id(s).equals("simulated:physics_assembler")))
            notes.add("it needs a Physics Assembler (with the blocks glued) to become a moving ship");
        for (String n : notes) out.append("- ").append(n).append(".\n");
        if (faults.isEmpty()) out.append("- Nothing here stops it working.\n");
        else for (String f : faults) out.append("- PROBLEM: ").append(f).append(".\n");
        return new BuildCheck.Report(out.toString().trim(), faults);
    }

    /**
     * Super Glue only takes blocks that share a face: its selection grows face to face from the first block, and a
     * selection with blocks that touch the rest only along an edge or at a corner is refused ("Selected blocks must be
     * connected"). So every piece of a ship has to share a face with the rest.
     */
    private static void pieces(Map<BlockPos, BlockState> blocks, boolean model, List<String> faults, List<String> notes) {
        Set<BlockPos> left = new HashSet<>();
        blocks.forEach((p, s) -> {
            if (!s.isAir() && !(s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) left.add(p);
        });
        List<List<BlockPos>> found = new ArrayList<>();
        while (!left.isEmpty()) {
            BlockPos start = left.iterator().next();
            left.remove(start);
            List<BlockPos> piece = new ArrayList<>(List.of(start));
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(piece);
            while (!todo.isEmpty()) {
                BlockPos p = todo.poll();
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                            BlockPos q = p.offset(dx, dy, dz);
                            if (left.remove(q)) {
                                piece.add(q);
                                todo.add(q);
                            }
                        }
            }
            found.add(piece);
        }
        if (found.size() < 2) return;
        found.sort((a, b) -> b.size() - a.size());
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < Math.min(found.size(), 4); i++)
            rest.add(found.get(i).size() + " block" + (found.get(i).size() == 1 ? "" : "s") + " at " + xyz(found.get(i).get(0)));
        String text = String.format(Locale.ROOT, "it is in %d pieces that don't share a face (the main one %d blocks; %s%s): Super "
                + "Glue joins only blocks that touch face to face, and refuses a selection where blocks touch the rest only "
                + "diagonally (along an edge or at a corner), so a stepped or diagonal hull can't be glued as it is; fill the "
                + "steps so each block shares a face with the next (stairs and slabs work), and give a balloon posts or a frame "
                + "down to the deck", found.size(),
                found.get(0).size(), String.join("; ", rest), found.size() > 4 ? "; and more" : "");
        if (model) faults.add(text);
        else notes.add(text.replace("it is in", "the marked build is in"));
    }

    /** The horizontal axis most of its propellers, thrusters and Boat Engines push along; null when none push sideways. */
    private static Direction.Axis driveAxis(List<Part> parts) {
        int x = 0, z = 0;
        for (Part p : parts) {
            if (!isPropeller(p.id) && !isThruster(p.id) && !p.id.equals("create_high_seas:boat_engine")
                    && !p.id.equals("create_submarine:submarine_propeller")) continue;
            Direction f = facing(p.state);
            if (f == null) continue;
            if (f.getAxis() == Direction.Axis.X) x++;
            else if (f.getAxis() == Direction.Axis.Z) z++;
        }
        return x == z ? null : x > z ? Direction.Axis.X : Direction.Axis.Z;
    }

    /**
     * Each propeller drive line against the engines on it: Create stops a whole network when what it turns draws more stress
     * than its sources give (a Portable Engine 2048 at its 32 RPM, whatever the gears do to the speed), so an engine that
     * isn't on the line, or one too few, leaves the propellers standing still.
     */
    private static void drive(Body b, Map<BlockPos, Float> rpm, net.minecraft.server.level.ServerLevel level, List<String> faults) {
        Map<BlockPos, Part> at = new java.util.HashMap<>();
        for (Part p : b.parts) at.put(p.pos, p);
        Set<BlockPos> done = new HashSet<>();
        for (Part p : b.parts) {
            if (!isPropeller(p.id) || done.contains(p.pos) || rpm.getOrDefault(p.pos, 0f) == 0) continue;
            Set<BlockPos> line = BuildCheck.network(b.blocks, level, p.pos);
            done.addAll(line);
            double need = 0, give = 0;
            List<String> props = new ArrayList<>();
            int engines = 0;
            for (BlockPos q : line) {
                BlockState s = b.blocks.get(q);
                if (s == null) continue;
                float r = Math.abs(rpm.getOrDefault(q, 0f));
                String id = id(s);
                if (isPropeller(id) && at.containsKey(q)) {
                    need += propellerStress(b, at.get(q), rpm);
                    props.add(xyz(q));
                } else {
                    need += com.simibubi.create.api.stress.BlockStressValues.getImpact(s.getBlock()) * r;
                }
                double cap = com.simibubi.create.api.stress.BlockStressValues.getCapacity(s.getBlock());
                if (cap <= 0 && id.endsWith("portable_engine")) cap = 64;
                if (cap > 0 && r > 0) {
                    give += cap * r;
                    engines++;
                }
            }
            if (need <= give + 1) continue;
            int more = (int) Math.ceil((need - give) / 2048);
            faults.add(String.format(Locale.ROOT, "the propeller%s at %s draw%s about %.0f stress units, but the %d engine%s on %s "
                    + "drive line give%s %.0f, so Create stalls the whole line and nothing turns: put %d more Portable Engine%s on "
                    + "that same line (each gives 2048 at any gearing; its front must meet a shaft end or a cogwheel's axle), or "
                    + "draw less (fewer sails, or a lower gear)", props.size() == 1 ? "" : "s", String.join(", ", props),
                    props.size() == 1 ? "s" : "", need, engines, engines == 1 ? "" : "s", props.size() == 1 ? "its" : "their",
                    engines == 1 ? "s" : "", give, more, more == 1 ? "" : "s"));
        }
    }

    /** " (+z)" and so on: a way named by the model's axis. */
    private static String axis(Direction d) {
        return " (" + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? "+" : "-") + d.getAxis().getName() + ")";
    }

    /** Whether the vehicle check looks at these blocks at all: they hold something that floats, flies, drives or pushes. */
    static boolean vehicle(Map<BlockPos, BlockState> blocks) {
        for (BlockState s : blocks.values()) {
            String id = id(s);
            if (id.equals("create_submarine:oxygene_diffuser") || id.endsWith(":hull_controller") || id.equals("create_submarine:ballast_tank")
                    || id.equals("create_high_seas:boat_engine") || id.equals("create_submarine:submarine_propeller")
                    || id.equals("create_submarine:water_thruster") || id.equals("aeronauticswinds:keel") || id.equals("aeronauticswinds:rudder")
                    || id.equals("create_high_seas:rudder") || s.is(HIGH_SEAS_SAILS) || id.equals("aeronautics:adjustable_burner")
                    || id.equals("aeronautics:steam_vent") || s.is(LEVITITE) || id.equals("offroad:wheel_mount")
                    || isPropeller(id) || isThruster(id) || liftScalar(s) > 0) return true;
        }
        return false;
    }

    /**
     * Fills the corner steps of a vehicle model so every block shares a face with the rest, as Super Glue needs: where two
     * pieces touch only along an edge, the empty cell between them on the inner side of the curve (the one with more blocks
     * around it, so a rounded envelope keeps its outline) takes the block around it; a touch at a single corner first gets
     * one cell to turn it into an edge. Gaps wider than that are left for the check to name. Returns the cells filled.
     */
    static List<BlockPos> closeSteps(Map<BlockPos, BlockState> cells, int sx, int sy, int sz) {
        List<BlockPos> added = new ArrayList<>();
        if (!vehicle(cells)) return added;
        java.util.function.Predicate<BlockPos> inBox = q -> q.getX() >= 0 && q.getY() >= 0 && q.getZ() >= 0 && q.getX() < sx
                && q.getY() < sy && q.getZ() < sz;
        java.util.function.Predicate<BlockPos> open = q -> inBox.test(q) && !cells.containsKey(q);
        for (int pass = 0; pass < 96; pass++) {
            Map<BlockPos, Integer> label = labels(cells);
            if (new HashSet<>(label.values()).size() < 2) break;
            Set<Long> edges = edgePairs(label);
            java.util.TreeSet<BlockPos> candidates = new java.util.TreeSet<>(java.util.Comparator.<BlockPos>comparingInt(q -> q.getY())
                    .thenComparingInt(q -> q.getZ()).thenComparingInt(q -> q.getX()));
            for (BlockPos p : label.keySet()) for (Direction d : Direction.values()) if (open.test(p.relative(d))) candidates.add(p.relative(d));
            List<BlockPos> ranked = new ArrayList<>();
            Map<BlockPos, Integer> score = new java.util.HashMap<>();
            for (BlockPos c : candidates) {
                if (overBurner(cells, c)) continue;
                Set<Integer> faces = new HashSet<>();
                for (Direction d : Direction.values()) {
                    Integer l = label.get(c.relative(d));
                    if (l != null) faces.add(l);
                }
                boolean joins = faces.size() >= 2;
                // Or it turns a corner touch into an edge one: a block of another piece meets it along an edge.
                boolean steps = false;
                if (!joins && faces.size() == 1) {
                    int own = faces.iterator().next();
                    for (int dx = -1; dx <= 1 && !steps; dx++)
                        for (int dy = -1; dy <= 1 && !steps; dy++)
                            for (int dz = -1; dz <= 1 && !steps; dz++) {
                                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 2) continue;
                                Integer l = label.get(c.offset(dx, dy, dz));
                                if (l != null && l != own && !edges.contains(pair(own, l))) steps = true;
                            }
                }
                if (!joins && !steps) continue;
                int around = 0;
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) if (label.containsKey(q)) around++;
                score.put(c, around + (joins ? 1000 : 0));
                ranked.add(c);
            }
            // Highest score first; the candidates came in a fixed order, and the sort keeps it among equals.
            ranked.sort((a, b) -> score.get(b) - score.get(a));
            BlockPos best = null;
            BlockState fill = null;
            for (BlockPos c : ranked) {
                fill = fillFor(cells, c);
                if (fill != null) {
                    best = c;
                    break;
                }
            }
            if (best == null || added.size() >= 48) break;
            cells.put(best, fill);
            added.add(best);
        }
        return added;
    }

    /** A cell in a burner's or steam vent's open column: a block there would become the balloon's roof over it. */
    private static boolean overBurner(Map<BlockPos, BlockState> cells, BlockPos c) {
        for (BlockPos q = c.below(); q.getY() >= 0; q = q.below()) {
            BlockState s = cells.get(q);
            if (s == null || s.isAir()) continue;
            String id = id(s);
            return id.equals("aeronautics:adjustable_burner") || id.equals("aeronautics:steam_vent");
        }
        return false;
    }

    /** Face-connected pieces of the solid blocks, by number. */
    private static Map<BlockPos, Integer> labels(Map<BlockPos, BlockState> cells) {
        Map<BlockPos, Integer> label = new java.util.HashMap<>();
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockState s = e.getValue();
            if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || label.containsKey(e.getKey())) continue;
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(e.getKey()));
            label.put(e.getKey(), n);
            while (!todo.isEmpty()) {
                BlockPos p = todo.poll();
                for (Direction d : Direction.values()) {
                    BlockPos q = p.relative(d);
                    BlockState qs = cells.get(q);
                    if (qs == null || qs.isAir() || qs.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || label.containsKey(q)) continue;
                    label.put(q, n);
                    todo.add(q);
                }
            }
            n++;
        }
        return label;
    }

    /** The pairs of pieces that already meet along an edge somewhere (one cell there joins them, no corner step needed). */
    private static Set<Long> edgePairs(Map<BlockPos, Integer> label) {
        Set<Long> out = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> e : label.entrySet()) {
            BlockPos p = e.getKey();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 2) continue;
                        Integer l = label.get(p.offset(dx, dy, dz));
                        if (l != null && !l.equals(e.getValue())) out.add(pair(e.getValue(), l));
                    }
        }
        return out;
    }

    private static long pair(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    /**
     * The block a filled step takes: the commonest plain full block beside it, else the commonest of the nearest ones (a
     * step between two cogs takes the casing or hull around them); never a machine or container.
     */
    private static BlockState fillFor(Map<BlockPos, BlockState> cells, BlockPos at) {
        for (int r = 1; r <= 64; r++) {
            Map<BlockState, Integer> full = new java.util.TreeMap<>(java.util.Comparator.comparing(BlockState::toString));
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                BlockPos p = e.getKey();
                int d = Math.abs(p.getX() - at.getX()) + Math.abs(p.getY() - at.getY()) + Math.abs(p.getZ() - at.getZ());
                if (d != r) continue;
                BlockState s = e.getValue();
                if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock
                        || !s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, p)) continue;
                full.merge(s, 1, Integer::sum);
            }
            if (!full.isEmpty()) return java.util.Collections.max(full.entrySet(), Map.Entry.comparingByValue()).getKey();
        }
        return null;
    }

    // ---------- the body: mass, inertia, shape ----------

    private static final class Body {
        final List<Part> parts;
        final double m, cx, cy, cz;
        final int minX, minY, minZ, maxX, maxY, maxZ, lenX, lenZ, height;
        final Map<BlockPos, BlockState> blocks;
        /** The long horizontal axis: a hull's keel line. */
        final Direction.Axis keel;
        /** The highest it climbs, once the flight sums have worked it out (NaN for what doesn't fly). */
        double ceiling = Double.NaN;

        Body(List<Part> parts, double m, double cx, double cy, double cz, Map<BlockPos, BlockState> blocks, Direction.Axis drive) {
            this.parts = parts;
            this.m = m;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.blocks = blocks;
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (Part p : parts) {
                x0 = Math.min(x0, p.pos.getX());
                y0 = Math.min(y0, p.pos.getY());
                z0 = Math.min(z0, p.pos.getZ());
                x1 = Math.max(x1, p.pos.getX());
                y1 = Math.max(y1, p.pos.getY());
                z1 = Math.max(z1, p.pos.getZ());
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
            lenX = x1 - x0 + 1;
            lenZ = z1 - z0 + 1;
            height = y1 - y0 + 1;
            // What drives it along sets its keel line, even where pods or outriggers make it wider than it is long.
            keel = drive != null ? drive : lenX >= lenZ ? Direction.Axis.X : Direction.Axis.Z;
        }

        /** Moment of inertia about the vertical axis through the centre of mass (each block a unit cube). */
        double iYaw() {
            double i = 0;
            for (Part p : parts) i += p.mass * (1 / 6.0 + sq(p.cx() - cx) + sq(p.cz() - cz));
            return i;
        }

        /** About the keel line through the centre of mass: the axis a hull heels around. */
        double iRoll() {
            double i = 0;
            for (Part p : parts) i += p.mass * (1 / 6.0 + sq(lateral(p.cx(), p.cz())) + sq(p.cy() - cy));
            return i;
        }

        /** About the line across the keel through the centre of mass: the axis a hull trims (pitches) around. */
        double iPitch() {
            double i = 0;
            for (Part p : parts) i += p.mass * (1 / 6.0 + sq(along(p.cx(), p.cz())) + sq(p.cy() - cy));
            return i;
        }

        /** Offset across the keel from the centre of mass. */
        double lateral(double x, double z) {
            return keel == Direction.Axis.X ? z - cz : x - cx;
        }

        /** Offset along the keel from the centre of mass. */
        double along(double x, double z) {
            return keel == Direction.Axis.X ? x - cx : z - cz;
        }

        boolean solid(BlockPos p) {
            BlockState s = blocks.get(p);
            return s != null && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, p).isEmpty();
        }

        long count(String id) {
            return parts.stream().filter(p -> p.id.equals(id)).count();
        }
    }

    // ---------- floating: draft, heel ----------

    /** Something that floats: a hull block (Sable) or a hold cell (High Seas), with the lift it gives fully under. */
    private record Floater(double x, double y, double z, double lift, double wetVolume) {
    }

    private static List<Floater> floaters(Body b, boolean holds) {
        List<Floater> list = new ArrayList<>();
        for (Part p : b.parts) if (p.shell && p.vol > 0) list.add(new Floater(p.cx(), p.cy(), p.cz(), WATER * p.vol, 1));
        if (!holds) return list;
        List<BlockPos> cells = holdCells(b);
        for (BlockPos c : cells) list.add(new Floater(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, HOLD, 0));
        for (double[] f : holdFloors(b, cells)) list.add(new Floater(f[0], f[1], f[2], HOLD * f[3], 0));
        return list;
    }

    /**
     * The open part of each partial block (a slab floor, a stair) that opens onto a hold cell and nowhere else outside
     * the hold, as {x, y, z, volume}: the same air HighSeasHoldFloorsMixin lifts.
     */
    private static List<double[]> holdFloors(Body b, List<BlockPos> cells) {
        Set<BlockPos> hold = new HashSet<>(cells), seen = new HashSet<>();
        List<double[]> out = new ArrayList<>();
        for (BlockPos c : cells)
            for (Direction d : Direction.values()) {
                BlockPos p = c.relative(d);
                BlockState s = b.blocks.get(p);
                if (s == null || hold.contains(p) || !seen.add(p) || !b.solid(p)) continue;
                VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, p);
                if (Block.isShapeFullBlock(shape)) continue;
                double[] open = openPart(shape);
                boolean leaks = false;
                for (Direction e : Direction.values()) {
                    BlockPos n = p.relative(e);
                    if (!hold.contains(n) && !b.solid(n) && opens(shape, e)) leaks = true;
                }
                if (open[3] > 0 && opens(shape, d.getOpposite()) && !leaks)
                    out.add(new double[] {p.getX() + open[0], p.getY() + open[1], p.getZ() + open[2], open[3]});
                else seen.remove(p);
            }
        return out;
    }

    /** {centre x, y, z within the cell, volume} of the part of a cell a collision shape leaves open (8^3 samples). */
    private static double[] openPart(VoxelShape shape) {
        List<AABB> boxes = shape.toAabbs();
        double sx = 0, sy = 0, sz = 0;
        int open = 0;
        for (int i = 0; i < 512; i++) {
            double x = ((i >> 6) + 0.5) / 8, y = ((i >> 3 & 7) + 0.5) / 8, z = ((i & 7) + 0.5) / 8;
            if (boxes.stream().anyMatch(a -> a.contains(x, y, z))) continue;
            open++;
            sx += x;
            sy += y;
            sz += z;
        }
        return open == 0 ? new double[] {0.5, 0.5, 0.5, 0} : new double[] {sx / open, sy / open, sz / open, open / 512.0};
    }

    /** Whether any of a shape's face on side d is open. */
    private static boolean opens(VoxelShape shape, Direction d) {
        double w = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 - 1e-3 : 1e-3;
        for (int u = 0; u < 8; u++)
            for (int v = 0; v < 8; v++) {
                double a = (u + 0.5) / 8, c = (v + 0.5) / 8;
                Vec3 q = switch (d.getAxis()) {
                    case X -> new Vec3(w, a, c);
                    case Y -> new Vec3(a, w, c);
                    case Z -> new Vec3(a, c, w);
                };
                if (shape.toAabbs().stream().noneMatch(box -> box.contains(q))) return true;
            }
        return false;
    }

    /** High Seas' hold: an open cell with the hull somewhere west, east, north and south of it and a floor below. */
    private static List<BlockPos> holdCells(Body b) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = b.minX + 1; x < b.maxX; x++)
            for (int z = b.minZ + 1; z < b.maxZ; z++)
                for (int y = b.minY + 1; y <= b.maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (b.solid(p) || b.blocks.containsKey(p) && !b.blocks.get(p).getFluidState().isEmpty()) continue;
                    if (ray(b, p, Direction.WEST) && ray(b, p, Direction.EAST) && ray(b, p, Direction.NORTH) && ray(b, p, Direction.SOUTH)
                            && ray(b, p, Direction.DOWN)) cells.add(p);
                }
        return cells;
    }

    private static boolean ray(Body b, BlockPos from, Direction d) {
        BlockPos p = from.relative(d);
        while (p.getX() >= b.minX && p.getX() <= b.maxX && p.getY() >= b.minY && p.getY() <= b.maxY && p.getZ() >= b.minZ && p.getZ() <= b.maxZ) {
            if (b.solid(p)) return true;
            p = p.relative(d);
        }
        return false;
    }

    /** How much of a unit cube centred at height y sits below water at h. */
    private static double under(double y, double h) {
        return Math.max(0, Math.min(1, h - (y - 0.5)));
    }

    /**
     * Height of a floater's centre with the hull heeled by a (radians, about the keel line) and trimmed by p (about the
     * line across it, both through the centre of mass); positive p raises the end ahead along the keel (+x or +z).
     */
    private static double tilted(Body b, Floater f, double a, double p) {
        return b.cy + b.lateral(f.x, f.z) * Math.sin(a) + b.along(f.x, f.z) * Math.sin(p) + (f.y - b.cy) * Math.cos(a) * Math.cos(p);
    }

    /** Lift at waterline h, the floaters heeled by angle a (radians) about the keel line through the centre of mass. */
    private static double lift(Body b, List<Floater> fl, double h, double a) {
        return lift(b, fl, h, a, 0);
    }

    private static double lift(Body b, List<Floater> fl, double h, double a, double p) {
        double sum = 0;
        for (Floater f : fl) sum += f.lift * under(tilted(b, f, a, p), h);
        return sum;
    }

    /** The waterline the weight settles at, or NaN when everything under still can't hold it up. */
    private static double waterline(Body b, List<Floater> fl, double a) {
        return waterline(b, fl, a, 0);
    }

    private static double waterline(Body b, List<Floater> fl, double a, double p) {
        double reach = b.height + Math.max(b.lenX, b.lenZ) + 2;
        double lo = b.minY - reach, hi = b.maxY + reach, need = G * b.m;
        if (lift(b, fl, hi, a, p) < need) return Double.NaN;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (lift(b, fl, mid, a, p) < need) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Righting arm at heel a: positive pushes it back upright, negative tips it further. */
    private static double rightingArm(Body b, List<Floater> fl, double a) {
        double h = waterline(b, fl, a);
        if (Double.isNaN(h)) return Double.NaN;
        double torque = 0, cos = Math.cos(a), sin = Math.sin(a);
        for (Floater f : fl) {
            double l = b.lateral(f.x, f.z), v = f.y - b.cy;
            double lift = f.lift * under(b.cy + l * sin + v * cos, h);
            torque += lift * (l * cos - v * sin);
        }
        return -torque / (G * b.m);
    }

    private static double wetVolume(Body b, List<Floater> fl, double h) {
        double v = 0;
        for (Floater f : fl) v += f.wetVolume * under(f.y, h);
        return v;
    }

    /** Σ 1.7·V·r² about the vertical axis: water resisting a turn. */
    private static double wetTurnDrag(Body b, List<Floater> fl, double h) {
        double c = 0;
        for (Floater f : fl) c += WATER_DRAG * f.wetVolume * under(f.y, h) * (sq(f.x - b.cx) + sq(f.z - b.cz));
        return c;
    }

    // ---------- boats ----------

    private static void boat(Body b, Map<BlockPos, Float> rpm, StringBuilder out, List<String> faults, List<String> notes, boolean quiet) {
        List<Floater> fl = floaters(b, true);
        long holds = fl.stream().filter(f -> f.wetVolume == 0 && f.lift == HOLD).count();
        double h = waterline(b, fl, 0);
        if (Double.isNaN(h)) {
            faults.add(String.format(Locale.ROOT, "it sinks: fully under water its hull and hold lift at most %.0f against a "
                    + "weight of %.0f (each outer hull block lifts 10.5 x its volume, each open hold cell 16.5; air sealed "
                    + "inside solid walls lifts nothing, and buried blocks only weigh)", lift(b, fl, b.maxY + 50, 0), G * b.m));
            return;
        }
        // Freeboard to the top of the hold walls (masts and cabins above don't keep water out).
        double hullTop = b.maxY + 1;
        if (holds > 0) {
            hullTop = b.minY;
            for (BlockPos c : holdCells(b)) hullTop = Math.max(hullTop, c.getY() + 1);
        }
        double draft = h - b.minY, freeboard = hullTop - h;
        double immersedHold = 0;
        for (Floater f : fl) if (f.wetVolume == 0 && f.lift == HOLD) immersedHold += under(f.y, h);
        double wet = Math.min(1, immersedHold / 8);
        out.append(String.format(Locale.ROOT, "- Floats with %.1f blocks under water and %.1f of hull above (%d hold cells give "
                + "High Seas lift).\n", draft, freeboard, holds));
        if (freeboard < 0.6) faults.add(String.format(Locale.ROOT, "it rides with only %.1f blocks above the water: the deck "
                + "is awash and any wave or turn floods it; make the hull lighter or the hold bigger", freeboard));
        stability(b, fl, wet, holds > 0, out, faults);
        trim(b, fl, wet, holds > 0, hullTop, out, faults, notes);

        double vWet = wetVolume(b, fl, h);
        Thrusts t = waterThrust(b, rpm, h, faults, notes);
        if (t.list.isEmpty() && t.sailTarget <= 0 && t.windPanels == 0) {
            faults.add("nothing drives it: add a Boat Engine (with its tiller), Submarine Propellers under the waterline, "
                    + "or sails");
            return;
        }
        double force = t.forward();
        double v = 0;
        if (force > 0) {
            // Thrust = High Seas hull drag along the keel + Sable's damping + water on the wet blocks.
            double a = force / b.m, k = DAMP + WATER_DRAG * vWet / b.m;
            v = solveBoat(a, wet, k);
        }
        double sailV = t.sailTarget;
        if (t.windPanels > 0) {
            // Winds & Weather cloth pushes it along at up to about the wind's speed: 3 to 7 blocks/s at sea level, 5 typical.
            // Full push needs canvas on about 3.5% of its blocks.
            double capture = Math.min(1, t.windPanels / (0.035 * b.parts.size()));
            sailV = Math.max(sailV, 5 * (0.3 + 0.7 * Math.sqrt(capture)));
            if (capture < 1) notes.add(String.format(Locale.ROOT, "it carries %d sail cloth blocks; about %.0f would catch the "
                    + "full wind", t.windPanels, Math.ceil(0.035 * b.parts.size())));
        }
        double top = Math.max(v, sailV);
        if (force > 0) out.append(String.format(Locale.ROOT, "- Top speed under power about %.1f blocks/s (%s).\n", v, t.describe()));
        if (t.sailTarget > 0) out.append(String.format(Locale.ROOT, "- Under High Seas sails about %.1f blocks/s with the wind "
                + "behind (less across it, none into it).\n", t.sailTarget));
        if (t.windPanels > 0) out.append("- Winds & Weather sails push it at up to about the wind's speed (3 to 7 blocks/s at "
                + "sea level, about 5 typical)" + (b.count("aeronauticswinds:keel") > 0 ? "; its keel lets it sail across the wind"
                : "; with no keel it only runs downwind") + ".\n");
        judgeSpeed(top, BOAT_BAD, BOAT_SLOW, "boat", faults, notes);

        // Turning: the engine's side push at its propeller, or rudders, against the water and High Seas' yaw damping.
        double iy = b.iYaw(), damp = (0.6 * wet + DAMP) * iy + wetTurnDrag(b, fl, h);
        double q = 0;
        String how = null;
        for (Thrust th : t.list) {
            if (!th.what.equals("Boat Engine")) continue;
            double arm = Math.abs(b.along(th.at.getX() + 0.5, th.at.getZ() + 0.5)) + 0.4;
            q = Math.max(q, 0.45 * v * b.m * arm);
            how = "the Boat Engine's tiller";
        }
        double rudderQ = rudderTorque(b, rpm, Math.max(top, 1), faults);
        if (rudderQ > q) {
            q = rudderQ;
            how = "its rudder";
        }
        double diff = differential(b, t.list);
        if (diff > q) {
            q = diff;
            how = "running its propellers unevenly";
        }
        // Measured on a 15-long plank boat at full speed: 31°/s where the sums give 69, the hull sliding sideways through
        // the turn; so half.
        turn(q * TURN_SLIP, damp, how, faults, notes, out, "boat");
    }

    /** Solve a = (0.25 + 0.055 v) v wet + k v for v (High Seas keel drag plus linear drag). */
    private static double solveBoat(double a, double wet, double k) {
        double qa = 0.055 * wet, qb = 0.25 * wet + k;
        if (qa < 1e-9) return a / qb;
        return (-qb + Math.sqrt(qb * qb + 4 * qa * a)) / (2 * qa);
    }

    /** The heel test: tip it 10° and 25° and see which way the water turns it. */
    private static void stability(Body b, List<Floater> fl, double wet, boolean highSeas, StringBuilder out, List<String> faults) {
        double iRoll = b.iRoll();
        StringBuilder line = new StringBuilder("- Heeled over, the water turns it ");
        String bad = null;
        for (int deg : new int[] {10, 25}) {
            double a = Math.toRadians(deg), gz = rightingArm(b, fl, a);
            if (Double.isNaN(gz)) {
                bad = "heeled " + deg + "° it no longer floats";
                break;
            }
            // High Seas pushes a boat with a wet hold back upright (4 rad/s² at full tilt, taken here as growing with sin).
            double tip = -gz * G * b.m / iRoll, help = highSeas ? 4 * wet * Math.sin(a) : 0;
            line.append(deg == 10 ? "" : ", ").append(String.format(Locale.ROOT, "%s at %d° (righting arm %.2f blocks%s)",
                    gz >= 0 ? "back upright" : "further over", deg, gz, highSeas && gz < 0 ? ", High Seas' self-righting "
                            + (help >= tip ? "holds it" : "can't hold it") : ""));
            if (gz < 0 && help < tip && bad == null)
                bad = String.format(Locale.ROOT, "it capsizes: heeled %d° the water pushes it further over (centre of mass %.1f "
                        + "blocks above the keel is too high for a hull %d wide); widen the hull, or move heavy blocks down",
                        deg, b.cy - b.minY, b.keel == Direction.Axis.X ? b.lenZ : b.lenX);
        }
        out.append(line).append(".\n");
        if (bad != null) faults.add(bad);
    }

    /**
     * How it sits at rest: the trim (fore and aft) and list (side to side) where the lift of the wet blocks and hold cells
     * lines up with the centre of mass, with High Seas' self-righting (4 x wet rad/s² at full tilt) pulling toward level.
     */
    private static void trim(Body b, List<Floater> fl, double wet, boolean highSeas, double hullTop, StringBuilder out,
            List<String> faults, List<String> notes) {
        double help = highSeas ? 4 * wet : 0;
        double pitch = rest(b, fl, true, help * b.iPitch()), list = rest(b, fl, false, help * b.iRoll());
        Direction bow = bow(b);
        // The ends of the keel and the sides, named from the bow when it has one.
        Direction plus = b.keel == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction side = b.keel == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        String ahead = bow == null ? plus.getName() + " end" : "bow", behind = bow == null ? plus.getOpposite().getName() + " end" : "stern";
        double up = bow == null || bow == plus ? 1 : -1; // + when a positive pitch raises the end named first
        String plusSide = bow == null ? side.getName() + " side" : (bow.getClockWise() == side ? "starboard" : "port");
        String minusSide = bow == null ? side.getOpposite().getName() + " side" : plusSide.equals("starboard") ? "port" : "starboard";
        // With the model's own axis, so a fix in blocks ("slide it toward the stern") says which way that is.
        ahead += axis(bow == null ? plus : bow);
        behind += axis(bow == null ? plus.getOpposite() : bow.getOpposite());
        plusSide += axis(side);
        minusSide += axis(side.getOpposite());
        if (Double.isNaN(pitch) || Math.abs(pitch) > Math.toRadians(40)) {
            faults.add(String.format(Locale.ROOT, "it doesn't float level fore and aft: its weight sits so far toward one end "
                    + "that it tips onto its %s and stands on end; move heavy blocks toward the middle, or give that end more hull",
                    centreOfLift(b, fl)[0] * up > 0 ? behind : ahead));
            return;
        }
        if (Double.isNaN(list) || Math.abs(list) > Math.toRadians(40)) {
            faults.add("it doesn't float level side to side: its weight is so far off the middle that it rolls onto its side; "
                    + "balance heavy blocks across it");
            return;
        }
        double pd = Math.toDegrees(pitch) * up, ld = Math.toDegrees(list);
        String trimText = Math.abs(pd) < 0.25 ? "level fore and aft" : String.format(Locale.ROOT, "%s %.1f° down", pd > 0 ? behind : ahead, Math.abs(pd));
        String listText = Math.abs(ld) < 0.25 ? "level side to side" : String.format(Locale.ROOT, "%.1f° down to %s", Math.abs(ld), ld > 0 ? minusSide : plusSide);
        out.append("- At rest with nobody aboard it sits ").append(trimText).append(" and ").append(listText);
        // Weight to move to level it: lift centre minus centre of mass along each axis, times mass.
        double[] off = centreOfLift(b, fl);
        List<String> fix = new ArrayList<>();
        if (Math.abs(pd) >= 0.25) fix.add(String.format(Locale.ROOT, "%.0f mass x blocks toward the %s", Math.abs(off[0]) * b.m, pd > 0 ? ahead : behind));
        if (Math.abs(ld) >= 0.25) fix.add(String.format(Locale.ROOT, "%.0f toward %s", Math.abs(off[1]) * b.m, ld > 0 ? plusSide : minusSide));
        if (!fix.isEmpty()) out.append(" (its weight and lift are off by ").append(String.join(", ", fix)).append("; level it "
                + "with the hull's own shape and layout: a fuller, wider hull under the heavy end, or the engine room, cargo and "
                + "cabin moved toward the high end; a plank weighs 0.5)");
        out.append(".\n");
        // The low end's freeboard at that trim.
        double h = waterline(b, fl, list, pitch);
        if (!Double.isNaN(h) && Math.abs(pd) >= 0.25) {
            double lo = Double.MAX_VALUE;
            for (Part p : b.parts) {
                double a = b.along(p.cx(), p.cz());
                lo = Math.min(lo, b.cy + a * Math.sin(pitch) + (hullTop - b.cy) * Math.cos(pitch) - h);
            }
            if (lo < 0.6) faults.add(String.format(Locale.ROOT, "trimmed %.1f° %s down at rest, its %s sits only %.1f blocks above "
                    + "the water: it ships water there; move weight toward the %s", Math.abs(pd), pd > 0 ? behind : ahead,
                    pd > 0 ? behind : ahead, Math.max(0, lo), pd > 0 ? ahead : behind));
        }
        if (Math.abs(pd) >= TRIM_BAD) faults.add(String.format(Locale.ROOT, "it sits %.1f° %s down at rest: move weight toward "
                + "the %s until it sits level", Math.abs(pd), pd > 0 ? behind : ahead, pd > 0 ? ahead : behind));
        else if (Math.abs(pd) >= TRIM_NOTE) notes.add(String.format(Locale.ROOT, "it sits %.1f° %s down at rest, which shows; a "
                + "level hull looks and rides better", Math.abs(pd), pd > 0 ? behind : ahead));
        if (Math.abs(ld) >= TRIM_BAD) faults.add(String.format(Locale.ROOT, "it lists %.1f° to %s at rest: balance its weight "
                + "across it", Math.abs(ld), ld > 0 ? minusSide : plusSide));
        else if (Math.abs(ld) >= TRIM_NOTE) notes.add(String.format(Locale.ROOT, "it lists %.1f° to %s at rest, which shows",
                Math.abs(ld), ld > 0 ? minusSide : plusSide));
    }

    /** Where the hull's lift centres at level against the centre of mass: {along the keel, across it}, in blocks. */
    private static double[] centreOfLift(Body b, List<Floater> fl) {
        double h = waterline(b, fl, 0), sum = 0, a = 0, l = 0;
        for (Floater f : fl) {
            double w = f.lift * under(f.y, h);
            sum += w;
            a += w * b.along(f.x, f.z);
            l += w * b.lateral(f.x, f.z);
        }
        return sum <= 0 ? new double[2] : new double[] {a / sum, l / sum};
    }

    /**
     * The trim (pitch true) or heel angle, radians, at which the water's turning push on the hull balances High Seas'
     * pull toward level ({@code righting} x sin of the angle); positive raises the + end of the keel or the + side. NaN
     * when nothing within 45° balances it.
     */
    private static double rest(Body b, List<Floater> fl, boolean pitch, double righting) {
        double step = Math.toRadians(0.25), max = Math.toRadians(45);
        double m0 = moment(b, fl, 0, pitch, righting);
        if (Double.isNaN(m0)) return Double.NaN;
        if (Math.abs(m0) < 1e-9) return 0;
        double dir = Math.signum(m0), prev = 0;
        for (double x = step; x <= max + 1e-9; x += step) {
            double m = moment(b, fl, dir * x, pitch, righting);
            if (Double.isNaN(m)) return Double.NaN;
            if (Math.signum(m) != dir) {
                double lo = prev, hi = x;
                for (int i = 0; i < 30; i++) {
                    double mid = (lo + hi) / 2, mm = moment(b, fl, dir * mid, pitch, righting);
                    if (Double.isNaN(mm) || Math.signum(mm) != dir) hi = mid;
                    else lo = mid;
                }
                return dir * (lo + hi) / 2;
            }
            prev = x;
        }
        return Double.NaN;
    }

    /** The turning push raising the + end (pitch) or + side at angle x, less High Seas' self-righting. */
    private static double moment(Body b, List<Floater> fl, double x, boolean pitch, double righting) {
        double a = pitch ? 0 : x, p = pitch ? x : 0, h = waterline(b, fl, a, p);
        if (Double.isNaN(h)) return Double.NaN;
        double torque = 0, cos = Math.cos(x), sin = Math.sin(x);
        for (Floater f : fl) {
            double r = pitch ? b.along(f.x, f.z) : b.lateral(f.x, f.z), v = f.y - b.cy;
            torque += f.lift * under(tilted(b, f, a, p), h) * (r * cos - v * sin);
        }
        return torque - righting * sin;
    }

    /** The way it goes ahead: its Boat Engine's facing, else its propellers' or thrusters'; null when nothing says. */
    private static Direction bow(Body b) {
        for (String id : new String[] {"create_high_seas:boat_engine", "create_submarine:submarine_propeller", "create_submarine:water_thruster"})
            for (Part p : b.parts) {
                Direction f = facing(p.state);
                if (p.id.equals(id) && f != null && f.getAxis() == b.keel) return f;
            }
        return null;
    }

    // ---------- submarines ----------

    private static void sub(Body b, Map<BlockPos, Float> rpm, StringBuilder out, List<String> faults, List<String> notes) {
        List<Floater> fl = floaters(b, false);
        double buoyancy = lift(b, fl, b.maxY + 50, 0), wNet = G * b.m - buoyancy;
        long tanks = b.count("create_submarine:ballast_tank");
        out.append(String.format(Locale.ROOT, "- Fully under water it weighs %.0f and its hull lifts %.0f (net %s %.0f); %d "
                + "Ballast Tank%s.\n", G * b.m, buoyancy, wNet > 0 ? "down" : "up", Math.abs(wNet), tanks, tanks == 1 ? "" : "s"));
        if (tanks == 0) faults.add("no Ballast Tanks: a submarine dives and surfaces only with them");
        else {
            // A tank pushes the sub toward a target climb or sink rate with 240 force per (b/s) of error, at most 2 of it.
            if (480 * tanks <= wNet)
                faults.add(String.format(Locale.ROOT, "it can't surface: %d tank%s push up at most %d against a net weight of %.0f; "
                        + "add tanks (each about 480) or lighten the hull", tanks, tanks == 1 ? "" : "s", 480 * tanks, wNet));
            if (wNet < 0 && 48 * tanks <= -wNet)
                faults.add(String.format(Locale.ROOT, "it can't dive from rest: it floats with %.0f to spare and full tanks pull "
                        + "down only %d from a standstill; add tanks or weight", -wNet, 48 * tanks));
            if (wNet > 480 * tanks * 0.5) notes.add("it is heavy for its tanks, so it drops fast with full tanks (the tanks can't "
                    + "slow a fall beyond 2 blocks/s)");
        }
        if (b.parts.stream().noneMatch(p -> p.id.equals("create:mechanical_pump") || p.id.equals("create_submarine:ballast_vent")
                || p.id.equals("create_submarine:pump_controller")))
            faults.add("nothing fills or empties the tanks: they need a Mechanical Pump (or a Ballast Vent) piped to them; a pump "
                    + "pushes only the way its arrow points, so use one each way or turn it with a Wrench");
        if (b.count("create_submarine:oxygene_diffuser") == 0)
            notes.add("no Oxygen Diffuser, so the crew has no air once the hatch shuts");
        depth(b, out, notes);

        double vWet = 0;
        for (Floater f : fl) vWet += f.wetVolume;
        Thrusts t = waterThrust(b, rpm, Double.POSITIVE_INFINITY, faults, notes);
        if (t.list.isEmpty()) {
            faults.add("nothing drives it under water: add Submarine Propellers (best as a 2x2 or 3x3 square), a Water "
                    + "Thruster, or a Boat Engine");
            return;
        }
        // Under water with tanks wet: 0.7 x mass of ballast drag on top of Sable's 0.09 and the water on each block.
        double v = t.forward() / ((0.7 + DAMP) * b.m + WATER_DRAG * vWet);
        out.append(String.format(Locale.ROOT, "- Top speed under water about %.1f blocks/s (%s).\n", v, t.describe()));
        judgeSpeed(v, SUB_BAD, SUB_SLOW, "submarine", faults, notes);
        double iy = b.iYaw(), damp = DAMP * iy + 0.7 * iy + wetTurnDrag(b, fl, Double.POSITIVE_INFINITY);
        double rq = rudderTorque(b, rpm, Math.max(v, 1), faults), dq = differential(b, t.list);
        double q = Math.max(rq, dq);
        String how = q == 0 ? null : rq >= dq ? "its rudder" : "running its propellers unevenly";
        for (Thrust th : t.list) {
            if (!th.what.equals("Boat Engine")) continue;
            double eq = 0.45 * v * b.m * (Math.abs(b.along(th.at.getX() + 0.5, th.at.getZ() + 0.5)) + 0.4);
            if (eq > q) {
                q = eq;
                how = "the Boat Engine's tiller";
            }
        }
        turn(q * TURN_SLIP, damp, how, faults, notes, out, "submarine");
    }

    /** Deep Seas' hull rating: the weakest outer block's own depth, before the hull's shape scales it (0.5x to 2x). */
    private static void depth(Body b, StringBuilder out, List<String> notes) {
        String weakest = null;
        int worst = Integer.MAX_VALUE;
        try {
            Class<?> cfg = Class.forName("com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig");
            var getFor = cfg.getMethod("getFor", BlockState.class);
            Set<BlockPos> outer = outerHull(b);
            for (Part p : b.parts) {
                if (!outer.contains(p.pos)) continue;
                Object opt = getFor.invoke(null, p.state);
                if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) continue;
                Object prop = o.get();
                int d = (int) prop.getClass().getMethod("maxWaterDepth").invoke(prop);
                if (d < worst) {
                    worst = d;
                    weakest = p.state.getBlock().getName().getString();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return;
        }
        if (weakest != null) out.append(String.format(Locale.ROOT, "- Weakest hull block: %s, rated %d m (a thick wall or "
                + "short panel doubles it, a wide thin one halves it).\n", weakest, worst));
    }

    /** The blocks the sea touches: those next to the open air around the hull, reached from outside its bounds. */
    private static Set<BlockPos> outerHull(Body b) {
        Set<BlockPos> outside = new HashSet<>(), hull = new HashSet<>();
        BlockPos start = new BlockPos(b.minX - 1, b.minY - 1, b.minZ - 1);
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        outside.add(start);
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < b.minX - 1 || n.getX() > b.maxX + 1 || n.getY() < b.minY - 1 || n.getY() > b.maxY + 1
                        || n.getZ() < b.minZ - 1 || n.getZ() > b.maxZ + 1) continue;
                if (b.solid(n)) {
                    hull.add(n);
                    continue;
                }
                if (outside.add(n)) todo.add(n);
            }
        }
        return hull;
    }

    // ---------- water thrust ----------

    private static final class Thrusts {
        final List<Thrust> list = new ArrayList<>();
        double sailTarget;
        int windPanels;

        /** The most it can push along one horizontal direction. */
        double forward() {
            double best = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                double sum = 0;
                for (Thrust t : list) if (t.dir == d || t.reversible && t.dir == d.getOpposite()) sum += t.force;
                best = Math.max(best, sum);
            }
            return best;
        }

        String describe() {
            java.util.Map<String, double[]> by = new java.util.LinkedHashMap<>();
            for (Thrust t : list) {
                double[] a = by.computeIfAbsent(t.what, k -> new double[2]);
                a[0]++;
                a[1] += t.force;
            }
            List<String> s = new ArrayList<>();
            by.forEach((k, a) -> s.add(String.format(Locale.ROOT, "%d %s, %.0f force", (int) a[0], k, a[1])));
            return String.join("; ", s);
        }
    }

    /** Forces under water at waterline h (infinite for a submerged sub). */
    private static Thrusts waterThrust(Body b, Map<BlockPos, Float> rpm, double h, List<String> faults, List<String> notes) {
        Thrusts t = new Thrusts();
        Set<BlockPos> grouped = new HashSet<>();
        int sails = 0;
        for (Part p : b.parts) {
            switch (p.id) {
                case "create_high_seas:boat_engine" -> {
                    Direction f = facing(p.state);
                    double propY = p.cy() - 1.156, immersion = Math.max(0, Math.min(1, (h - propY + 1) / 1.5));
                    if (immersion <= 0) faults.add("the Boat Engine at " + xyz(p.pos) + " sits too high: its propeller (a block "
                            + "below it) must be under water to push");
                    if (immersion > 0 && f != null && f.getAxis().isHorizontal())
                        t.list.add(new Thrust(p.pos, f, 36 * b.m * immersion, Double.MAX_VALUE, "Boat Engine", true));
                }
                case "create_submarine:submarine_propeller" -> {
                    if (grouped.contains(p.pos)) continue;
                    Direction f = facing(p.state);
                    if (f == null || p.cy() > h) {
                        if (p.cy() > h) notes.add("the Submarine Propeller at " + xyz(p.pos) + " is above the water line and gives nothing there");
                        continue;
                    }
                    int size = square(b, p, grouped);
                    float r = Math.abs(rpm.getOrDefault(p.pos, 0f));
                    if (r == 0) {
                        faults.add("the Submarine Propeller at " + xyz(p.pos) + " isn't turning: nothing drives it");
                        continue;
                    }
                    double force = 3 * r * (size == 1 ? 1 : size * size * size * size);
                    t.list.add(new Thrust(p.pos, f, force, Double.MAX_VALUE,
                            size == 1 ? "Submarine Propeller" : size + "x" + size + " Submarine Propeller square", true));
                }
                case "create_submarine:water_thruster" -> {
                    Direction f = facing(p.state);
                    if (f != null) t.list.add(new Thrust(p.pos, f, 2400, Double.MAX_VALUE, "Water Thruster (at a full bucket a second)", false));
                }
                default -> {
                    if (p.state.is(HIGH_SEAS_SAILS)) sails++;
                    if (p.id.equals("aeronauticswinds:windcatcher_canvas") || p.id.contains("_sail_cloth")) t.windPanels++;
                }
            }
        }
        if (sails > 0) {
            // High Seas sails steer the speed toward a target: 4·√(hold length + 3)·rig, at most 14 (wind behind, sea breeze).
            double len = (b.keel == Direction.Axis.X ? b.lenX : b.lenZ) - 2 + 3;
            double rig = Math.min(1, Math.sqrt(sails / (0.2 * Math.pow(b.m, 2 / 3.0))));
            t.sailTarget = Math.min(14, 4 * Math.sqrt(Math.max(1, len)) * rig * 0.8);
        }
        return t;
    }

    /** The side of an isolated 2x2 or 3x3 square of Submarine Propellers this one belongs to (1 when alone). */
    private static int square(Body b, Part p, Set<BlockPos> grouped) {
        Direction f = facing(p.state);
        if (f == null) return 1;
        Direction[] plane = f.getAxis() == Direction.Axis.X ? new Direction[] {Direction.UP, Direction.SOUTH}
                : f.getAxis() == Direction.Axis.Z ? new Direction[] {Direction.UP, Direction.EAST} : new Direction[] {Direction.EAST, Direction.SOUTH};
        for (int size = 3; size >= 2; size--) {
            for (int a = 0; a < size; a++)
                for (int c = 0; c < size; c++) {
                    BlockPos corner = p.pos.relative(plane[0], -a).relative(plane[1], -c);
                    if (!isolated(b, corner, plane, p.state, size)) continue;
                    for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) grouped.add(corner.relative(plane[0], i).relative(plane[1], j));
                    return size;
                }
        }
        return 1;
    }

    private static boolean isolated(Body b, BlockPos corner, Direction[] plane, BlockState ref, int size) {
        for (int i = -1; i <= size; i++)
            for (int j = -1; j <= size; j++) {
                BlockState s = b.blocks.get(corner.relative(plane[0], i).relative(plane[1], j));
                boolean inside = i >= 0 && i < size && j >= 0 && j < size;
                boolean prop = s != null && s.getBlock() == ref.getBlock() && s.equals(ref);
                if (inside != prop) return false;
            }
        return true;
    }

    /** Winds & Weather rudders: side force |v|·sin(30°)·M·(0.18c + 0.5·max(0.5,|v|)) at a lever of at least 3. */
    private static double rudderTorque(Body b, Map<BlockPos, Float> rpm, double v, List<String> faults) {
        double best = 0;
        boolean wheel = b.count("simulated:steering_wheel") > 0;
        for (Part p : b.parts) {
            if (!p.id.equals("aeronauticswinds:rudder")) continue;
            // Its blade angle follows a Steering Wheel on board, or a shaft turning it; with neither it stays straight.
            if (!wheel && rpm.getOrDefault(p.pos, 0f) == 0) {
                faults.add("the Rudder at " + xyz(p.pos) + " has nothing setting its angle, so it stays straight: put a "
                        + "Steering Wheel on board (the rudder follows it) or turn the rudder with a shaft");
                continue;
            }
            double c = Math.max(0.85, Math.min(6, Math.sqrt(b.m / 3)));
            double force = v * Math.sin(Math.toRadians(30)) * b.m * (0.18 * c + 0.5 * Math.max(0.5, v));
            double lever = Math.max(3, Math.abs(b.along(p.cx(), p.cz())) - 0.62);
            best = Math.max(best, Math.min(25 * b.m, force) * lever);
        }
        return Math.min(best, 80 * b.m);
    }

    /** Turning by pushing harder on one side: the larger side's thrust times its distance off the centre line. */
    private static double differential(Body b, List<Thrust> list) {
        double left = 0, right = 0;
        for (Thrust t : list) {
            double lat = b.lateral(t.at.getX() + 0.5, t.at.getZ() + 0.5);
            if (Math.abs(lat) < 0.75 || t.dir.getAxis() != b.keel) continue;
            if (lat > 0) right += t.force * lat;
            else left -= t.force * lat;
        }
        return left > 0 && right > 0 ? Math.max(left, right) : 0;
    }

    // ---------- aircraft ----------

    private static void air(Body b, Map<BlockPos, Float> rpm, StringBuilder out, List<String> faults, List<String> notes) {
        // Balloons: each burner fills the envelope above it.
        Map<BlockPos, int[]> balloons = new java.util.LinkedHashMap<>(); // seed -> volume, burners
        Map<BlockPos, double[]> centres = new java.util.HashMap<>(); // seed -> centre of its air
        double balloonY = 0;
        for (Part p : b.parts) {
            boolean burner = p.id.equals("aeronautics:adjustable_burner"), vent = p.id.equals("aeronautics:steam_vent");
            if (!burner && !vent) continue;
            BlockPos up = p.pos.above();
            while (up.getY() <= b.maxY + 1 && !b.solid(up)) up = up.above();
            BlockState roof = b.blocks.get(up);
            if (roof == null || !airtight(roof)) {
                faults.add((burner ? "the Hot Air Burner" : "the Steam Vent") + " at " + xyz(p.pos) + " has "
                        + (roof == null ? "open sky" : roof.getBlock().getName().getString()) + " straight above it, not "
                        + "envelope or wool: the first solid block above a burner must be the balloon's roof, or it lifts nothing");
                continue;
            }
            BlockPos seed = up.below();
            BlockPos key = null;
            for (BlockPos k : balloons.keySet()) if (balloon(b, k).contains(seed)) key = k;
            if (key == null) {
                Set<BlockPos> cells = balloon(b, seed);
                if (cells.isEmpty()) {
                    faults.add("the balloon over " + xyz(p.pos) + " leaks: its air escapes sideways or upward through a gap in the "
                            + "envelope (a balloon is an upside-down bucket: closed top and sides, open bottom)");
                    continue;
                }
                key = seed;
                balloons.put(key, new int[] {cells.size(), 0, 0});
                double xs = 0, ys = 0, zs = 0;
                for (BlockPos c : cells) {
                    xs += c.getX() + 0.5;
                    ys += c.getY() + 0.5;
                    zs += c.getZ() + 0.5;
                }
                centres.put(key, new double[] {xs / cells.size(), ys / cells.size(), zs / cells.size()});
                balloonY = Math.max(balloonY, ys / cells.size() - b.cy);
            }
            balloons.get(key)[1] += burner ? 500 : 5000;
        }
        double filled = 0;
        for (int[] v : balloons.values()) filled += Math.min(v[0], v[1]);
        long lev = b.parts.stream().filter(p -> p.state.is(LEVITITE)).count();
        long endStone = b.count("minecraft:end_stone");
        long airtight = b.parts.stream().filter(p -> airtight(p.state)).count();
        double levLift = Math.min(110 * lev + 22 * endStone, G * b.m);

        // Thrust from propellers and thrusters, split into up and sideways.
        List<Thrust> all = new ArrayList<>();
        double stress = 0;
        for (Part p : b.parts) {
            Thrust t = airThrust(b, p, rpm, faults);
            if (t == null) continue;
            all.add(t);
            if (isPropeller(p.id)) stress += propellerStress(b, p, rpm);
        }
        all = againstEachOther(b, all, rpm, faults);
        double up = 0;
        for (Thrust t : all) {
            if (t.dir == Direction.UP || t.reversible && t.dir == Direction.DOWN) up += t.force;
            else if (t.dir == Direction.DOWN && !t.reversible && t.airflow < Double.MAX_VALUE) up -= t.force;
        }
        List<Thrust> side = all.stream().filter(t -> t.dir.getAxis().isHorizontal()).toList();

        double p0 = pressure(TAKEOFF_Y);
        // Top speed first, since wings lift in proportion to it. Propellers lose push as the ship nears their airflow
        // speed; thrusters don't.
        Wings wings = wings(b);
        double drag = DAMP * b.m + ENVELOPE_DRAG * p0 * airtight + 0.55 * lev;
        Direction best = null;
        double v = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            double props = 0, propsOverAir = 0, thrusters = 0;
            for (Thrust t : side) {
                // A propeller turning the wrong way pushes back at full strength (its air meets the ship head on).
                if (t.dir == d.getOpposite() && !t.reversible && t.airflow < Double.MAX_VALUE) props -= t.force * p0;
                if (!(t.dir == d || t.reversible && t.dir == d.getOpposite())) continue;
                if (t.airflow < Double.MAX_VALUE) {
                    props += t.force * p0;
                    propsOverAir += t.force * p0 / t.airflow;
                } else thrusters += t.force;
            }
            double u = terminal(props + thrusters, drag + wings.drag(d.getAxis(), p0), 16.5 * lev, propsOverAir);
            // Reversible propellers push as hard either way; it flies the way they face, not tail first.
            if (u > v + 1e-9 || best != null && u > v - 1e-9 && facingPush(side, d) > facingPush(side, best)) {
                v = u;
                best = d;
            }
        }
        double wingLift = wings.up * p0 * v;
        double other = 16.5 * filled * pressure(TAKEOFF_Y + balloonY) + levLift + up * p0;
        double lift0 = other + Math.max(0, wingLift);
        out.append(String.format(Locale.ROOT, "- Lift at y %d: %.0f against a weight of %.0f (", (int) TAKEOFF_Y, lift0, G * b.m));
        List<String> src = new ArrayList<>();
        if (filled > 0) src.add(String.format(Locale.ROOT, "%d balloon%s, %.0f m3 filled", balloons.size(), balloons.size() == 1 ? "" : "s", filled));
        if (lev + endStone > 0) src.add(lev + endStone + " Levitite/End Stone, hover only");
        if (up > 0) src.add(String.format(Locale.ROOT, "%.0f upward thrust", up));
        if (wingLift > 0) src.add(String.format(Locale.ROOT, "%s, %.0f at its top speed of %.1f blocks/s", wings.describe(), wingLift, v));
        out.append(src.isEmpty() ? "nothing lifts it" : String.join("; ", src)).append(").\n");
        if (wings.count > 0) wingLift(b, wings, v, p0, other, best, side, rpm, out, faults, notes);
        for (int[] bv : balloons.values())
            if (bv[0] > bv[1]) notes.add(String.format(Locale.ROOT, "a balloon holds %d m3 but its burners fill only %d (500 each at "
                    + "full); another burner would add lift", bv[0], bv[1]));
            else if (bv[1] > bv[0] * 1.6) notes.add(String.format(Locale.ROOT, "a balloon holds only %d m3 for %d m3 of burner: a "
                    + "bigger envelope would lift more (each m3 holds up 1.5 mass)", bv[0], bv[1]));
        if (lift0 < G * b.m && wings.up > 0) {
            // Wings: say what it takes in wings or speed, the two things the builder can change.
            double each = 0.475 * p0 * v, needSpeed = (G * b.m - other) / (wings.up * p0);
            faults.add(String.format(Locale.ROOT, "it can't take off: at its top speed of %.1f blocks/s its wings lift %.0f%s, "
                    + "short of its weight %.0f by %.0f. Wings lift only while it moves (0.475 x speed x air pressure each, the "
                    + "way they face), so it needs %s about %.1f blocks/s (more forward thrust: more or faster propellers, "
                    + "less drag)%s, or %.0f mass less", v, wingLift, other > 0 ? String.format(Locale.ROOT, " and the rest %.0f", other)
                    : "", G * b.m, G * b.m - lift0, v > 0 ? String.format(Locale.ROOT, "about %.0f more wings facing up (each lifts "
                    + "%.1f at that speed, though each also adds its own mass and drag), or", Math.ceil((G * b.m - lift0) / each), each)
                    : "forward thrust first; then", needSpeed, wings.down > 0 ? "; and turn its downward-facing wings up" : "",
                    (G * b.m - lift0) / G));
            propPlaneAdvice(b, wings, side, best, rpm, needSpeed, faults);
        } else if (lift0 < G * b.m) {
            faults.add(String.format(Locale.ROOT, "it can't take off: lift at y %d is %.0f, short of its weight %.0f by %.0f "
                    + "(about %.0f m3 more balloon, or %.0f mass less)", (int) TAKEOFF_Y, lift0, G * b.m, G * b.m - lift0,
                    (G * b.m - lift0) / (16.5 * p0), (G * b.m - lift0) / G));
        } else if (filled > 0 && lev == 0 && up == 0) {
            // Ceiling: where the balloon's lift falls to the weight (air thins as exp(-0.004·(y-63))).
            double need = (G * b.m - levLift) / (16.5 * filled);
            double ceiling = Math.min(280, 63 - Math.log(need) / 0.004 - balloonY);
            b.ceiling = ceiling;
            out.append(String.format(Locale.ROOT, "- Ceiling about y %.0f (the air thins as it climbs).\n", ceiling));
            if (ceiling < CEILING_BAD) faults.add(String.format(Locale.ROOT, "its ceiling is only about y %.0f: it barely clears "
                    + "the ground and hits hills; it needs more balloon for its weight", ceiling));
        } else if (filled == 0 && wingLift > 0 && lev == 0) {
            // Wings and upward thrust both weaken as the air thins; the speed holds (the drag thins too).
            double need = G * b.m / ((wings.up * v + up) * pressure(63));
            double ceiling = Math.min(280, 63 - Math.log(need) / 0.004);
            b.ceiling = ceiling;
            out.append(String.format(Locale.ROOT, "- Ceiling about y %.0f at full speed (the air thins as it climbs).\n", ceiling));
            if (ceiling < CEILING_BAD) faults.add(String.format(Locale.ROOT, "its ceiling is only about y %.0f: it barely clears "
                    + "the ground and hits hills; it needs more wings or more speed for its weight", ceiling));
        }
        double climbDrag = DAMP * b.m + ENVELOPE_DRAG * p0 * airtight + 1.1 * lev + wings.drag(Direction.Axis.Y, p0);
        // A winged craft's climb comes from flying it (below); this is the straight-up climb of balloons and rotors.
        if (lift0 >= G * b.m && (filled > 0 || up > 0 || lev + endStone > 0)) {
            double climb = terminal(lift0 - G * b.m, climbDrag, 22 * lev, 0);
            if (filled > 0 || up > 0) out.append(String.format(Locale.ROOT, "- Climbs at about %.1f blocks/s at y %d with everything at full; turning the "
                    + "burners down lets it sink.\n", climb, (int) TAKEOFF_Y));
            if (lev + endStone > 0 && filled == 0 && up == 0)
                faults.add("Levitite only cancels weight, it never climbs: it needs a balloon or upward propellers to rise");
            else if (climb < CLIMB_BAD) faults.add(String.format(Locale.ROOT, "it climbs at only %.1f blocks/s: too weak to get "
                    + "over terrain; more lift or less weight", climb));
            else if (climb < CLIMB_SLOW) notes.add(String.format(Locale.ROOT, "it climbs slowly (%.1f blocks/s)", climb));
        }

        if (side.isEmpty()) {
            faults.add("nothing pushes it along: it can only drift with the wind. Add propellers driven by an on-board engine, "
                    + "or thrusters");
        } else {
            if (lev > 0 && v < AIR_SLOW) {
                double push = 0;
                for (Thrust t : side) if (best != null && (t.dir == best || t.reversible && t.dir == best.getOpposite()))
                    push += t.airflow < Double.MAX_VALUE ? t.force * p0 : t.force;
                notes.add(String.format(Locale.ROOT, "its %d Levitite brake slow movement hard (16.5 per block at a crawl, fading "
                        + "above 2 blocks/s): it takes more than about %.0f of push to break free, and it has %.0f", lev,
                        17.3 * lev + drag * Math.sqrt(3), push));
            }
            out.append(String.format(Locale.ROOT, "- Top speed about %.1f blocks/s at y %d (%s; drag from %s).\n", v,
                    (int) TAKEOFF_Y, describe(side), wings.list.isEmpty() ? airtight + " envelope blocks and its mass"
                    : wings.count + " wings and its mass"));
            judgeSpeed(v, AIR_BAD, AIR_SLOW, "aircraft", faults, notes);
            if (filled > 0 && wings.count == 0 && lev == 0 && v < AIRSHIP_GOOD)
                airshipAdvice(b, v, drag, filled, balloonY, p0, faults, notes);
            double fastest = 0;
            for (Thrust t : side) if (t.airflow < Double.MAX_VALUE) fastest = Math.max(fastest, t.airflow);
            if (fastest > 0 && fastest < AIR_SLOW * 1.5 && v < AIR_SLOW)
                notes.add(String.format(Locale.ROOT, "at these speeds its propellers push air at only %.1f blocks/s, so they can "
                        + "never move it faster than that: speed them up toward 256 RPM (from a Portable Engine's 32 RPM: "
                        + Drives.change(32, 256) + ") or use bigger propellers", fastest));
            // Turning: uneven thrust, vectoring thrusters or tilting propellers, against Sable's damping and the envelope.
            double q = 0;
            String how = null;
            double diff = differentialAir(b, side, best);
            if (diff > 0) {
                q = diff;
                how = "running its propellers or thrusters unevenly";
            }
            // A propeller pushing sideways near the bow or stern swings the nose round (reversible ones either way).
            for (Thrust t : side) {
                if (best == null || t.dir.getAxis() == best.getAxis()) continue;
                double arm = best.getAxis() == Direction.Axis.X ? t.at.getX() + 0.5 - b.cx : t.at.getZ() + 0.5 - b.cz;
                double tq = t.force * p0 * Math.abs(arm);
                if (Math.abs(arm) >= 1.5 && tq > q) {
                    q = tq;
                    how = "its sideways " + t.what + (t.reversible ? "" : " (one way only)");
                }
            }
            for (Thrust t : all) {
                if (!(t.what.contains("Vector") || t.what.equals("Smart Propeller"))) continue;
                double vq = t.force * Math.sin(Math.toRadians(45)) * Math.max(1, Math.abs(b.along(t.at.getX() + 0.5, t.at.getZ() + 0.5)));
                if (vq > q) {
                    q = vq;
                    how = "tilting its " + t.what;
                }
            }
            // A propeller or fin on an upright Swivel Bearing swings round as a Steering Wheel turns the bearing's cog.
            Swivel swivel = swivel(b, all, v, p0);
            if (swivel != null && !swivel.wheel())
                faults.add("nothing turns the Swivel Bearing at " + xyz(swivel.bearing()) + ", so what it carries only points straight: "
                        + "put a Steering Wheel at the helm with a line of cogwheels from its shaft to the bearing's cog");
            if (swivel != null && swivel.torque() > q) {
                q = swivel.torque();
                how = swivel.how();
            }
            double iy = b.iYaw(), damp = DAMP * iy;
            for (Part p : b.parts) {
                double r2 = sq(p.cx() - b.cx) + sq(p.cz() - b.cz);
                if (airtight(p.state)) damp += ENVELOPE_DRAG * p0 * r2;
                if (p.state.is(LEVITITE)) damp += 0.55 * r2;
            }
            for (Wings.Wing wing : wings.all) damp += wing.free() * p0 * (sq(wing.part().cx() - b.cx) + sq(wing.part().cz() - b.cz));
            long gyros = b.count("aeroworks:gyroscope");
            if (gyros > 0) damp += iy;
            if (lev > 0 && q > 0) damp = levititeBrake(b, q, damp);
            turn(q, damp, how, faults, notes, out, "aircraft");
            if (how != null) steering(b, swivel != null && how.equals(swivel.how()) ? swivel : null, faults, notes, out);
            if (gyros > 0) notes.add("its Gyroscope keeps it level but also brakes turning");
        }
        if (stress > 0) notes.add(String.format(Locale.ROOT, "its propellers draw about %.0f stress units at these speeds; the "
                + "engines turning them must supply that (a Portable Engine gives 2048, a Stirling Engine 4096)", stress));
        double liftAbove = hang(b, balloons, centres, all, out, faults, notes);
        if (liftAbove > 0.25 && wings.count == 0 && best != null && v > 0.5) cruisePitch(b, side, best, v, p0, liftAbove, faults, notes);
        // Pitch: a thrust line far above or below the centre of mass noses it up or down.
        double pitch = 0;
        for (Thrust t : side) pitch = Math.max(pitch, Math.abs(t.at.getY() + 0.5 - b.cy));
        if (pitch >= 4 && b.count("aeroworks:gyroscope") == 0 && wingLift <= 0)
            notes.add(String.format(Locale.ROOT, "its propellers push %.0f blocks off the height of its centre of mass, so it "
                    + "pitches when they run; a Gyroscope or lower propellers fix that", pitch));
    }

    /**
     * Sable's lift blocks on board (Create Propulsion wings, Create sails; Simulated's Symmetric Sail only drags). Each
     * pushes the way it faces with 0.475 x speed x air pressure, whichever way it moves, drags 0.069 x speed x pressure
     * and, against motion along its face's normal, 0.75 more (BlockSubLevelLiftProvider's defaults).
     */
    private static final class Wings {
        record Wing(Part part, Direction push, double lift, double par, double free) {
        }

        /** The ones that lift; all holds the drag-only ones too. */
        final List<Wing> list = new ArrayList<>(), all = new ArrayList<>();
        int count, down;
        /** Lift scalars facing up, less those facing down. */
        double up;
        /** Lift scalars by the horizontal way they push (3D data value). */
        final double[] side = new double[6];
        double dirless;
        /** Parallel drag scalars by the axis of the face's normal. */
        final double[] parallel = new double[3];

        double mass() {
            return list.stream().mapToDouble(wing -> wing.part.mass).sum();
        }

        /** Drag per unit of speed moving along an axis. */
        double drag(Direction.Axis axis, double pressure) {
            return pressure * (dirless + parallel[axis.ordinal()]);
        }

        String describe() {
            long sails = list.stream().filter(w -> w.part.state.is(SAILS)).count();
            return list.stream().filter(w -> w.push == Direction.UP).count() + " "
                    + (sails == 0 ? "wings" : sails == count ? "sails" : "wings and sails") + " facing up";
        }
    }

    private static Wings wings(Body b) {
        Wings w = new Wings();
        // A propeller bearing's sails spin with it as its own contraption: they push as its propeller, not as wings.
        Set<BlockPos> spinning = new HashSet<>();
        for (Part p : b.parts) {
            Direction f = facing(p.state);
            if (p.id.endsWith("propeller_bearing") && f != null) sails(b, p.pos, f, spinning);
        }
        for (Part p : b.parts) {
            if (spinning.contains(p.pos)) continue;
            if (!(p.state.getBlock() instanceof dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider lp)) continue;
            Direction push;
            double lift, par, free;
            try {
                push = lp.sable$getNormal(p.state).getOpposite();
                lift = lp.sable$getLiftScalar();
                par = lp.sable$getParallelDragScalar();
                free = lp.sable$getDirectionlessDragScalar();
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            w.dirless += free;
            w.parallel[push.getAxis().ordinal()] += par;
            Wings.Wing wing = new Wings.Wing(p, push, Math.max(lift, 0), par, free);
            w.all.add(wing);
            if (lift <= 0) continue;
            w.count++;
            w.list.add(wing);
            if (push == Direction.UP) w.up += lift;
            else if (push == Direction.DOWN) {
                w.up -= lift;
                w.down++;
            } else w.side[push.get3DDataValue()] += lift;
        }
        return w;
    }

    /** The lift scalar of a Sable lift block, 0 for anything else. */
    private static double liftScalar(BlockState s) {
        try {
            return s.getBlock() instanceof dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider lp ? lp.sable$getLiftScalar() : 0;
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /**
     * Wings in flight: take-off speed, sideways push from upright fins, and balance. Sable puts each wing's lift at the
     * wing, turning with the ship, so lift ahead of the centre of mass noses it up for as long as it flies (nothing
     * swings it back level as under a balloon) and only Sable's damping (0.09 x inertia) slows that; the thrust line
     * and the wings' drag add their own pitch.
     */
    private static void wingLift(Body b, Wings w, double v, double p0, double other, Direction best, List<Thrust> side,
            Map<BlockPos, Float> rpm, StringBuilder out, List<String> faults, List<String> notes) {
        double weight = G * b.m;
        if (w.up <= 0) {
            faults.add(w.down > 0 ? "its wings face down, so they push it down as it flies: a wing lifts the way it faces; lay "
                    + "them flat facing up" : "none of its wings lie flat facing up, so they lift nothing: a wing lifts the way "
                    + "it faces");
        } else if (other < weight && v > 0) {
            double takeoff = (weight - other) / (w.up * p0);
            if (takeoff <= v) {
                out.append(String.format(Locale.ROOT, "- Its wings carry it from about %.1f blocks/s (take-off speed at y %d), so "
                        + "it needs a run along the ground or water to get up to that.\n", takeoff, (int) TAKEOFF_Y));
                if (takeoff > 0.85 * v) notes.add(String.format(Locale.ROOT, "it only just flies: it takes off at %.1f blocks/s and "
                        + "tops out at %.1f; more wings or thrust give it margin to climb", takeoff, v));
            }
        }
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            double net = w.side[d.get3DDataValue()] - w.side[d.getOpposite().get3DDataValue()];
            if (net <= 0 || v <= 0) continue;
            double f = net * p0 * v;
            String text = String.format(Locale.ROOT, "its upright wings facing %s push it %s as it flies (%.0f at top speed), so "
                    + "it slides sideways: a wing pushes the way it faces whichever way it moves, so give each upright fin a twin "
                    + "facing the other way, or lay it flat", d.getName(), d.getName(), f);
            if (f > 0.1 * weight) faults.add(text);
            else if (f >= 1) notes.add(text);
        }
        if (best == null || w.up <= 0 || v <= 0) return;
        // Side to side: nothing levels a ship, so the wings' lift must sit over the centre of mass across it.
        Direction right = best.getClockWise();
        double l = 0, sum = 0;
        for (Wings.Wing wing : w.list) {
            if (wing.push.getAxis() != Direction.Axis.Y) continue;
            double f = wing.push == Direction.UP ? wing.lift : -wing.lift;
            sum += f;
            l += f * forward(b, right, wing.part.cx(), wing.part.cz());
        }
        if (sum > 0 && Math.abs(l / sum) >= 0.1) faults.add(String.format(Locale.ROOT, "it isn't balanced side to side: its wings "
                + "lift %.1f blocks to the %s of its centre of mass, so it rolls onto its %s side as it flies; make the wings the "
                + "same on both sides, or shift about %.0f mass x blocks of weight toward the %s", Math.abs(l / sum),
                l > 0 ? "right" : "left", l > 0 ? "left" : "right", Math.abs(l / sum) * b.m, l > 0 ? "right" : "left"));
        // Fore and aft: flown, since nothing in Sable trims a ship level (only an Aeroworks Gyroscope does); then, if it
        // fails, flown again with the wings moved, or with a gyroscope, to find what fixes it.
        int gyros = 0;
        double spin = 0;
        for (Part p : b.parts) {
            if (!p.id.equals("aeroworks:gyroscope")) continue;
            gyros++;
            spin += Math.min(1, Math.abs(rpm.getOrDefault(p.pos, 0f)) / GYRO_RPM);
        }
        if (gyros > 0 && spin == 0) faults.add("its Gyroscope isn't turning, so it holds nothing level: drive it from an engine on "
                + "board (full strength at " + (int) GYRO_RPM + " RPM, drawing 16 stress units per RPM)");
        // Side to side nothing rights a plane: a wing lifts the way it faces however it leans, so the smallest lean
        // tips its lift, it slides toward the low wing and spirals down (a biplane flown without one did, within 20 s).
        if (gyros == 0 && other < weight) faults.add("nothing holds its wings level side to side: a wing lifts the way it faces "
                + "however the plane leans, so the smallest lean grows into a spiral down. It needs an Aeroworks Gyroscope on "
                + "board, turned from below by an engine on the ship (a Portable Engine's own 32 RPM, through a gearbox under "
                + "it, holds a small plane level)");
        double gyro = gyros == 0 ? 0 : spin / gyros;
        Trial trial = trial(b, w, side, best, v, 0, gyro);
        out.append(trial.describe());
        if (trial.ok()) return;
        double fix = 0;
        for (double d = 1; d <= 6 && fix == 0; d++)
            for (double shift : new double[] {-d, d}) if (fix == 0 && trial(b, w, side, best, v, shift, gyro).ok()) fix = shift;
        if (fix != 0) {
            faults.add(trial.problem() + String.format(Locale.ROOT, ". With its wings %.0f block%s further toward the %s (or "
                    + "about %.0f mass x blocks of weight moved toward the %s) it flies steadily", Math.abs(fix),
                    Math.abs(fix) == 1 ? "" : "s", fix > 0 ? "nose" : "tail", Math.abs(fix) * (b.m - w.mass()), fix > 0 ? "tail" : "nose"));
            return;
        }
        double need = 0;
        for (double g : new double[] {0.125, 0.25, 0.5, 0.75, 1}) if (need == 0 && g > gyro && trial(b, w, side, best, v, 0, g).ok()) need = g;
        faults.add(trial.problem() + (need > 0 ? String.format(Locale.ROOT, ". Nothing in the air levels a ship by itself and "
                + "moving its wings doesn't settle it; %s turning at least %d RPM holds it level (full strength at %d RPM; it "
                + "takes its shaft from below and draws 16 stress units per RPM from an engine on board)", gyros == 0
                ? "an Aeroworks Gyroscope" : "its Gyroscope", (int) Math.round(need * GYRO_RPM), (int) GYRO_RPM)
                : ". Neither moving its wings nor a Gyroscope settles it: bring the thrusters' line level with the centre of "
                + "mass and keep the wings even around it"));
    }

    /** How it flies at full power, and at the power that holds its height (cruise; null when full power can't). */
    private record Trial(Flight full, Flight cruise) {
        boolean ok() {
            return !full.flipped() && full.swing() <= 15 && full.trim() > -12 && (cruise == null ? full.climb() > -1
                    : !cruise.flipped() && Math.abs(cruise.trim()) <= 12 && cruise.swing() <= 10);
        }

        String describe() {
            String text = full.describe("at full power");
            if (cruise != null && !full.flipped()) text += cruise.describe(String.format(Locale.ROOT, "with %.0f%% of its thrust "
                    + "(where it holds its height; Solid Fuel Thrusters are only on or off, so that means running them part of the "
                    + "time)", cruise.power() * 100));
            return text;
        }

        String problem() {
            if (full.flipped() || full.swing() > 15 || full.trim() <= -12 || cruise == null) return full.problem("at full power");
            return cruise.problem(String.format(Locale.ROOT, "with the %.0f%% of its thrust that holds its height", cruise.power() * 100));
        }
    }

    private static Trial trial(Body b, Wings w, List<Thrust> side, Direction best, double v, double shift, double gyro) {
        Flight full = fly(b, w, side, best, v, shift, gyro, 1);
        if (full.flipped() || full.climb() <= 0) return new Trial(full, null);
        double lo = 0, hi = 1;
        Flight cruise = full;
        for (int i = 0; i < 8; i++) {
            double mid = (lo + hi) / 2;
            Flight f = fly(b, w, side, best, v * mid, shift, gyro, mid);
            if (f.flipped() || f.climb() > 0) {
                hi = mid;
                cruise = f;
            } else lo = mid;
        }
        return new Trial(full, cruise);
    }

    /** Aeroworks' gyroscope: full strength at this RPM, a critically damped pull back to level (omega 3, damping 0.9). */
    private static final double GYRO_RPM = 256;

    /** Twenty seconds of flight from level at one power, worked out step by step: how the ship pitches and moves. */
    private record Flight(boolean flipped, boolean noseUp, double flipTime, double climb, double trim, double swing, double speed,
                          double power) {
        String describe(String how) {
            if (flipped) return String.format(Locale.ROOT, "- Flown %s, it noses %s and turns over within %.0f s.\n", how,
                    noseUp ? "up" : "down", Math.max(1, flipTime));
            return String.format(Locale.ROOT, "- Flown %s, it settles at %.0f blocks/s, %.0f° nose %s, %s %.1f blocks/s%s.\n", how,
                    speed, Math.abs(trim), trim >= 0 ? "up" : "down", climb >= 0 ? "climbing" : "sinking", Math.abs(climb),
                    swing > 3 ? String.format(Locale.ROOT, ", rocking %.0f° up and down", swing) : "");
        }

        String problem(String how) {
            if (flipped) return String.format(Locale.ROOT, "it isn't balanced in flight: %s it noses %s and turns over within "
                    + "%.0f s", how, noseUp ? "up" : "down", Math.max(1, flipTime));
            if (swing > 10) return String.format(Locale.ROOT, "it isn't steady in flight: %s it rocks %.0f° nose up and down", how, swing);
            if (climb <= -1) return String.format(Locale.ROOT, "it can't hold its height: %s it settles %.0f° nose %s and sinks "
                    + "%.1f blocks/s", how, Math.abs(trim), trim >= 0 ? "up" : "down", -climb);
            return String.format(Locale.ROOT, "it doesn't fly level: %s it flies %.0f° nose %s", how, Math.abs(trim),
                    trim >= 0 ? "up" : "down");
        }
    }

    /**
     * Flies the ship in its fore-and-aft plane with Sable's own rules: weight, damping (0.09 x mass and x inertia,
     * measured), each lift block's lift and drag at its own speed through the air, and each thruster or propeller
     * pushing where it sits. Starts level at top speed; shift moves every wing that far toward the nose.
     */
    private static Flight fly(Body b, Wings w, List<Thrust> side, Direction best, double v0, double shift, double gyro,
            double power) {
        java.util.IdentityHashMap<Part, Wings.Wing> wingOf = new java.util.IdentityHashMap<>();
        for (Wings.Wing wing : w.all) wingOf.put(wing.part, wing);
        int n = b.parts.size();
        double[] pa = new double[n], ph = new double[n];
        double m = 0, ca = 0, ch = 0;
        for (int i = 0; i < n; i++) {
            Part p = b.parts.get(i);
            pa[i] = forward(b, best, p.cx(), p.cz()) + (wingOf.containsKey(p) ? shift : 0);
            ph[i] = p.cy() - b.cy;
            m += p.mass;
            ca += p.mass * pa[i];
            ch += p.mass * ph[i];
        }
        ca /= m;
        ch /= m;
        double inertia = 0;
        for (int i = 0; i < n; i++) inertia += b.parts.get(i).mass * (1 / 6.0 + sq(pa[i] - ca) + sq(ph[i] - ch));
        // Lift blocks: place, normal (fore, up), scalars.
        List<double[]> lifts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Wings.Wing wing = wingOf.get(b.parts.get(i));
            if (wing == null) continue;
            Direction normal = wing.push.getOpposite();
            double nf = normal == best ? 1 : normal == best.getOpposite() ? -1 : 0, nu = normal.getStepY();
            lifts.add(new double[] {pa[i] - ca, ph[i] - ch, nf, nu, wing.lift, wing.par, wing.free});
        }
        List<double[]> pushes = new ArrayList<>(); // fore, up, force, airflow, along (1) or up (0)
        for (Thrust t : side) {
            double a = forward(b, best, t.at.getX() + 0.5, t.at.getZ() + 0.5) - ca, h = t.at.getY() + 0.5 - b.cy - ch;
            if (t.dir == best.getOpposite() && !t.reversible && t.airflow < Double.MAX_VALUE) {
                pushes.add(new double[] {a, h, -t.force, Double.MAX_VALUE, 1});
                continue;
            }
            if (t.dir != best && !(t.reversible && t.dir == best.getOpposite())) continue;
            pushes.add(new double[] {a, h, t.force, t.airflow, 1});
        }
        // The gyroscope's strength, as Aeroworks works it out: its RPM share, capped by its authority (5000 each at
        // full speed) over the ship's mean inertia.
        double meanInertia = (inertia + b.iYaw() + b.iRoll()) / 3, scale = Math.min(gyro, gyro * 5000 / Math.max(meanInertia, 1e-6));
        double dt = 0.025, x = 0, y = TAKEOFF_Y, vx = v0, vy = 0, th = 0, om = 0, minTh = 0, maxTh = 0, sumTh = 0, y15 = 0, felt = 0;
        int counted = 0;
        for (int step = 1; step <= 800; step++) {
            double cos = Math.cos(th), sin = Math.sin(th), fx = 0, fy = -G * m - DAMP * m * vy, tq = -DAMP * inertia * om;
            fx -= DAMP * m * vx;
            for (double[] k : lifts) {
                double rx = k[0] * cos - k[1] * sin, ry = k[0] * sin + k[1] * cos;
                double px = vx - om * ry, py = vy + om * rx;
                double nx = k[2] * cos - k[3] * sin, ny = k[2] * sin + k[3] * cos;
                double pr = pressure(y + ry), nv = nx * px + ny * py, par = nv * k[5] * pr;
                double lx = px - nx * par * dt, ly = py - ny * par * dt, lift = Math.hypot(lx, ly) * k[4] * pr;
                double gx = -(nx * par + px * k[6] * pr + nx * lift), gy = -(ny * par + py * k[6] * pr + ny * lift);
                fx += gx;
                fy += gy;
                tq += rx * gy - ry * gx;
            }
            for (double[] k : pushes) {
                double rx = k[0] * cos - k[1] * sin, ry = k[0] * sin + k[1] * cos;
                double along = vx * cos + vy * sin;
                double f = power * (k[3] < Double.MAX_VALUE ? k[2] * pressure(y + ry) * Math.max(0, 1 - along / k[3]) : k[2]);
                double gx = f * cos, gy = f * sin;
                fx += gx;
                fy += gy;
                tq += rx * gy - ry * gx;
            }
            if (scale > 0) {
                // It pulls toward level, damps the swing and cancels (smoothed) the torque it feels from everything else.
                felt += 0.2 * (tq - felt);
                tq += -scale * (9 * inertia * Math.sin(th) + 5.4 * inertia * om) - scale * felt;
            }
            vx += fx / m * dt;
            vy += fy / m * dt;
            om += tq / inertia * dt;
            x += vx * dt;
            y += vy * dt;
            th += om * dt;
            double deg = Math.toDegrees(th);
            if (Math.abs(deg) > 60) return new Flight(true, deg > 0, step * dt, 0, 0, 0, 0, power);
            if (step == 600) y15 = y;
            if (step > 600) {
                minTh = counted == 0 ? deg : Math.min(minTh, deg);
                maxTh = counted == 0 ? deg : Math.max(maxTh, deg);
                sumTh += deg;
                counted++;
            }
        }
        return new Flight(false, false, 0, (y - y15) / 5, sumTh / counted, maxTh - minTh, Math.hypot(vx, vy), power);
    }

    /** Offset from the centre of mass in a horizontal direction. */
    private static double forward(Body b, Direction d, double x, double z) {
        return (x - b.cx) * d.getStepX() + (z - b.cz) * d.getStepZ();
    }

    /**
     * How it hangs when hovering: a balloon lifts at the centre of its air, Levitite at its own blocks and an upward
     * propeller where it sits, and the ship swings until that lift is straight above its centre of mass (Sable doesn't
     * level anything; a Gyroscope does). Lift below the centre of mass turns it over.
     */
    /**
     * At speed the air drags on every envelope block at its own height and the propellers push at theirs, so a thrust line
     * below the middle of the drag tips the nose up (above it, down) until the lift over the centre of mass swings it back:
     * sin(pitch) = drag x (drag height - thrust height) / (weight x lift height).
     */
    private static void cruisePitch(Body b, List<Thrust> side, Direction best, double v, double p0, double liftAbove,
            List<String> faults, List<String> notes) {
        double drag = DAMP * b.m * v, dragY = DAMP * b.m * v * b.cy;
        for (Part p : b.parts) {
            double d = airtight(p.state) ? ENVELOPE_DRAG * p0 * v : p.state.is(LEVITITE) ? 0.55 * v : 0;
            drag += d;
            dragY += d * p.cy();
        }
        double push = 0, pushY = 0;
        for (Thrust t : side) {
            if (!(t.dir == best || t.reversible && t.dir == best.getOpposite())) continue;
            double f = t.airflow < Double.MAX_VALUE ? t.force * Math.max(0, 1 - v / t.airflow) : t.force;
            push += f;
            pushY += f * (t.at.getY() + 0.5);
        }
        if (push <= 0 || drag <= 0) return;
        double dy = dragY / drag, ty = pushY / push, gap = dy - ty;
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, drag * gap / (G * b.m * liftAbove)))));
        if (Math.abs(pitch) < 4) return;
        String text = String.format(Locale.ROOT, "at full speed it pitches about %.0f° nose %s: its propellers push %.1f blocks %s the "
                + "middle of its drag (the envelope, about %.0f blocks above its lowest block), so the air holds the top back while they drive "
                + "the bottom on; mount the propellers about level with the middle of the envelope (on pods beside it or on the "
                + "frame that holds it), or lower the envelope toward them", Math.abs(pitch), gap > 0 ? "up" : "down",
                Math.abs(gap), gap > 0 ? "below" : "above", dy - b.minY);
        if (Math.abs(pitch) >= 10) faults.add(text);
        else notes.add(text);
    }

    /** Hangs it under its lift; returns how far the lift centres above the centre of mass (NaN when nothing lifts it). */
    private static double hang(Body b, Map<BlockPos, int[]> balloons, Map<BlockPos, double[]> centres, List<Thrust> all,
            StringBuilder out, List<String> faults, List<String> notes) {
        double sum = 0, a = 0, l = 0, v = 0;
        for (Map.Entry<BlockPos, int[]> e : balloons.entrySet()) {
            double[] c = centres.get(e.getKey());
            double f = 16.5 * Math.min(e.getValue()[0], e.getValue()[1]);
            if (c == null || f <= 0) continue;
            sum += f;
            a += f * b.along(c[0], c[2]);
            l += f * b.lateral(c[0], c[2]);
            v += f * (c[1] - b.cy);
        }
        List<Part> lev = b.parts.stream().filter(p -> p.state.is(LEVITITE) || p.id.equals("minecraft:end_stone")).toList();
        double levTotal = 0;
        for (Part p : lev) levTotal += p.state.is(LEVITITE) ? 110 : 22;
        double levScale = levTotal > G * b.m ? G * b.m / levTotal : 1;
        for (Part p : lev) {
            double f = (p.state.is(LEVITITE) ? 110 : 22) * levScale;
            sum += f;
            a += f * b.along(p.cx(), p.cz());
            l += f * b.lateral(p.cx(), p.cz());
            v += f * (p.cy() - b.cy);
        }
        for (Thrust t : all) {
            if (t.dir != Direction.UP) continue;
            sum += t.force;
            a += t.force * b.along(t.at.getX() + 0.5, t.at.getZ() + 0.5);
            l += t.force * b.lateral(t.at.getX() + 0.5, t.at.getZ() + 0.5);
            v += t.force * (t.at.getY() + 0.5 - b.cy);
        }
        if (sum <= 0) return Double.NaN;
        a /= sum;
        l /= sum;
        v /= sum;
        if (b.count("aeroworks:gyroscope") > 0) {
            out.append(String.format(Locale.ROOT, "- Its lift centres %.1f blocks above its centre of mass; its Gyroscope holds it "
                    + "level.\n", v));
            return Double.NaN;
        }
        if (v <= 0.25) {
            faults.add(String.format(Locale.ROOT, "it is top-heavy: its lift centres %.1f blocks %s its centre of mass, so it "
                    + "turns over in the air; put the heavy blocks (engines, cargo) low, under the balloon, or add a Gyroscope",
                    Math.abs(v), v < 0 ? "below" : "level with"));
            return Double.NaN;
        }
        Direction bow = null;
        double strongest = 0;
        for (Thrust t : all)
            if (t.dir.getAxis() == b.keel && t.force > strongest) {
                strongest = t.force;
                bow = t.dir;
            }
        Direction plus = b.keel == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction side = b.keel == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        String ahead = bow == null ? plus.getName() + " end" : "bow", behind = bow == null ? plus.getOpposite().getName() + " end" : "stern";
        double up = bow == null || bow == plus ? 1 : -1;
        String plusSide = bow == null ? side.getName() + " side" : bow.getClockWise() == side ? "starboard" : "port";
        String minusSide = bow == null ? side.getOpposite().getName() + " side" : plusSide.equals("starboard") ? "port" : "starboard";
        // With the model's own axis, so a fix in blocks ("slide it toward the stern") says which way that is.
        ahead += axis(bow == null ? plus : bow);
        behind += axis(bow == null ? plus.getOpposite() : bow.getOpposite());
        plusSide += axis(side);
        minusSide += axis(side.getOpposite());
        // Swinging till the lift is over the centre of mass raises the end the lift sits toward.
        double pd = Math.toDegrees(Math.atan2(a, v)) * up, ld = Math.toDegrees(Math.atan2(l, v));
        String trimText = Math.abs(pd) < 0.25 ? "level fore and aft" : String.format(Locale.ROOT, "%s %.1f° down", pd > 0 ? behind : ahead, Math.abs(pd));
        String listText = Math.abs(ld) < 0.25 ? "level side to side" : String.format(Locale.ROOT, "%.1f° down to %s", Math.abs(ld), ld > 0 ? minusSide : plusSide);
        out.append(String.format(Locale.ROOT, "- Hovering it hangs %s and %s (its lift centres %.1f blocks above its centre of "
                + "mass", trimText, listText, v));
        List<String> fix = new ArrayList<>();
        if (Math.abs(pd) >= 0.25) fix.add(String.format(Locale.ROOT, "%.0f mass x blocks toward the %s", Math.abs(a) * b.m, pd > 0 ? ahead : behind));
        if (Math.abs(ld) >= 0.25) fix.add(String.format(Locale.ROOT, "%.0f toward %s", Math.abs(l) * b.m, ld > 0 ? plusSide : minusSide));
        if (!fix.isEmpty()) out.append("; its weight and lift are off by ").append(String.join(", ", fix))
                .append(": move the balloon or the gondola's rooms and machines to level it");
        out.append(").\n");
        // Sliding the balloon (or everything under it) by d moves lift and weight apart by d x the rest's share of the mass.
        double envelope = 0;
        for (Part p : b.parts) if (airtight(p.state)) envelope += p.mass;
        double slide = Math.abs(a) * b.m / Math.max(b.m - envelope, 0.25 * b.m);
        if (Math.abs(pd) >= TRIM_BAD) faults.add(String.format(Locale.ROOT, "it hangs %.1f° %s down: slide the balloon about %.0f "
                + "block%s toward the %s, or the gondola with everything under the balloon that far toward the %s (or move its heavy "
                + "rooms and machines that way), until it hangs level", Math.abs(pd), pd > 0 ? behind : ahead, Math.max(1, slide),
                Math.round(Math.max(1, slide)) == 1 ? "" : "s", pd > 0 ? behind : ahead, pd > 0 ? ahead : behind));
        else if (Math.abs(pd) >= TRIM_NOTE) notes.add(String.format(Locale.ROOT, "it hangs %.1f° %s down, which shows",
                Math.abs(pd), pd > 0 ? behind : ahead));
        double sideways = Math.abs(l) * b.m / Math.max(b.m - envelope, 0.25 * b.m);
        if (Math.abs(ld) >= TRIM_BAD) faults.add(String.format(Locale.ROOT, "it hangs %.1f° down to %s: slide the balloon about %.0f "
                + "block%s toward %s, or balance the weight under it across it (the same side to side)", Math.abs(ld),
                ld > 0 ? minusSide : plusSide, Math.max(1, sideways), Math.round(Math.max(1, sideways)) == 1 ? "" : "s",
                ld > 0 ? minusSide : plusSide));
        else if (Math.abs(ld) >= TRIM_NOTE) notes.add(String.format(Locale.ROOT, "it hangs %.1f° down to %s, which shows",
                Math.abs(ld), ld > 0 ? minusSide : plusSide));
        return v;
    }

    /**
     * Levitite brakes slow movement hard (16.5 per block sideways, fading as exp(-1.5 v²/9)), so a slow turn meets far
     * more resistance than a fast one. The turn rate is where torque first meets that resistance; returned as the
     * damping that gives it.
     */
    private static double levititeBrake(Body b, double q, double damp) {
        double top = q / damp;
        for (int i = 1; i <= 2000; i++) {
            double w = top * i / 2000, r = damp * w;
            for (Part p : b.parts) {
                if (!p.state.is(LEVITITE)) continue;
                double r2 = sq(p.cx() - b.cx) + sq(p.cz() - b.cz);
                r += 16.5 * r2 * w * Math.exp(-1.5 * r2 * w * w / 9);
            }
            if (r >= q) return q / w;
        }
        return damp;
    }

    /**
     * The speed a steady push settles at: the first speed where linear drag, Levitite's low-speed brake
     * (brake·v·exp(-1.5 v²/9)) and propellers losing grip as they near their airflow (overAir·v) use it all up.
     */
    /**
     * What a slow balloon needs, in the two things that decide its speed: push (propellers lose it toward their airflow)
     * and drag, which is mostly its envelope (every envelope block drags, so an envelope bigger than its load needs costs
     * speed). Push is given for the two ways an early airship gets it: Wooden Propellers at 256 RPM (256 force, 1024 stress
     * each, two per Portable Engine) and a Propeller Bearing with 13 sails at 128 RPM (0.2 x 13^1.5 x 128 = 1200 force,
     * 3328 stress, two Portable Engines on one shaft).
     */
    private static void airshipAdvice(Body b, double v, double drag, double filled, double balloonY, double p0,
            List<String> faults, List<String> notes) {
        double u = AIRSHIP_GOOD, want = drag * u;
        int wooden = (int) Math.ceil(want / (256 * p0 * (1 - u / 25.6)));
        int bearings = (int) Math.ceil(want / (1200 * p0 * (1 - u / 23.1)));
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "at %.1f blocks/s it is too slow to travel on (an "
                + "airship wants about %.0f): at that speed its drag is about %.0f, so it needs that much push there. Spin "
                + "propellers fast, since each pushes only up to its airflow: %d Wooden Propeller%s at 256 RPM (from a Portable "
                + "Engine's 32 RPM: " + Drives.change(32, 256) + "; one engine turns two), or %d Propeller Bearing%s with 13 "
                + "sails at 128 RPM (" + Drives.change(32, 128) + "; two Portable Engines on the same shaft turn one, as "
                + "engines at the same speed add up their stress). A bearing's big fan pushes more for the same engines", v, u, want, wooden, wooden == 1 ? "" : "s",
                bearings, bearings == 1 ? "" : "s"));
        double need = G * b.m / (16.5 * pressure(200 + balloonY));
        if (filled > need * 1.3)
            text.append(String.format(Locale.ROOT, ". Its envelope also holds %.0f m3 where about %.0f would carry it to y 200, "
                    + "and every envelope block drags (each as much as %.0f mass of ship): a smaller envelope sized to its load "
                    + "is faster", filled, need, ENVELOPE_DRAG * p0 / DAMP));
        if (v < AIR_SLOW) faults.add(text.toString());
        else notes.add(text.toString());
    }

    private static double terminal(double force, double linear, double brake, double overAir) {
        if (force <= 0) return 0;
        for (double v = 0.005; v < 80; v += 0.005) {
            double drag = linear * v + brake * v * Math.exp(-1.5 * v * v / 9);
            if (drag >= force - overAir * v) return v;
        }
        return 80;
    }

    /**
     * What keeps a propeller plane on the ground, in the two things that decide it: propellers push only up to their
     * airflow (0.1 x RPM for a Wooden or Andesite Propeller, 0.05 x √sails x RPM for a Propeller Bearing), so they must
     * turn fast enough to pass the take-off speed; and the take-off speed falls with more wing and less weight, where
     * a Wing block weighs four times a Create sail that lifts the same.
     */
    private static void propPlaneAdvice(Body b, Wings w, List<Thrust> side, Direction best, Map<BlockPos, Float> rpm,
            double needSpeed, List<String> faults) {
        double fastest = 0, slowest = Double.MAX_VALUE;
        boolean thrusters = false;
        for (Thrust t : side) {
            if (best != null && t.dir.getAxis() != best.getAxis()) continue;
            if (t.airflow == Double.MAX_VALUE) thrusters = true;
            else {
                fastest = Math.max(fastest, t.airflow);
                slowest = Math.min(slowest, Math.abs(rpm.getOrDefault(t.at, 0f)));
            }
        }
        if (fastest > 0 && !thrusters && needSpeed > 0.75 * fastest) {
            String spin = slowest < 256 ? String.format(Locale.ROOT, "spin them at 256 RPM, the most a shaft turns (they turn %.0f "
                    + "now; from a Portable Engine's 32: " + Drives.change(32, 256) + "; at 256 a Wooden Propeller pushes "
                    + "air at 25.6 blocks/s and draws 1024 stress units, so one Portable Engine turns two), and ", slowest) : "";
            faults.add(String.format(Locale.ROOT, "its propellers push air at only %.1f blocks/s and their push falls to nothing as "
                    + "it nears that, short of the %.1f blocks/s it needs to take off: %slower the take-off speed with more wing "
                    + "for its weight (a propeller plane wants about three sails facing up per mass of everything else, and a "
                    + "take-off speed well under three quarters of its propellers' airflow)", fastest, needSpeed, spin));
        }
        double heavy = 0;
        int count = 0;
        for (Wings.Wing wing : w.list) {
            if (wing.part().state.is(SAILS) || wing.part().mass <= 0.25) continue;
            heavy += wing.part().mass - 0.25;
            count++;
        }
        if (count > 0) faults.add(String.format(Locale.ROOT, "its %d heavy wing blocks weigh %.0f more than Create sails would "
                + "(a sail weighs 0.25 and lifts the same, so sail wings, like a fabric-covered biplane's, take off far slower)",
                count, heavy));
    }

    /**
     * A Wooden or Andesite Propeller pushes along its facing or against it by which way its shaft turns (times -1 when
     * a wrench has reversed it), so two that face alike but turn opposite ways, as through a gearbox's straight-on and
     * sideways faces, push against each other. Which way a whole drive turns is the builder's to set, so among the
     * propellers facing one way, the larger push counts as pushing the way they face and the others as pushing back.
     */
    private static List<Thrust> againstEachOther(Body b, List<Thrust> all, Map<BlockPos, Float> rpm, List<String> faults) {
        Map<Direction, double[]> by = new java.util.EnumMap<>(Direction.class);
        Map<Thrust, Integer> spin = new java.util.IdentityHashMap<>();
        for (Thrust t : all) {
            BlockState s = b.blocks.get(t.at);
            String id = s == null ? "" : id(s);
            if (!id.equals("aeronautics:wooden_propeller") && !id.equals("aeronautics:andesite_propeller")) continue;
            float r = rpm.getOrDefault(t.at, 0f);
            if (r == 0) continue;
            boolean reversed = s.getProperties().stream().anyMatch(q -> q.getName().equals("reversed")
                    && q instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp && s.getValue(bp));
            int sign = (int) Math.signum(r * t.dir.getAxisDirection().getStep() * (reversed ? -1 : 1));
            spin.put(t, sign);
            by.computeIfAbsent(t.dir, k -> new double[2])[sign > 0 ? 0 : 1] += t.force;
        }
        List<Thrust> out = new ArrayList<>();
        Map<Direction, List<String>> wrong = new java.util.EnumMap<>(Direction.class);
        for (Thrust t : all) {
            Integer sign = spin.get(t);
            if (sign == null) {
                out.add(t);
                continue;
            }
            double[] f = by.get(t.dir);
            int forward = f[0] >= f[1] ? 1 : -1;
            // Its spin sets which way it pushes, so a sideways one no longer counts as pushing either way (one facing up
            // or down still lifts whichever way the whole drive turns).
            if (sign == forward) out.add(t.dir.getAxis().isVertical() ? t : new Thrust(t.at, t.dir, t.force, t.airflow, t.what, false));
            else {
                out.add(new Thrust(t.at, t.dir.getOpposite(), t.force, t.airflow, t.what, false));
                wrong.computeIfAbsent(t.dir, k -> new ArrayList<>()).add(xyz(t.at));
            }
        }
        wrong.forEach((d, at) -> faults.add(String.format(Locale.ROOT, "the propeller%s at %s turn%s the other way from the "
                + "other%s facing %s, so %s push%s backward against them: reverse %s with a wrench (or turn %s shaft the "
                + "other way)", at.size() == 1 ? "" : "s", String.join(", ", at), at.size() == 1 ? "s" : "",
                at.size() == 1 ? "s" : "s", d.getName(), at.size() == 1 ? "it" : "they", at.size() == 1 ? "es" : "",
                at.size() == 1 ? "it" : "each", at.size() == 1 ? "its" : "their")));
        return out;
    }

    /** The push of the propellers and thrusters facing that way (not reversed to it). */
    private static double facingPush(List<Thrust> side, Direction d) {
        double sum = 0;
        for (Thrust t : side) if (t.dir == d) sum += t.force;
        return sum;
    }

    private static String describe(List<Thrust> list) {
        java.util.Map<String, double[]> by = new java.util.LinkedHashMap<>();
        for (Thrust t : list) {
            double[] a = by.computeIfAbsent(t.what, k -> new double[3]);
            a[0]++;
            a[1] += t.force;
            a[2] = t.airflow;
        }
        List<String> s = new ArrayList<>();
        by.forEach((k, a) -> s.add(String.format(Locale.ROOT, "%d %s, %.0f force%s", (int) a[0], k, a[1],
                a[2] < Double.MAX_VALUE ? String.format(Locale.ROOT, ", pushing up to %.0f blocks/s", a[2]) : "")));
        return String.join("; ", s);
    }

    /** One propeller's or thruster's push, or null. */
    private static Thrust airThrust(Body b, Part p, Map<BlockPos, Float> rpm, List<String> faults) {
        Direction f = facing(p.state);
        if (isThruster(p.id)) {
            if (f == null) return null;
            double base = switch (p.id) {
                case "createpropulsion:solid_fuel_thruster" -> 200;
                case "createpropulsion:thruster" -> 533;
                case "createpropulsion:vector_thruster", "createpropulsion:liquid_vector_thruster" -> 733;
                case "createpropulsion:ion_thruster" -> 800;
                default -> 0;
            };
            if (base == 0) return null;
            // It needs 10 clear blocks behind its nozzle (opposite the way it pushes) for full thrust.
            int clear = 0;
            for (BlockPos q = p.pos.relative(f.getOpposite()); clear < 10 && !b.solid(q); q = q.relative(f.getOpposite())) clear++;
            if (clear < 10) faults.add(String.format(Locale.ROOT, "the %s at %s has only %d clear blocks behind its nozzle, so it "
                    + "gives %d%% thrust; it needs 10", p.state.getBlock().getName().getString(), xyz(p.pos), clear, clear * 10));
            String name = p.id.contains("vector") ? "Vector Thruster" : p.state.getBlock().getName().getString();
            return new Thrust(p.pos, f, base * clear / 10.0, Double.MAX_VALUE, name, false);
        }
        if (!isPropeller(p.id)) return null;
        float r = Math.abs(rpm.getOrDefault(p.pos, 0f));
        if (r == 0) {
            faults.add("the " + p.state.getBlock().getName().getString() + " at " + xyz(p.pos) + " isn't turning: no engine on "
                    + "board drives it, so it pushes nothing");
            return null;
        }
        if (p.id.equals("aeronautics:smart_propeller")) return new Thrust(p.pos, Direction.UP, r, 0.1 * r, "Smart Propeller", false);
        if (f == null) return null;
        if (p.id.endsWith("propeller_bearing")) {
            int s = sails(b, p.pos, f);
            if (s < 2) {
                faults.add("the Propeller Bearing at " + xyz(p.pos) + " has " + s + " sail block" + (s == 1 ? "" : "s") + " on its "
                        + "face; it needs at least 2 (Create sails or Windcatchers) to push");
                return null;
            }
            return new Thrust(p.pos, f, 0.2 * Math.pow(s, 1.5) * r, 0.05 * Math.sqrt(s) * r, "Propeller Bearing (" + s + " sails)", true);
        }
        if (p.id.equals("create_submarine:submarine_propeller")) return null;
        return new Thrust(p.pos, f, r, 0.1 * r, p.state.getBlock().getName().getString(), true);
    }

    private static double propellerStress(Body b, Part p, Map<BlockPos, Float> rpm) {
        float r = Math.abs(rpm.getOrDefault(p.pos, 0f));
        Direction f = facing(p.state);
        if (p.id.endsWith("propeller_bearing") && f != null) return 2 * Math.max(sails(b, p.pos, f), 2) * r;
        return 4 * r;
    }

    /** Sail blocks on a propeller bearing's face, in the plane in front of it. */
    private static int sails(Body b, BlockPos bearing, Direction f) {
        return sails(b, bearing, f, new HashSet<>());
    }

    private static int sails(Body b, BlockPos bearing, Direction f, Set<BlockPos> found) {
        BlockPos start = bearing.relative(f);
        if (!b.blocks.containsKey(start)) return 0;
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        int count = 0;
        while (!todo.isEmpty() && seen.size() < 128) {
            BlockPos p = todo.poll();
            BlockState s = b.blocks.get(p);
            if (s.is(SAILS)) {
                count++;
                found.add(p);
            }
            for (Direction d : Direction.values()) {
                if (d.getAxis() == f.getAxis()) continue;
                BlockPos n = p.relative(d);
                BlockState ns = b.blocks.get(n);
                if (ns != null && ns.is(SAILS) && seen.add(n)) todo.add(n);
            }
        }
        return count;
    }

    /** A propeller or fin a Swivel Bearing swings round, its turning force at full lock and what turns the bearing. */
    private record Swivel(double torque, String how, BlockPos bearing, boolean wheel) {
    }

    /** How far a Steering Wheel is taken to swing a swivelled part, either way: past this a propeller mostly pushes sideways. */
    private static final double SWIVEL_LOCK = Math.toRadians(45);

    /**
     * Steering on a Swivel Bearing standing upright (facing up or down): the part glued to its face swings round its
     * axis, so a propeller there pushes partly sideways and a fin of Symmetric Sails meets the air at an angle. Measured
     * in-world: a Steering Wheel at 45° on a stern Wooden Propeller turned an 11 by 15 blimp about 11° a second. The part
     * is taken as the blocks within 2 of the bearing's face, which is what a small glue box of its own would hold.
     */
    private static Swivel swivel(Body b, List<Thrust> all, double v, double p0) {
        Swivel best = null;
        boolean wheel = b.count("simulated:steering_wheel") > 0 || b.count("aeroworks:mechanical_servo") > 0
                || b.count("aeroworks:stepper_servo") > 0;
        for (Part p : b.parts) {
            if (!p.id.equals("simulated:swivel_bearing")) continue;
            Direction f = facing(p.state);
            if (f == null || f.getAxis() != Direction.Axis.Y) continue;
            BlockPos face = p.pos.relative(f);
            if (!b.blocks.containsKey(face)) continue;
            Set<BlockPos> part = new HashSet<>(List.of(face));
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(part);
            while (!todo.isEmpty()) {
                BlockPos q = todo.poll();
                for (Direction d : Direction.values()) {
                    BlockPos n = q.relative(d);
                    BlockState ns = b.blocks.get(n);
                    if (ns == null || ns.isAir() || n.equals(p.pos) || n.distManhattan(face) > 2 || !part.add(n)) continue;
                    todo.add(n);
                }
            }
            double arm = Math.abs(b.along(p.cx(), p.cz()));
            if (arm < 1.5) continue;
            double torque = 0;
            String what = null;
            for (Thrust t : all) {
                // A Propeller Bearing's fan is a contraption of its own; riding a swivel is untried, so it isn't counted.
                if (!part.contains(t.at) || !t.dir.getAxis().isHorizontal() || t.what.startsWith("Propeller Bearing")) continue;
                double tq = (t.airflow < Double.MAX_VALUE ? t.force * p0 : t.force) * Math.sin(SWIVEL_LOCK) * arm;
                if (tq > torque) {
                    torque = tq;
                    what = t.what;
                }
            }
            // A fin: Symmetric Sail drag 1.75 x pressure x the speed across it, whose sideways share peaks at 45 degrees.
            long fins = part.stream().filter(q -> id(b.blocks.get(q)).endsWith("_symmetric_sail")).count();
            double fin = 1.75 * p0 * v * fins * Math.sin(SWIVEL_LOCK) * Math.cos(SWIVEL_LOCK) * arm;
            if (fin > torque) {
                torque = fin;
                what = fins + "-sail fin";
            }
            if (what == null) continue;
            String how = "swinging its " + what + " on the Swivel Bearing at " + xyz(p.pos)
                    + (wheel ? " with the Steering Wheel (at 45°)" : "");
            if (!wheel) torque = 0;
            if (best == null || torque > best.torque()) best = new Swivel(torque, how, p.pos, wheel);
        }
        return best;
    }

    /**
     * Who steers it, and with what. A pilot steers with one control turned left or right: a Steering Wheel turning a Swivel
     * Bearing, a Steering Wheel whose comparators (one at each side, each lit only while the wheel is turned that way) cut
     * that side's propellers, or an Aeroworks console's wheel sending Left and Right on two Redstone Link frequencies.
     * Running each side's propellers from a lever of its own is the fallback for a player who can't make a wheel yet.
     */
    private static void steering(Body b, Swivel swivel, List<String> faults, List<String> notes, StringBuilder out) {
        if (swivel != null) {
            if (swivel.wheel()) out.append("- Steered with one Steering Wheel, which swings the Swivel Bearing at ").append(xyz(swivel.bearing()))
                    .append(" (its own small glue box must hold the part it swings).\n");
            return;
        }
        if (b.count("aeroworks:control_desk") + b.count("aeroworks:control_stand") > 0) {
            out.append("- Steered from its Aeroworks console: a Wheel Module's Left and Right go out on two Redstone Link frequencies, "
                    + "each to the links that cut or slow that side.\n");
            return;
        }
        List<BlockPos> wheels = new ArrayList<>();
        for (Part p : b.parts) if (p.id.equals("simulated:steering_wheel")) wheels.add(p.pos);
        for (BlockPos w : wheels) {
            Direction f = facing(b.blocks.get(w));
            if (f == null || !f.getAxis().isHorizontal()) continue;
            if (reads(b, w, f.getClockWise()) && reads(b, w, f.getCounterClockWise())) {
                out.append("- Steered with one Steering Wheel: the comparator at each side of it lights only while the wheel is turned "
                        + "one way, and cuts the propellers on one side.\n");
                return;
            }
        }
        boolean makeable = Drives.canMake(new Drives.Part("simulated:steering_wheel", "steers"))
                && Drives.canMake(new Drives.Part("minecraft:comparator", "reads the wheel"));
        String fix = "steer it with one Steering Wheel at the helm instead: a comparator against each side of the wheel (its back to "
                + "the wheel) lights only while the wheel is turned one way (the other side's for the other way), so wire or Redstone "
                + "Link each one to a Clutch in the propeller line on one side; cutting one side's push swings the nose toward it (if it steers backwards, swap "
                + "the two). Or let the wheel's cog turn a Swivel Bearing under a stern propeller";
        if (!wheels.isEmpty()) {
            faults.add("its Steering Wheel at " + xyz(wheels.get(0)) + " steers nothing: a wheel only turns what its shaft or cog drives "
                    + "and what comparators at its sides read. " + Character.toUpperCase(fix.charAt(0)) + fix.substring(1));
        } else if (makeable) {
            faults.add("it turns only by running one side's propellers harder than the other's, and nothing at the helm does that with "
                    + "one control, so the pilot would juggle a lever for each side: " + fix);
        } else {
            notes.add("it steers by running one side's propellers harder, from a lever for each side; once the player can make a "
                    + "Steering Wheel (andesite casing, a large cogwheel and a shaft) and comparators, " + fix);
        }
    }

    /** Whether a comparator beside the wheel, on its {@code side}, reads it (its back against the wheel). */
    private static boolean reads(Body b, BlockPos wheel, Direction side) {
        BlockState s = b.blocks.get(wheel.relative(side));
        return s != null && s.is(net.minecraft.world.level.block.Blocks.COMPARATOR) && facing(s) == side.getOpposite();
    }

    private static double differentialAir(Body b, List<Thrust> side, Direction forward) {
        if (forward == null) return 0;
        double left = 0, right = 0;
        for (Thrust t : side) {
            if (t.dir.getAxis() != forward.getAxis()) continue;
            double lat = forward.getAxis() == Direction.Axis.X ? t.at.getZ() + 0.5 - b.cz : t.at.getX() + 0.5 - b.cx;
            if (Math.abs(lat) < 0.75) continue;
            if (lat > 0) right += t.force * lat;
            else left -= t.force * lat;
        }
        return left > 0 && right > 0 ? Math.max(left, right) : 0;
    }

    /** The upside-down bucket a burner fills: up and sideways through open cells, then down while each layer stays closed. */
    private static Set<BlockPos> balloon(Body b, BlockPos seed) {
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(seed));
        cells.add(seed);
        while (!todo.isEmpty()) {
            BlockPos p = todo.poll();
            for (Direction d : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockPos n = p.relative(d);
                if (cells.contains(n)) continue;
                BlockState s = b.blocks.get(n);
                if (s != null && airtight(s)) continue;
                if (outside(b, n)) return Set.of(); // the air escapes: no seal
                cells.add(n);
                todo.add(n);
            }
        }
        // Grow down a layer at a time while the layer under the lowest one is closed in sideways.
        int bottom = seed.getY();
        while (true) {
            int y = bottom - 1;
            Set<BlockPos> layer = new HashSet<>();
            boolean open = false;
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            for (BlockPos c : cells) if (c.getY() == bottom) {
                BlockPos below = c.below();
                BlockState s = b.blocks.get(below);
                if (s == null || !airtight(s)) {
                    if (layer.add(below)) q.add(below);
                }
            }
            while (!q.isEmpty() && !open) {
                BlockPos p = q.poll();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = p.relative(d);
                    if (layer.contains(n)) continue;
                    BlockState s = b.blocks.get(n);
                    if (s != null && airtight(s)) continue;
                    if (outside(b, n)) {
                        open = true;
                        break;
                    }
                    layer.add(n);
                    q.add(n);
                }
            }
            if (open || layer.isEmpty()) break;
            cells.addAll(layer);
            bottom = y;
        }
        // Blocks inside the envelope take up room the air can't.
        cells.removeIf(c -> b.blocks.containsKey(c) && !b.blocks.get(c).isAir());
        return cells;
    }

    private static boolean outside(Body b, BlockPos p) {
        return p.getX() < b.minX || p.getX() > b.maxX || p.getY() < b.minY || p.getY() > b.maxY || p.getZ() < b.minZ || p.getZ() > b.maxZ;
    }

    // ---------- land ----------

    private static void land(Body b, Map<BlockPos, Float> rpm, StringBuilder out, List<String> faults, List<String> notes) {
        List<Part> mounts = b.parts.stream().filter(p -> p.id.equals("offroad:wheel_mount")).toList();
        int n = mounts.size();
        double k = Math.min(b.m / n, 10), perWheel = b.m / n;
        double drive = 0;
        int driven = 0;
        for (Part p : mounts) {
            float r = Math.abs(rpm.getOrDefault(p.pos, 0f));
            if (r > 0) {
                driven++;
                drive += 1.75 * r;
            }
        }
        out.append(String.format(Locale.ROOT, "- %d wheel mount%s, %d driven, carrying about %.0f mass each.\n", n, n == 1 ? "" : "s",
                driven, perWheel));
        if (n < 3) faults.add("it has " + n + " wheel mount" + (n == 1 ? "" : "s") + "; it needs at least 3 (better 4) to stand");
        if (perWheel > 23.6 * k) faults.add(String.format(Locale.ROOT, "each wheel carries about %.0f mass, more than its "
                + "suspension holds at the default setting (about %.0f): it bottoms out; add wheels, or raise the mounts' "
                + "suspension setting", perWheel, 23.6 * k));
        if (driven == 0) {
            faults.add("no wheel mount is turned: drive at least two with a shaft from an engine on board");
            return;
        }
        double v = drive / (n * 1.5 * k + DAMP * b.m);
        out.append(String.format(Locale.ROOT, "- Top speed on flat ground about %.1f blocks/s.\n", v));
        judgeSpeed(v, CAR_BAD, CAR_SLOW, "vehicle", faults, notes);
        notes.add("it steers by redstone into a mount's sides (up to 30°), so one axle needs a steering control; tyres go in the mounts");
    }

    // ---------- verdicts ----------

    private static void judgeSpeed(double v, double bad, double slow, String what, List<String> faults, List<String> notes) {
        if (v < bad) faults.add(String.format(Locale.ROOT, "it crawls at %.1f blocks/s: too slow to be a usable %s (it should "
                + "manage at least %.0f); more thrust or less weight and drag", v, what, slow));
        else if (v < slow) notes.add(String.format(Locale.ROOT, "it is slow for a %s (%.1f blocks/s)", what, v));
    }

    private static void turn(double torque, double damping, String how, List<String> faults, List<String> notes, StringBuilder out, String what) {
        if (torque <= 0 || how == null) {
            faults.add("nothing turns it: it can only go straight. " + switch (what) {
                case "aircraft" -> "Steer it with one Steering Wheel at the helm: its cog turning a Swivel Bearing under a "
                        + "propeller at the stern, or a propeller on each side with a comparator at each side of the wheel cutting "
                        + "that side's line (a Clutch in it); rudders work only in water";
                case "submarine" -> "Add a Winds & Weather Rudder at the stern, or propellers on both sides";
                default -> "Steer with the Boat Engine's tiller, a Winds & Weather Rudder at the stern, or propellers on both sides";
            });
            return;
        }
        double deg = Math.toDegrees(torque / damping);
        out.append(String.format(Locale.ROOT, "- Turns at about %.0f°/s at speed, by %s.\n", deg, how));
        if (deg < TURN_BAD) faults.add(String.format(Locale.ROOT, "it barely turns (%.1f°/s): a full circle takes %.0f s; it "
                + "needs a stronger turning force farther from its centre", deg, 360 / deg));
        else if (deg < TURN_SLOW) notes.add(String.format(Locale.ROOT, "it turns slowly (%.0f°/s)", deg));
    }

    // ---------- helpers ----------

    private static double mass(BlockGetter getter, BlockPos pos, BlockState s) {
        try {
            return dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(getter, pos, s);
        } catch (RuntimeException | LinkageError e) {
            return s.getCollisionShape(getter, pos).isEmpty() ? 0 : 1;
        }
    }

    private static double volume(BlockState s) {
        try {
            return dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getVolume(s);
        } catch (RuntimeException | LinkageError e) {
            return 1;
        }
    }

    /**
     * The Boat Engine's own rules for a usable tiller: taking it (empty-hand right-click) is refused with "There is no room to sit
     * at the tiller" when any block's collision touches the box HelmServer.tryMount tests at helmAnchor, and the engine can't be
     * placed on top of a block (its propeller hangs in the space below).
     */
    private static void boatEngines(Map<BlockPos, BlockState> blocks, BlockGetter getter, List<String> faults) {
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (!id(e.getValue()).equals("create_high_seas:boat_engine")) continue;
            BlockPos pos = e.getKey();
            Direction f = facing(e.getValue());
            if (f == null || !f.getAxis().isHorizontal()) continue;
            BlockState below = blocks.get(pos.below());
            if (below != null && !below.canBeReplaced())
                faults.add("the Boat Engine at " + xyz(pos) + " sits on " + below.getBlock().getName().getString() + ", but a Boat Engine "
                        + "can't be placed on top of a block: its propeller hangs in the block below, so mount it where that block is open "
                        + "or water (over the stern edge)");
            Vec3 a;
            try {
                a = com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity.helmAnchor(pos, f);
            } catch (LinkageError err) {
                continue;
            }
            AABB seat = new AABB(a.x - 0.25, a.y + 0.3, a.z - 0.25, a.x + 0.25, a.y + 1.5, a.z + 0.25);
            List<String> in = new ArrayList<>();
            for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(seat.minX, seat.minY, seat.minZ),
                    BlockPos.containing(seat.maxX, seat.maxY, seat.maxZ))) {
                BlockState s = blocks.get(q);
                if (s == null || s.getCollisionShape(getter, q).isEmpty()) continue;
                VoxelShape shape = s.getCollisionShape(getter, q).move(q.getX(), q.getY(), q.getZ());
                if (shape.toAabbs().stream().anyMatch(box -> box.intersects(seat))) in.add(s.getBlock().getName().getString() + " at " + xyz(q));
            }
            if (!in.isEmpty())
                faults.add("no one can take the tiller of the Boat Engine at " + xyz(pos) + ": the engine needs open space for the "
                        + "helmsman two blocks out from its " + f.getSerializedName() + " side (the side it faces), at its height and one "
                        + "above, reaching a sliver into the column on the helmsman's left, and " + String.join(", ", in) + " fill that, so the "
                        + "engine says there is no room to sit at the tiller; clear those blocks (a deck, carpet or top slab is fine)");
            // The helmsman is held at that spot only while at the tiller: on letting go they drop from it, so the
            // vehicle has to be under their feet there, not open water beside the stern.
            BlockPos spot = BlockPos.containing(a.x, a.y, a.z);
            if (footing(blocks, getter, a) == null) {
                String turn = "turn the engine to face into the boat, with deck (or a floor block) two blocks in from it";
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (d == f) continue;
                    Vec3 other;
                    try {
                        other = com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity.helmAnchor(pos, d);
                    } catch (LinkageError err) {
                        break;
                    }
                    if (footing(blocks, getter, other) != null) {
                        turn = "turn the engine to face " + d.getSerializedName() + ", into the boat, so the helmsman stands on the "
                                + "deck at " + xyz(BlockPos.containing(other.x, other.y, other.z));
                        break;
                    }
                }
                faults.add("whoever takes the tiller of the Boat Engine at " + xyz(pos) + " stands two blocks out from the side it "
                        + "faces (" + f.getSerializedName() + "), at " + xyz(spot) + ", and no block of the vehicle is under their feet "
                        + "there: they would hang outside the boat at the tiller and fall off as soon as they let go; " + turn);
            }
        }
    }

    /**
     * Heavy storage blocks (iron, gold, copper and the like) carried as dead weight. A player may add one to trim a ship
     * they built; a designed ship sits level by its hull shape and layout, so in a model they are a fault.
     */
    private static void ballast(Map<BlockPos, BlockState> blocks, BlockGetter getter, List<String> faults) {
        Map<String, Integer> found = new java.util.TreeMap<>();
        blocks.forEach((p, s) -> {
            if (s.is(STORAGE_BLOCKS) && mass(getter, p, s) >= 2 && !powers(blocks, p, s))
                found.merge(s.getBlock().getName().getString(), 1, Integer::sum);
        });
        if (found.isEmpty()) return;
        List<String> what = new ArrayList<>();
        found.forEach((n, c) -> what.add(c + " " + n));
        faults.add("it carries " + String.join(", ", what) + " as dead weight: a designed ship sits level and upright by the "
                + "shape of its hull and where its rooms and machines go, not by ballast (that is the player's own fix for a "
                + "ship they built). Take the ballast out and shape or lay out the hull so it balances");
    }

    /** A block of redstone that touches something else of the vehicle is powering it (a burner, a bearing), not ballast. */
    private static boolean powers(Map<BlockPos, BlockState> blocks, BlockPos p, BlockState s) {
        if (!s.isSignalSource()) return false;
        for (Direction d : Direction.values()) {
            BlockState next = blocks.get(p.relative(d));
            if (next != null && !next.isAir() && !next.is(STORAGE_BLOCKS)) return true;
        }
        return false;
    }

    /** Controls a player has to stand at and use by hand, from the vehicle itself. */
    static boolean isControl(String id) {
        return id.equals("simulated:steering_wheel") || id.equals("simulated:throttle_lever") || id.equals("simulated:linked_typewriter")
                || id.equals("minecraft:lever") || id.equals("create_submarine:pump_controller");
    }

    /**
     * Where a player's feet rest at {@code feet} (a point on the vehicle): the top of the highest vehicle block under the
     * middle of their footprint, from half a block below to the 0.3 the helm's room test leaves free, or null when
     * nothing of the vehicle is there to stand on.
     */
    static Double footing(Map<BlockPos, BlockState> blocks, BlockGetter getter, Vec3 feet) {
        AABB under = new AABB(feet.x - 0.15, feet.y - 0.5, feet.z - 0.15, feet.x + 0.15, feet.y + 0.3, feet.z + 0.15);
        Double top = null;
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(under.minX, under.minY, under.minZ),
                BlockPos.containing(under.maxX, under.maxY, under.maxZ))) {
            BlockState s = blocks.get(q);
            if (s == null) continue;
            VoxelShape shape = s.getCollisionShape(getter, q);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.move(q.getX(), q.getY(), q.getZ()).toAabbs())
                if (box.intersects(under) && box.maxY <= under.maxY && (top == null || box.maxY > top)) top = box.maxY;
        }
        return top;
    }

    /**
     * Every Steering Wheel, Throttle Lever, lever and other hand control has to be in reach (a player's 4.5 blocks, from
     * the eyes to the nearest point of the block) of a spot on the vehicle a player can stand on: vehicle blocks under
     * the feet and room for a body above. A control that can only be worked from outside the vehicle, from the water or
     * from thin air, is of no use once it moves.
     */
    private static void controls(Map<BlockPos, BlockState> blocks, BlockGetter getter, List<String> faults) {
        List<BlockPos> controls = new ArrayList<>();
        blocks.forEach((p, s) -> {
            if (isControl(id(s))) controls.add(p);
        });
        if (controls.isEmpty()) return;
        List<Vec3> eyes = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            // Nobody works a wheel standing on top of it.
            if (isControl(id(e.getValue()))) continue;
            BlockPos cell = e.getKey().above();
            Vec3 feet = Vec3.atBottomCenterOf(cell);
            Double top = footing(blocks, getter, feet.add(0, 0.25, 0));
            if (top == null || top < cell.getY() - 0.5 || top > cell.getY() + 0.25) continue;
            AABB body = new AABB(feet.x - 0.3, top + 0.01, feet.z - 0.3, feet.x + 0.3, top + 1.8, feet.z + 0.3);
            if (blocked(blocks, getter, body)) continue;
            eyes.add(new Vec3(feet.x, top + 1.62, feet.z));
        }
        List<String> out = new ArrayList<>();
        for (BlockPos c : controls) {
            AABB box = new AABB(c);
            boolean reach = eyes.stream().anyMatch(eye -> {
                double dx = Math.max(box.minX - eye.x, Math.max(0, eye.x - box.maxX));
                double dy = Math.max(box.minY - eye.y, Math.max(0, eye.y - box.maxY));
                double dz = Math.max(box.minZ - eye.z, Math.max(0, eye.z - box.maxZ));
                return dx * dx + dy * dy + dz * dz <= 4.5 * 4.5;
            });
            if (!reach) out.add(blocks.get(c).getBlock().getName().getString() + " at " + xyz(c));
        }
        if (!out.isEmpty())
            faults.add("no one can work " + String.join(", ", out) + " from the vehicle: there is no spot on board to stand within "
                    + "reach (4.5 blocks) of it, with vehicle blocks underfoot and two blocks of room above. Put the controls by the "
                    + "deck or cabin floor where the driver stands");
    }

    private static boolean blocked(Map<BlockPos, BlockState> blocks, BlockGetter getter, AABB body) {
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            BlockState s = blocks.get(q);
            if (s == null) continue;
            VoxelShape shape = s.getCollisionShape(getter, q);
            if (!shape.isEmpty() && shape.move(q.getX(), q.getY(), q.getZ()).toAabbs().stream().anyMatch(b -> b.intersects(body)))
                return true;
        }
        return false;
    }

    /** Sable gives a block no buoyancy or drag when all six neighbours are full blocks. */
    private static boolean interior(Map<BlockPos, BlockState> blocks, BlockGetter getter, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState s = blocks.get(n);
            if (s == null || !s.isCollisionShapeFullBlock(getter, n)) return false;
        }
        return true;
    }

    private static boolean airtight(BlockState s) {
        return s.is(AIRTIGHT) || s.is(BlockTags.WOOL);
    }

    private static boolean isPropeller(String id) {
        return id.equals("aeronautics:propeller_bearing") || id.equals("aeronautics:gyroscopic_propeller_bearing")
                || id.equals("aeronautics:wooden_propeller") || id.equals("aeronautics:andesite_propeller") || id.equals("aeronautics:smart_propeller");
    }

    private static boolean isThruster(String id) {
        return id.startsWith("createpropulsion:") && (id.endsWith("thruster")) && !id.startsWith("createpropulsion:creative");
    }

    private static Direction facing(BlockState s) {
        if (s.hasProperty(BlockStateProperties.FACING)) return s.getValue(BlockStateProperties.FACING);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return null;
    }

    /** Air pressure at height y: 1 at sea level, thinning as exp(-0.004·(y-63)), capped at 1.5. */
    static double pressure(double y) {
        return Math.min(1.5, Math.exp(-0.004 * (y - 63)));
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static TagKey<Block> tag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    private static String xyz(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static double sq(double d) {
        return d * d;
    }
}
