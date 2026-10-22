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
            CAR_BAD = 3, CAR_SLOW = 6, TURN_BAD = 3, TURN_SLOW = 8, CLIMB_BAD = 0.5, CLIMB_SLOW = 1, CEILING_BAD = 100;
    /** A hull in water turns about half as fast as its turning force alone gives (measured; it slides sideways). */
    private static final double TURN_SLIP = 0.45;
    /** The height an airship is judged at for lifting off (above most ground, below most mountains). */
    private static final double TAKEOFF_Y = 100;

    private static final TagKey<Block> AIRTIGHT = tag("aeronautics:airtight"), LEVITITE = tag("aeronautics:levitite"),
            SAILS = tag("create:windmill_sails"), HIGH_SEAS_SAILS = tag("create_high_seas:sails");

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
        boolean props = blocks.values().stream().anyMatch(s -> isPropeller(id(s)) || isThruster(id(s)));
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
        Body body = new Body(parts, m, cx, cy, cz, blocks);

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
            // Propellers alone: a boat if one of them is a water propeller, else judged as an aircraft.
            air(body, rpm, out, faults, notes);
        }
        pieces(blocks, model, faults, notes);
        boatEngines(blocks, getter, faults);
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

    // ---------- the body: mass, inertia, shape ----------

    private static final class Body {
        final List<Part> parts;
        final double m, cx, cy, cz;
        final int minX, minY, minZ, maxX, maxY, maxZ, lenX, lenZ, height;
        final Map<BlockPos, BlockState> blocks;
        /** The long horizontal axis: a hull's keel line. */
        final Direction.Axis keel;

        Body(List<Part> parts, double m, double cx, double cy, double cz, Map<BlockPos, BlockState> blocks) {
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
            keel = lenX >= lenZ ? Direction.Axis.X : Direction.Axis.Z;
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

    /** Lift at waterline h, the floaters heeled by angle a (radians) about the keel line through the centre of mass. */
    private static double lift(Body b, List<Floater> fl, double h, double a) {
        double sum = 0, cos = Math.cos(a), sin = Math.sin(a);
        for (Floater f : fl) sum += f.lift * under(b.cy + b.lateral(f.x, f.z) * sin + (f.y - b.cy) * cos, h);
        return sum;
    }

    /** The waterline the weight settles at, or NaN when everything under still can't hold it up. */
    private static double waterline(Body b, List<Floater> fl, double a) {
        double lo = b.minY - b.height - 2, hi = b.maxY + b.height + 2, need = G * b.m;
        if (lift(b, fl, hi, a) < need) return Double.NaN;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (lift(b, fl, mid, a) < need) lo = mid;
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
                double ys = 0;
                for (BlockPos c : cells) ys += c.getY() + 0.5;
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
        double up = 0;
        for (Thrust t : all) if (t.dir == Direction.UP || t.reversible && t.dir == Direction.DOWN) up += t.force;
        List<Thrust> side = all.stream().filter(t -> t.dir.getAxis().isHorizontal()).toList();

        double p0 = pressure(TAKEOFF_Y);
        double lift0 = 16.5 * filled * pressure(TAKEOFF_Y + balloonY) + levLift + up * p0;
        out.append(String.format(Locale.ROOT, "- Lift at y %d: %.0f against a weight of %.0f (", (int) TAKEOFF_Y, lift0, G * b.m));
        List<String> src = new ArrayList<>();
        if (filled > 0) src.add(String.format(Locale.ROOT, "%d balloon%s, %.0f m3 filled", balloons.size(), balloons.size() == 1 ? "" : "s", filled));
        if (lev + endStone > 0) src.add(lev + endStone + " Levitite/End Stone, hover only");
        if (up > 0) src.add(String.format(Locale.ROOT, "%.0f upward thrust", up));
        out.append(src.isEmpty() ? "nothing lifts it" : String.join("; ", src)).append(").\n");
        for (int[] v : balloons.values())
            if (v[0] > v[1]) notes.add(String.format(Locale.ROOT, "a balloon holds %d m3 but its burners fill only %d (500 each at "
                    + "full); another burner would add lift", v[0], v[1]));
            else if (v[1] > v[0] * 1.6) notes.add(String.format(Locale.ROOT, "a balloon holds only %d m3 for %d m3 of burner: a "
                    + "bigger envelope would lift more (each m3 holds up 1.5 mass)", v[0], v[1]));
        if (lift0 < G * b.m) {
            faults.add(String.format(Locale.ROOT, "it can't take off: lift at y %d is %.0f, short of its weight %.0f by %.0f "
                    + "(about %.0f m3 more balloon, or %.0f mass less)", (int) TAKEOFF_Y, lift0, G * b.m, G * b.m - lift0,
                    (G * b.m - lift0) / (16.5 * p0), (G * b.m - lift0) / G));
        } else if (filled > 0 && lev == 0 && up == 0) {
            // Ceiling: where the balloon's lift falls to the weight (air thins as exp(-0.004·(y-63))).
            double need = (G * b.m - levLift) / (16.5 * filled);
            double ceiling = Math.min(280, 63 - Math.log(need) / 0.004 - balloonY);
            out.append(String.format(Locale.ROOT, "- Ceiling about y %.0f (the air thins as it climbs).\n", ceiling));
            if (ceiling < CEILING_BAD) faults.add(String.format(Locale.ROOT, "its ceiling is only about y %.0f: it barely clears "
                    + "the ground and hits hills; it needs more balloon for its weight", ceiling));
        }
        double climbDrag = DAMP * b.m + ENVELOPE_DRAG * p0 * airtight + 1.1 * lev;
        if (lift0 >= G * b.m) {
            double climb = terminal(lift0 - G * b.m, climbDrag, 22 * lev, 0);
            if (filled > 0 || up > 0) out.append(String.format(Locale.ROOT, "- Climbs at about %.1f blocks/s at y %d with everything at full; turning the "
                    + "burners down lets it sink.\n", climb, (int) TAKEOFF_Y));
            if (lev + endStone > 0 && filled == 0 && up == 0)
                faults.add("Levitite only cancels weight, it never climbs: it needs a balloon or upward propellers to rise");
            else if (climb < CLIMB_BAD) faults.add(String.format(Locale.ROOT, "it climbs at only %.1f blocks/s: too weak to get "
                    + "over terrain; more lift or less weight", climb));
            else if (climb < CLIMB_SLOW) notes.add(String.format(Locale.ROOT, "it climbs slowly (%.1f blocks/s)", climb));
        }

        // Sideways: propellers lose push as the ship nears their airflow speed; thrusters don't.
        if (side.isEmpty()) {
            faults.add("nothing pushes it along: it can only drift with the wind. Add propellers driven by an on-board engine, "
                    + "or thrusters");
        } else {
            double drag = DAMP * b.m + ENVELOPE_DRAG * p0 * airtight + 0.55 * lev;
            Direction best = null;
            double v = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                double props = 0, propsOverAir = 0, thrusters = 0;
                for (Thrust t : side) {
                    if (!(t.dir == d || t.reversible && t.dir == d.getOpposite())) continue;
                    if (t.airflow < Double.MAX_VALUE) {
                        props += t.force * p0;
                        propsOverAir += t.force * p0 / t.airflow;
                    } else thrusters += t.force;
                }
                double u = terminal(props + thrusters, drag, 16.5 * lev, propsOverAir);
                if (u > v) {
                    v = u;
                    best = d;
                }
            }
            if (lev > 0 && v < AIR_SLOW) {
                double push = 0;
                for (Thrust t : side) if (best != null && (t.dir == best || t.reversible && t.dir == best.getOpposite()))
                    push += t.airflow < Double.MAX_VALUE ? t.force * p0 : t.force;
                notes.add(String.format(Locale.ROOT, "its %d Levitite brake slow movement hard (16.5 per block at a crawl, fading "
                        + "above 2 blocks/s): it takes more than about %.0f of push to break free, and it has %.0f", lev,
                        17.3 * lev + drag * Math.sqrt(3), push));
            }
            out.append(String.format(Locale.ROOT, "- Top speed about %.1f blocks/s at y %d (%s; drag from %d envelope blocks "
                    + "and its mass).\n", v, (int) TAKEOFF_Y, describe(side), airtight));
            judgeSpeed(v, AIR_BAD, AIR_SLOW, "aircraft", faults, notes);
            double fastest = 0;
            for (Thrust t : side) if (t.airflow < Double.MAX_VALUE) fastest = Math.max(fastest, t.airflow);
            if (fastest > 0 && fastest < AIR_SLOW * 1.5 && v < AIR_SLOW)
                notes.add(String.format(Locale.ROOT, "at these speeds its propellers push air at only %.1f blocks/s, so they can "
                        + "never move it faster than that: gear them up (a large cogwheel driving a small one doubles the RPM; "
                        + "a Portable Engine gives 32 RPM) or use bigger propellers", fastest));
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
            double iy = b.iYaw(), damp = DAMP * iy;
            for (Part p : b.parts) {
                double r2 = sq(p.cx() - b.cx) + sq(p.cz() - b.cz);
                if (airtight(p.state)) damp += ENVELOPE_DRAG * p0 * r2;
                if (p.state.is(LEVITITE)) damp += 0.55 * r2;
            }
            long gyros = b.count("aeroworks:gyroscope");
            if (gyros > 0) damp += iy;
            if (lev > 0 && q > 0) damp = levititeBrake(b, q, damp);
            turn(q, damp, how, faults, notes, out, "aircraft");
            if (gyros > 0) notes.add("its Gyroscope keeps it level but also brakes turning");
        }
        if (stress > 0) notes.add(String.format(Locale.ROOT, "its propellers draw about %.0f stress units at these speeds; the "
                + "engines turning them must supply that (a Portable Engine gives 2048, a Stirling Engine 4096)", stress));
        // Pitch: a thrust line far above or below the centre of mass noses it up or down.
        double pitch = 0;
        for (Thrust t : side) pitch = Math.max(pitch, Math.abs(t.at.getY() + 0.5 - b.cy));
        if (pitch >= 4 && b.count("aeroworks:gyroscope") == 0)
            notes.add(String.format(Locale.ROOT, "its propellers push %.0f blocks off the height of its centre of mass, so it "
                    + "pitches when they run; a Gyroscope or lower propellers fix that", pitch));
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
    private static double terminal(double force, double linear, double brake, double overAir) {
        if (force <= 0) return 0;
        for (double v = 0.005; v < 80; v += 0.005) {
            double drag = linear * v + brake * v * Math.exp(-1.5 * v * v / 9);
            if (drag >= force - overAir * v) return v;
        }
        return 80;
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
        BlockPos start = bearing.relative(f);
        if (!b.blocks.containsKey(start)) return 0;
        Set<BlockPos> seen = new HashSet<>(List.of(start));
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(start));
        int count = 0;
        while (!todo.isEmpty() && seen.size() < 128) {
            BlockPos p = todo.poll();
            BlockState s = b.blocks.get(p);
            if (s.is(SAILS)) count++;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == f.getAxis()) continue;
                BlockPos n = p.relative(d);
                BlockState ns = b.blocks.get(n);
                if (ns != null && ns.is(SAILS) && seen.add(n)) todo.add(n);
            }
        }
        return count;
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
                case "aircraft" -> "Put propellers or thrusters on both sides (run one side harder to turn), or use Vector "
                        + "Thrusters; rudders work only in water";
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
        }
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
