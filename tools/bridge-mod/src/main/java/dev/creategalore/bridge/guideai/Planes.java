package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small propeller planes laid out by the server (one or two seats, wood, colour), so the book never wires a plane by
 * hand. The layout is the one that flies on Sable's rules and stays small: a light wooden fuselage with the pilot up
 * front under the wing and nothing ahead of them but a low nose and a window, so they see where they go; two Portable
 * Engines side by side behind the seats, each geared up to 256 RPM through a Gearshift and a cog ladder out along its
 * wing to an Analog Transmission and two Wooden Propellers on the wing's leading edge (four in all, pushing in line
 * with the centre of mass); two more engines, one on the other, under an Aeroworks Gyroscope at 256 RPM, which keeps
 * the wings level; and Grindstones
 * for landing gear (one under the nose, two just behind the centre of mass), which roll almost freely where wheel
 * mounts drag hard. One Steering Wheel steers by slowing one side's propellers (a comparator each side of it, by
 * Redstone Links to that side's transmission); a Throttle Lever slows both; a lever reverses the propellers through the
 * Gearshifts, which stops it in a few blocks after landing. Wings of several spans and chords, one deck or two, at a
 * few places along the fuselage, are each judged with the vehicle check itself (take-off and landing runs, flight,
 * balance, steering, the pilot's view and reach, boarding), and the smallest that passes with a short run is kept.
 */
final class Planes {
    /** Take-off run wished for, blocks: the smallest wing that needs no more than this wins; else the shortest run. */
    private static final double SHORT_RUN = 40;
    /**
     * Ceiling wished for, y: a plane that can't climb over hills is of little use (y 105 clears the trees and most hills; mountains
     * need more). Four Wooden Propellers on two Portable Engines push 1024 at most, so the ceiling comes from wing for the
     * weight, and it is touchy: air thins with height, so a few percent more weight costs about ten blocks of ceiling.
     * The gyroscope's second engine and its third step-up weigh about 5 more; with them a sail biplane needed more than
     * a 31-block span for y 110 and about 27 for y 105. Copycat Wings carry it in less (21 across for a biplane).
     */
    private static final double CEILING = 105;
    /** How long the fuselage is, nose to tail fin; the tail's stabiliser and fin stand at its last row. */
    private static final int TAIL = 14;
    /** Where along the fuselage the main gear stands (just behind the centre of mass). */
    private static final int GEAR = 8;
    /** Where along the cockpit the step in is, between the seats. */
    private static final int STEP = 4;
    /** The last row of the three-wide body (the engine bay's back); the boom runs on from here to the tail. */
    private static final int BAY = 9;
    /** The propellers' places out along each wing. */
    private static final int INNER = 7, OUTER = 9;
    /** Wing span either side of the fuselage's middle: the propellers sit out to OUTER, so the wing reaches past them. */
    private static final int MIN_HALF = OUTER + 1, MAX_HALF = 15;

    private Planes() {
    }

    /** What the player asked for. */
    record Spec(int seats, String wood, String colour, int wings, boolean copycat, int span, int chord, boolean round) {
    }

    /** One wing layout: span either side of the fuselage, chord, the row its leading edge is at, and a lower wing too. */
    record Wing(int half, int chord, int lead, boolean biplane) {
        int sails() {
            int upper = (2 * half + 1) * chord;
            return biplane ? upper + 2 * (half - 2) * chord : upper;
        }
    }

    // ---------- the spec ----------

    static JsonObject definition() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("seats", WorldTools.prop("integer", "1 or 2."));
        p.add("wood", WorldTools.prop("string", "Body wood."));
        p.add("colour", WorldTools.prop("string", "Wing dye."));
        p.add("wings", WorldTools.prop("integer", "1, 2, 0."));
        p.add("wing_block", WorldTools.prop("string", "sail or copycat_wing."));
        p.add("span", WorldTools.prop("integer", "0 auto."));
        p.add("tips", WorldTools.prop("string", "square/round."));
        o.add("properties", p);
        JsonArray req = new JsonArray();
        p.keySet().forEach(req::add);
        o.add("required", req);
        o.addProperty("additionalProperties", false);
        JsonObject a = new JsonObject();
        a.addProperty("type", "array");
        a.addProperty("description", "Empty or one small plane.");
        a.add("items", o);
        return a;
    }

    static Spec spec(JsonObject o, List<String> problems) {
        int seats = Diagrams.intOr(o, "seats", 2);
        String wood = GuideText.string(o, "wood").strip().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("_planks", "");
        String colour = GuideText.string(o, "colour").strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace("grey", "gray");
        if (wood.isEmpty()) wood = "spruce";
        if (colour.isEmpty()) colour = "white";
        if (seats < 1 || seats > 2) problems.add("plane seats must be 1 or 2");
        int wings = Diagrams.intOr(o, "wings", 0), span = Diagrams.intOr(o, "span", 0), chord = Diagrams.intOr(o, "chord", 0);
        String block = GuideText.string(o, "wing_block").strip().toLowerCase(Locale.ROOT).replace("createpropulsion:", "");
        String tips = GuideText.string(o, "tips").strip().toLowerCase(Locale.ROOT);
        if (wings < 0 || wings > 2) problems.add("plane wings must be 1, 2 or 0 (either)");
        if (!block.isEmpty() && !block.equals("sail") && !block.startsWith("copycat_wing"))
            problems.add("plane wing_block must be sail or copycat_wing");
        if (span != 0 && (span < 2 * MIN_HALF + 1 || span > 2 * MAX_HALF + 1))
            problems.add("plane span must be 0 (worked out) or " + (2 * MIN_HALF + 1) + " to " + (2 * MAX_HALF + 1) + " blocks");
        if (chord != 0 && (chord < 2 || chord > 7)) problems.add("plane chord must be 0 (worked out) or 2 to 7 blocks");
        if (!tips.isEmpty() && !tips.equals("square") && !tips.equals("round")) problems.add("plane tips must be square or round");
        if (!Airships.WOODS.contains(wood)) problems.add("plane wood must be one of " + String.join(", ", Airships.WOODS));
        if (!Airships.COLOURS.contains(colour)) problems.add("plane colour must be one of " + String.join(", ", Airships.COLOURS));
        return new Spec(seats, wood, colour, wings, block.startsWith("copycat"), span == 0 ? 0 : span | 1, chord,
                tips.equals("round"));
    }

    // ---------- the search ----------

    /** A tried layout: its wing, cells (centred frame), and the check's verdict and figures. */
    private record Try(Wing wing, Map<BlockPos, BlockState> cells, List<String> faults, Map<String, Double> figures) {
        double run() {
            return figures.getOrDefault("run", Double.MAX_VALUE);
        }

        double ceiling() {
            return figures.getOrDefault("ceiling", 0.0);
        }
    }

    /** Lays out the plane, or says why it can't (problems). Server thread: the check reads the block registry. */
    static Airships.Layout design(Spec spec, ServerLevel level, List<String> problems) {
        Map<String, BlockState> parts = new HashMap<>();
        String missing = parts(spec, parts);
        if (missing != null) {
            problems.add("the plane layout needs " + missing + ", which this server doesn't have");
            return null;
        }
        List<Wing> wings = new ArrayList<>();
        // The player's (or the book's) choices fix what they name; the rest is searched, smallest wing first.
        for (int half = MIN_HALF; half <= MAX_HALF; half++)
            for (int chord = 2; chord <= 7; chord++)
                for (boolean bi : new boolean[] {false, true}) {
                    if (spec.span() != 0 && 2 * half + 1 != spec.span()) continue;
                    if (spec.chord() != 0 ? chord != spec.chord() : chord < 3 || chord > 6) continue;
                    if (spec.wings() != 0 && bi != (spec.wings() == 2)) continue;
                    for (int lead = 1; lead <= 5; lead++) wings.add(new Wing(half, chord, lead, bi));
                }
        wings.sort(java.util.Comparator.comparingInt(Wing::sails).thenComparingInt(w -> w.biplane() ? 1 : 0));
        Try best = null, nearest = null;
        String why = null;
        for (Wing w : wings) {
            Map<BlockPos, BlockState> cells = fromZero(lay(spec, w, parts));
            // Glue takes only blocks that share faces: the cog ladders and propeller gearboxes meet the rest at edges.
            int[] size = size(cells);
            VehicleCheck.closeSteps(cells, size[0], size[1], size[2]);
            // Panes and fences join their neighbours as build_model will place them, so the check sees the same shapes
            // (a windscreen pane doesn't reach out to a wing beside it, which left a gap).
            Models.connect(cells, level);
            Map<BlockPos, Float> rpm = BuildCheck.rpm(cells, level, new ArrayList<>());
            BuildCheck.Report r = VehicleCheck.check(cells, level, rpm, true, true);
            if (r == null) continue;
            Try t = new Try(w, cells, r.faults(), new HashMap<>(VehicleCheck.FIGURES.get()));
            if (Boolean.getBoolean("creategalore.planes.debug"))
                System.out.println("[planes] " + w + " run " + t.run() + " " + t.figures + " " + r.text().lines().skip(1).findFirst().orElse("") + " " + t.faults);
            if (!t.faults.isEmpty()) {
                if (why == null) why = t.faults.get(0);
                continue;
            }
            if (t.run() <= SHORT_RUN && t.ceiling() >= CEILING) {
                best = t;
                break;
            }
            if (nearest == null || t.ceiling() > nearest.ceiling()) nearest = t;
        }
        if (best == null) best = nearest;
        if (best == null) {
            String hint = spec.wings() == 1 && !spec.copycat() ? "; sails can't carry a monoplane here (a single sail wing wide "
                    + "enough to lift it drags too much to hold its height): use wing_block copycat_wing, or two wings"
                    : spec.span() != 0 ? "; try span 0 so the server can pick a wider wing" : "";
            problems.add("no plane layout passed the vehicle check" + (why == null ? "" : " (the smallest: " + why + ")") + hint);
            return null;
        }
        return finish(spec, best);
    }

    // ---------- the blocks ----------

    /** Looks up every block state the layout uses; the first one this server lacks, or null. */
    private static String parts(Spec spec, Map<String, BlockState> out) {
        String w = spec.wood();
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("gear", "minecraft:grindstone[face=floor,facing=north]");
        raw.put("slabTop", "minecraft:" + w + "_slab[type=top]");
        raw.put("slab", "minecraft:" + w + "_slab[type=bottom]");
        raw.put("planks", "minecraft:" + w + "_planks");
        raw.put("stairs", "minecraft:" + w + "_stairs[facing=south,half=bottom]");
        raw.put("stairsNoseW", "minecraft:" + w + "_stairs[facing=east,half=top]");
        raw.put("stairsNoseE", "minecraft:" + w + "_stairs[facing=west,half=top]");
        raw.put("ladder", "minecraft:ladder[facing=west]");
        raw.put("belly", "minecraft:" + w + "_stairs[facing=south,half=top]");
        raw.put("wallW", "minecraft:" + w + "_stairs[facing=east,half=top]");
        raw.put("wallE", "minecraft:" + w + "_stairs[facing=west,half=top]");
        raw.put("linkUp", "create:redstone_link[facing=up,receiver=true]");
        raw.put("casing", "create:andesite_casing");
        raw.put("largeEnc", "create:andesite_encased_large_cogwheel[axis=x,top_shaft=true,bottom_shaft=true]");
        raw.put("cogEnc", "create:andesite_encased_cogwheel[axis=x,top_shaft=true,bottom_shaft=true]");
        raw.put("shaftEnc", "create:andesite_encased_shaft[axis=x]");
        raw.put("pane", "minecraft:glass_pane[east=true,west=true]");
        raw.put("wheel", "simulated:steering_wheel[facing=south]");
        raw.put("compWest", "minecraft:comparator[facing=east]");
        raw.put("compEast", "minecraft:comparator[facing=west]");
        raw.put("throttle", "simulated:throttle_lever[facing=south,inverted=true]");
        raw.put("lever", "minecraft:lever[face=floor,facing=south]");
        raw.put("seat", "create:" + spec.colour() + "_seat");
        raw.put("engineWest", "simulated:red_portable_engine[facing=west]");
        raw.put("engineEast", "simulated:red_portable_engine[facing=east]");
        raw.put("engineSouth", "simulated:red_portable_engine[facing=south]");
        raw.put("gearboxX", "create:gearbox[axis=x]");
        raw.put("gearboxY", "create:gearbox[axis=y]");
        raw.put("gyro", "aeroworks:gyroscope");
        // The gyroscope's step-up: each cogwheel's shaft opens only toward the one it hands the drive to.
        raw.put("gyroLarge1", "create:andesite_encased_large_cogwheel[axis=y,top_shaft=false,bottom_shaft=true]");
        raw.put("gyroSmall1", "create:andesite_encased_cogwheel[axis=y,top_shaft=true,bottom_shaft=false]");
        raw.put("gyroLarge2", "create:andesite_encased_large_cogwheel[axis=y,top_shaft=false,bottom_shaft=true]");
        raw.put("gyroSmall2", "create:andesite_encased_cogwheel[axis=y,top_shaft=true,bottom_shaft=false]");
        raw.put("gyroLarge3", "create:andesite_encased_large_cogwheel[axis=y,top_shaft=false,bottom_shaft=true]");
        raw.put("gyroSmall3", "create:andesite_encased_cogwheel[axis=y,top_shaft=false,bottom_shaft=false]");
        raw.put("gyroIdler", "create:andesite_encased_cogwheel[axis=y,top_shaft=true,bottom_shaft=false]");
        raw.put("gearshift", "create:gearshift[axis=x]");
        raw.put("large", "create:large_cogwheel[axis=x]");
        raw.put("cog", "create:cogwheel[axis=x]");
        raw.put("shaft", "create:shaft[axis=x]");
        raw.put("transmission", "simulated:analog_transmission[axis=x]");
        raw.put("propIn", "aeronautics:wooden_propeller[facing=north,reversed=true]");
        raw.put("propOut", "aeronautics:wooden_propeller[facing=north,reversed=false]");
        raw.put("linkSouth", "create:redstone_link[facing=south,receiver=true]");
        raw.put("linkInWest", "create:redstone_link[facing=west,receiver=true]");
        raw.put("linkInEast", "create:redstone_link[facing=east,receiver=true]");
        raw.put("linkDown", "create:redstone_link[facing=down,receiver=true]");
        raw.put("linkOutWest", "create:redstone_link[facing=west,receiver=false]");
        raw.put("linkOutEast", "create:redstone_link[facing=east,receiver=false]");
        raw.put("sail", spec.copycat() ? "createpropulsion:copycat_wing_8[facing=up]" : "create:" + spec.colour() + "_sail[facing=up]");
        raw.put("fin", "simulated:" + spec.colour() + "_symmetric_sail[axis=x]");
        raw.put("stabiliser", "simulated:white_symmetric_sail[axis=y]");
        raw.put("assembler", "simulated:physics_assembler[facing=north]");
        for (Map.Entry<String, String> e : raw.entrySet()) {
            BlockState s = Models.state(e.getValue());
            // An undyed sail or seat where the colour has none.
            if (s == null && (e.getKey().equals("sail") || e.getKey().equals("seat") || e.getKey().equals("fin")))
                s = Models.state(e.getValue().replace(spec.colour() + "_", "white_"));
            if (s == null) return e.getValue();
            out.put(e.getKey(), s);
        }
        return null;
    }

    /** The plane's cells, nose toward -z (north) at z 0, the fuselage's middle at x 0, the gear's foot at y 0. */
    private static Map<BlockPos, BlockState> lay(Spec spec, Wing w, Map<String, BlockState> p) {
        Map<BlockPos, BlockState> c = new LinkedHashMap<>();
        // The fuselage: a closed wooden body three wide, its belly and sides two blocks deep from nose to engine bay, then
        // a two-high boom one wide to the tail. Every part below joins it where its shape meets a block's, so nothing
        // hangs loose and no sky shows through the body.
        // Wooden stairs weigh half what planks do (Sable counts them super light) and still look solid from the side.
        for (int z = 0; z <= BAY; z++) c.put(new BlockPos(0, 1, z), p.get("belly"));
        // The boom is planks: stacked stairs would leave a notch in every block, a lattice from the side.
        for (int z = BAY + 1; z <= TAIL; z++) for (int y = 1; y <= 2; y++) c.put(new BlockPos(0, y, z), p.get("planks"));
        for (int z = 1; z <= BAY; z++) {
            c.put(new BlockPos(-1, 1, z), p.get("wallW"));
            c.put(new BlockPos(1, 1, z), p.get("wallE"));
        }
        c.put(new BlockPos(-1, 1, 0), p.get("stairsNoseW"));
        c.put(new BlockPos(1, 1, 0), p.get("stairsNoseE"));
        // The nose: a cowling of stairs sloping up to the windscreen, planks under the pilot's eye, panes at it.
        for (int x = -1; x <= 1; x++) {
            c.put(new BlockPos(x, 2, 0), p.get("stairs"));
            c.put(new BlockPos(x, 2, 1), p.get("planks"));
            c.put(new BlockPos(x, 3, 1), p.get("pane"));
        }
        // The dash: the wheel ahead of the pilot, a comparator each side of it (the cockpit's sides there), the throttle
        // and the reverse lever by the seat, and the senders for the links outside them.
        c.put(new BlockPos(0, 2, 2), p.get("wheel"));
        c.put(new BlockPos(-1, 2, 2), p.get("compWest"));
        c.put(new BlockPos(1, 2, 2), p.get("compEast"));
        c.put(new BlockPos(-1, 2, 3), p.get("throttle"));
        c.put(new BlockPos(1, 2, 3), p.get("lever"));
        c.put(new BlockPos(-2, 2, 2), p.get("linkOutWest"));
        c.put(new BlockPos(-2, 2, 3), p.get("linkOutWest"));
        c.put(new BlockPos(2, 2, 2), p.get("linkOutEast"));
        c.put(new BlockPos(2, 2, 3), p.get("linkOutEast"));
        c.put(new BlockPos(0, 2, 3), p.get("seat"));
        if (spec.seats() > 1) c.put(new BlockPos(0, 2, 5), p.get("seat"));
        // The cockpit's sides: planks, with a gap on the left at the step in, a ladder up the side below it.
        for (int z = STEP; z <= BAY; z++) {
            c.put(new BlockPos(1, 2, z), p.get("wallE"));
            if (z != STEP) c.put(new BlockPos(-1, 2, z), p.get("wallW"));
        }
        // The ladder hangs on a plank (a stair's side is too narrow to hold one).
        c.put(new BlockPos(-1, 1, STEP), p.get("planks"));
        c.put(new BlockPos(-2, 1, STEP), p.get("ladder"));
        // Landing gear: a Grindstone under the nose and two under the sides just behind the centre of mass, each right
        // under a plank so it is set into the body.
        c.put(new BlockPos(0, 0, 1), p.get("gear"));
        for (int x : new int[] {-1, 1}) c.put(new BlockPos(x, 0, GEAR), p.get("gear"));
        // Engines behind the seats, each reached from the cockpit to refuel. The side two drive the propellers; the
        // middle two, one on the other, each turn a gearbox, and the two gearboxes share one shaft up to the Gyroscope
        // through a tower of encased cogwheels stepping large to small three times, 32 up to 256 RPM. Aeroworks'
        // gyroscope works in proportion to its RPM and only reaches full strength at 256: at an engine's bare 32 this
        // plane flew banked 8 degrees, at 128 still a few, at 256 level. At 256 it takes 4096 stress units (16 per RPM),
        // both engines' worth. (Engines facing each other through one gearbox would leave the back one out of reach.)
        c.put(new BlockPos(-1, 3, 7), p.get("engineWest"));
        c.put(new BlockPos(1, 3, 7), p.get("engineEast"));
        c.put(new BlockPos(0, 2, 6), p.get("engineSouth"));
        c.put(new BlockPos(0, 3, 6), p.get("engineSouth"));
        c.put(new BlockPos(0, 2, 8), p.get("assembler"));
        c.put(new BlockPos(0, 2, 7), p.get("gearboxX"));
        c.put(new BlockPos(0, 3, 7), p.get("gearboxX"));
        c.put(new BlockPos(0, 4, 7), p.get("gyroLarge1"));
        // Every step-up is a pair, one cogwheel each side of the tower, so its weight sits on the middle line: off to
        // one side, it leaned the plane over a few degrees that the gyroscope had to hold all flight.
        for (int x : new int[] {-1, 1}) {
            c.put(new BlockPos(x, 4, 8), p.get("gyroSmall1"));
            c.put(new BlockPos(x, 5, 8), p.get("gyroLarge2"));
            c.put(new BlockPos(x, 6, 8), p.get("gyroSmall3"));
        }
        c.put(new BlockPos(0, 5, 7), p.get("gyroSmall2"));
        c.put(new BlockPos(0, 6, 7), p.get("gyroLarge3"));
        // The last pair hands 256 RPM to a cogwheel between them, under the gyroscope.
        c.put(new BlockPos(0, 6, 8), p.get("gyroIdler"));
        c.put(new BlockPos(0, 7, 8), p.get("gyro"));
        for (int s : new int[] {-1, 1}) {
            // Each engine's line, in a casing at the wing's root: the Gearshift (reverse), encased cogwheels stepping
            // large to small three times (32 to 256 RPM) down to the wing, with casing filling each step's corner, the
            // transmission, and the encased shaft along the leading edge with a propeller on a gearbox at each place.
            // The drive comes down from the engines to the wing so the propellers push level with the centre of mass.
            c.put(new BlockPos(2 * s, 3, 7), p.get("gearshift"));
            c.put(new BlockPos(2 * s, 4, 7), p.get("linkUp"));
            c.put(new BlockPos(3 * s, 3, 7), p.get("largeEnc"));
            c.put(new BlockPos(3 * s, 2, 7), p.get("casing"));
            c.put(new BlockPos(3 * s, 2, 6), p.get("cogEnc"));
            c.put(new BlockPos(4 * s, 2, 6), p.get("largeEnc"));
            c.put(new BlockPos(4 * s, 3, 6), p.get("casing"));
            c.put(new BlockPos(4 * s, 3, 5), p.get("cogEnc"));
            c.put(new BlockPos(5 * s, 3, 5), p.get("largeEnc"));
            c.put(new BlockPos(5 * s, 2, 5), p.get("casing"));
            c.put(new BlockPos(5 * s, 2, 4), p.get("cogEnc"));
            c.put(new BlockPos(6 * s, 2, 4), p.get("transmission"));
            c.put(new BlockPos(6 * s, 2, 5), p.get("linkSouth"));
            c.put(new BlockPos(7 * s, 2, 4), p.get(s < 0 ? "linkInWest" : "linkInEast"));
            // On a monoplane a casing behind the receiver joins the wing's root sails to the drive; a biplane's wings
            // run above and below that cell, so it stays empty there (a casing would hang between them).
            if (!w.biplane()) c.put(new BlockPos(7 * s, 2, 5), p.get("casing"));
            c.put(new BlockPos(6 * s, 2, 3), p.get("cogEnc"));
            for (int x = INNER; x <= OUTER; x++) {
                if (x == INNER || x == OUTER) {
                    c.put(new BlockPos(x * s, 2, 3), p.get("gearboxY"));
                    c.put(new BlockPos(x * s, 2, 2), p.get(x == INNER ? "propIn" : "propOut"));
                } else c.put(new BlockPos(x * s, 2, 3), p.get("shaftEnc"));
            }
        }
        // The wing, level with the drive line and the centre of mass, joined at its root to the body and the casings.
        // Sails join the blocks beside them at their middle, so the wing meets the body along its root.
        // A biplane's wings are above and below the drive line instead, the upper one joined to the casings' tops
        // and the lower one to the belly's sides, with the propellers between them.
        for (int y : w.biplane() ? new int[] {1, 3} : new int[] {2})
            for (int x = -w.half(); x <= w.half(); x++) {
                if (Math.abs(x) <= 1) continue;
                for (int z = w.lead(); z < w.lead() + w.chord(); z++) {
                    // Round tips: the tip's front and back corners left off, as a wing's tip curves in.
                    boolean corner = z == w.lead() || z == w.lead() + w.chord() - 1;
                    if (spec.round() && w.chord() >= 3 && Math.abs(x) == w.half() && corner) continue;
                    c.putIfAbsent(new BlockPos(x, y, z), p.get("sail"));
                }
            }
        // Tail: a flat stabiliser either side of its end and an upright fin on it.
        for (int x = -3; x <= 3; x++) if (x != 0) c.put(new BlockPos(x, 2, TAIL), p.get("stabiliser"));
        for (int y = 3; y <= 4; y++) c.put(new BlockPos(0, y, TAIL), p.get("fin"));
        return c;
    }

    /** What was asked for, as the start of "why this one". */
    private static String choices(Spec spec) {
        List<String> c = new ArrayList<>();
        if (spec.wings() != 0) c.add(spec.wings() == 2 ? "two wings" : "one wing");
        c.add(spec.copycat() ? "Copycat Wings (8m)" : "sails");
        if (spec.span() != 0) c.add(spec.span() + " blocks across");
        if (spec.chord() != 0) c.add(spec.chord() + " deep");
        if (spec.round()) c.add("round tips");
        return "asked for " + String.join(", ", c) + "; within that, ";
    }

    /** The cells moved so the smallest x, y and z are 0, as a model's are. */
    private static Map<BlockPos, BlockState> fromZero(Map<BlockPos, BlockState> cells) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        for (BlockPos q : cells.keySet()) {
            x0 = Math.min(x0, q.getX());
            y0 = Math.min(y0, q.getY());
            z0 = Math.min(z0, q.getZ());
        }
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) out.put(e.getKey().offset(-x0, -y0, -z0), e.getValue());
        return out;
    }

    private static int[] size(Map<BlockPos, BlockState> cells) {
        int[] s = new int[3];
        for (BlockPos q : cells.keySet()) {
            s[0] = Math.max(s[0], q.getX() + 1);
            s[1] = Math.max(s[1], q.getY() + 1);
            s[2] = Math.max(s[2], q.getZ() + 1);
        }
        return s;
    }

    // ---------- the result ----------

    private static Airships.Layout finish(Spec spec, Try t) {
        // The cells start at 0 already; the layout's own frame had the fuselage's middle at x 0.
        Map<BlockPos, BlockState> out = t.cells;
        int[] size = size(out);
        int sx = size[0], sy = size[1], sz = size[2];
        BlockPos shift = new BlockPos(Math.max(t.wing.half(), OUTER), 0, 0);
        Wing w = t.wing;
        Map<String, Double> f = t.figures;
        java.util.function.Function<BlockPos, String> at = q -> {
            BlockPos m = q.offset(shift);
            return m.getX() + "," + m.getY() + "," + m.getZ();
        };
        StringBuilder s = new StringBuilder("Laid out by the server from plane (for you: tell the player what it is and how to "
                + "build and fly it):\n");
        long wingBlocks = out.values().stream().map(b -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath())
                .filter(id -> id.equals("copycat_wing_8") || id.endsWith("_sail") && !id.endsWith("symmetric_sail")).count();
        s.append(String.format(Locale.ROOT, "- A small %s for %s: a %s fuselage %d blocks long, %s of %d %s (%d wide, %d deep, "
                        + "%s tips); %d x %d x %d in all, nose toward -z.\n", w.biplane() ? "biplane" : "monoplane",
                spec.seats() == 1 ? "one" : "two (pilot in front, passenger behind)", spec.wood().replace('_', ' '), TAIL + 1,
                w.biplane() ? "two wings" : "one wing", wingBlocks, spec.copycat() ? "Copycat Wings (8m)"
                        : spec.colour().replace('_', ' ') + " sails", 2 * w.half() + 1, w.chord(), spec.round() ? "round" : "square",
                sx, sy, sz));
        if (spec.copycat()) s.append("- Copycat Wings (8m): each is two Copycat Wings stacked in one block (cut a Wing on a "
                + "Mechanical Saw or stonecutter for a Copycat Wing, place one, then use a second on it), facing up; it lifts "
                + "half again as much as a sail for the same weight, which is why this plane is smaller than a sail-winged one. "
                + "Use any block on a placed wing to give it that block's look (white wool or concrete for a classic wing); "
                + "the look doesn't change its weight.\n");
        s.append("- Drive: two Portable Engines behind the seats, each through a Gearshift and encased cogwheels stepping large "
                + "to small three times (32 up to 256 RPM, a casing in each step's corner) down to its wing's root, then an "
                + "Analog Transmission and an encased shaft along the leading edge to two Wooden Propellers, all four pushing forward at the height of the centre of mass. The inner two have "
                + "their blades reversed (as the model shows) because they turn the other way round from the outer two; if a "
                + "propeller blows backwards when built, flip its blades with a wrench. Two more engines, one on the other behind the passenger, "
                + "each turning a gearbox on one shaft, turn the Gyroscope through three large-to-small steps (256 RPM: a gyroscope "
                + "works in proportion to its RPM, full at 256, and takes 16 stress units per RPM, both engines' worth; slower, "
                + "the plane flies banked), which keeps the wings level; nothing else does in the air.\n");
        s.append("- Landing gear: Grindstones, one under the nose and two just behind the centre of mass (they roll almost "
                + "freely; wheel mounts drag so hard at speed that a plane on them needs a far longer field). Board on the "
                + "left: a ladder on the plank there leads up to the gap in the cockpit's side.\n");
        s.append(String.format(Locale.ROOT, "- Controls, all within the pilot's reach: the Steering Wheel at %s steers by "
                        + "slowing one side's propellers (the comparator each side of it sends that side's Redstone Link); the "
                        + "Throttle Lever at %s sets both transmissions (pushed fully forward is flat out, pulled back the propellers stop); the lever at %s reverses the "
                        + "propellers through the Gearshifts, as a brake after landing. Redstone Link frequencies (two items "
                        + "in each link's slots, the same on the sender and its receivers): throttle Redstone + Redstone (sender "
                        + "%s, receivers on both transmissions' backs %s and %s); left side Stick + Redstone (%s to %s); right "
                        + "side Redstone + Stick (%s to %s); reverse Stick + Stick (%s to the links on top of both Gearshifts, %s "
                        + "and %s).\n", at.apply(new BlockPos(0, 2, 2)), at.apply(new BlockPos(-1, 2, 3)),
                at.apply(new BlockPos(1, 2, 3)), at.apply(new BlockPos(-2, 2, 3)), at.apply(new BlockPos(-6, 2, 5)),
                at.apply(new BlockPos(6, 2, 5)), at.apply(new BlockPos(-2, 2, 2)), at.apply(new BlockPos(-7, 2, 4)),
                at.apply(new BlockPos(2, 2, 2)), at.apply(new BlockPos(7, 2, 4)), at.apply(new BlockPos(2, 2, 3)),
                at.apply(new BlockPos(-2, 4, 7)), at.apply(new BlockPos(2, 4, 7))));
        s.append(String.format(Locale.ROOT, "- Worked out: from a field at about y 70 it lifts off at about %.0f blocks/s after a run of about %.0f blocks, "
                        + "tops out near %.0f blocks/s, ceiling about y %.0f; after touching down, reversing the propellers stops "
                        + "it in about %.0f blocks (the full check below has the exact figures).\n", f.getOrDefault("liftoff", 0.0),
                f.getOrDefault("run", 0.0), f.getOrDefault("top", 0.0), f.getOrDefault("ceiling", 0.0), f.getOrDefault("stop", 0.0)));
        s.append("- Flying it: build it on open flat ground or a runway pointed into the open, glue it all with Super Glue and "
                + "use the Physics Assembler at " + at.apply(new BlockPos(0, 2, 8)) + "; light all four engines, sit in the front "
                + "seat and push the throttle fully forward: it rolls, lifts and climbs by itself. Turn the wheel only a little at a "
                + "time: full wheel stops one side, which turns it hard and loses speed. To land, ease the throttle back to sink, and "
                + "once it is down flip the reverse lever, then off again when it has stopped.\n");
        boolean met = t.run() <= SHORT_RUN && t.ceiling() >= CEILING;
        s.append(String.format(Locale.ROOT, "- Why this one: " + choices(spec) + "wings %d to %d blocks across, 3 to 6 deep, at "
                + "five places along the fuselage were each judged with the vehicle check (flight, balance, take-off and landing "
                + "runs, steering, the pilot's view and reach, boarding), and this is the smallest that passes with a take-off "
                + "run of %.0f blocks or less and a ceiling of y %.0f or more%s. Its two drive engines give all the push it "
                + "has (four propellers at 256 RPM take all their stress), so more height or speed comes from less weight or "
                + "more wing, not more propellers. Keep the engines, gear and wing where they are when you revise it "
                + "(from_draft): they set its balance; light trim (paint, a pane, a banner) changes little; check every version.\n",
                2 * MIN_HALF + 1, 2 * MAX_HALF + 1, SHORT_RUN, CEILING, met ? "" : String.format(Locale.ROOT, " (none did both, so this is the one that climbs "
                        + "highest, run %.0f blocks, ceiling y %.0f: tell the player)", t.run(), t.ceiling())));
        s.append("- Open cockpit: above y 128 the wind chills whoever is aboard and above y 192 it freezes them, so tell the "
                + "player to sew Windproof Lining onto each armour piece before climbing high.\n");
        return new Airships.Layout(out, sx, sy, sz, Map.of(), s.toString());
    }
}
