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
 * Small planes laid out by the server (one or two seats, wood, colour), so the book never wires a plane by hand. The
 * propeller plane is the one that flies on Sable's rules and stays small: a light wooden fuselage with a tandem cockpit,
 * the pilot in the back seat as in an open-cockpit biplane (from the front one the nose propeller fills the middle of
 * the view), a low nose and a window ahead of them; under the cockpit floor three Portable Engines drive a Rotation Speed
 * Controller set to -256, whose shaft runs forward through an Analog Transmission (the throttle) to a twelve-sail
 * propeller on a Propeller Bearing at the nose. Nothing holds it level: a fourth engine behind the seats turns Tilt
 * Adapters that swing ailerons and an elevator on Swivel Bearings, so the pilot flies it, rolls it and loops it, as players
 * want (a Gyroscope would hold it level but stop all that, so it is only an add-on the player may ask for).
 * Offroad Wheel Mounts with Tires are its landing gear (one under the nose, two just behind the centre of mass,
 * suspension 5), each main wheel braked on its own Redstone Link, so braking one steers it on the ground. An Aeroworks
 * Control Stand is the cockpit: a Joystick Module for the stick, a Lever Module for the throttle and two Pedal Modules
 * for the brakes, each direction on its own Redstone Link frequency. Wings of several spans, chords and places, one deck or two, with
 * tails of several sizes, are each judged with the vehicle check itself (take-off and landing runs, flight, balance,
 * rolls, loops, the pilot's view and reach, boarding), and the smallest that passes with a short run is kept. The
 * result carries its wiring (every link's frequency, the controller's speed, each mount's suspension), which the
 * schematic keeps so the Schematicannon prints it set.
 */
final class Planes {
    /** Take-off run wished for, blocks: the smallest wing that needs no more than this wins; else the shortest run. */
    private static final double SHORT_RUN = 50;
    /**
     * Ceiling wished for, y: a plane that can't climb over hills is of little use (y 105 clears the trees and most hills;
     * mountains need more). Air thins with height, so the ceiling comes from wing for the weight, and it is touchy: a few
     * percent more weight costs about ten blocks of it. Copycat Wings carry the plane in less span than sails.
     */
    private static final double CEILING = 105;
    /** A jet's fuselage length, nose to tail fin (a propeller plane's tail row is searched, {@link #BACKS}). */
    private static final int TAIL = 14;
    /** Where along a jet's fuselage the main gear stands (just behind the centre of mass). */
    private static final int GEAR = 8;
    /** The cockpit's back seat, a propeller plane's pilot's (the front one, by the stand, is a jet pilot's). */
    private static final int PILOT = 5, PILOT_JET = 3;
    /** Where along the cockpit the step in is, between the seats. */
    private static final int STEP = 4;
    /** The first row behind the cockpit's back seat: a biplane's upper wing may cross the body from here back. */
    private static final int COCKPIT_END = 6;
    /** The last row of the three-wide body (the engine bay's back); the boom runs on from here to the tail. */
    private static final int BAY = 9;
    /**
     * Wing span either side of the fuselage's middle. The propeller is on the nose, so a wing can be short: every flat
     * block resists a roll in proportion to its distance out squared, so a short, deep wing rolls far quicker than a long,
     * thin one of the same area. Six is the least that leaves room for the aileron drive and an aileron past it.
     */
    private static final int MIN_HALF = 6, MAX_HALF = 15;

    private Planes() {
    }

    /** What the player asked for. */
    record Spec(int seats, String wood, String colour, int wings, boolean copycat, int span, int chord, boolean round, boolean jet) {
    }

    /**
     * One layout: wing span either side of the fuselage, chord, the row its leading edge is at, a lower wing too; the
     * tail's half-width and the row it stands at (0 for a jet, whose tail is fixed).
     */
    record Wing(int half, int chord, int lead, boolean biplane, int tail, int back) {
        int sails() {
            int upper = (2 * half + 1) * chord;
            return biplane ? upper + 2 * (half - 2) * chord : upper;
        }

        /** The wing's trailing edge row, where the ailerons hinge. */
        int te() {
            return lead + chord - 1;
        }

        /** The tail's row: the elevator's hinge line and the fin's back edge. */
        int tailRow() {
            return back == 0 ? TAIL : back;
        }

        /** Where the main gear stands: a row behind the middle of the wing, so just behind the centre of mass. */
        int gear() {
            return back == 0 ? GEAR : lead + chord / 2 + 1;
        }
    }

    // ---------- the spec ----------

    static JsonObject definition() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("seats", WorldTools.prop("integer", "1 or 2."));
        p.add("wood", WorldTools.prop("string", "Wood."));
        p.add("colour", WorldTools.prop("string", "Dye."));
        p.add("wings", WorldTools.prop("integer", "1, 2, 0."));
        p.add("wing_block", WorldTools.prop("string", "sail/copycat_wing."));
        p.add("span", WorldTools.prop("integer", "0 auto."));
        p.add("tips", WorldTools.prop("string", "square/round."));
        p.add("engine", WorldTools.prop("string", "prop/jet."));
        o.add("properties", p);
        JsonArray req = new JsonArray();
        p.keySet().forEach(req::add);
        o.add("required", req);
        o.addProperty("additionalProperties", false);
        JsonObject a = new JsonObject();
        a.addProperty("type", "array");
        a.addProperty("description", "Empty or one.");
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
        String engine = GuideText.string(o, "engine").strip().toLowerCase(Locale.ROOT);
        if (!engine.isEmpty() && !engine.equals("prop") && !engine.equals("jet")) problems.add("plane engine must be prop or jet");
        if (wings < 0 || wings > 2) problems.add("plane wings must be 1, 2 or 0 (either)");
        if (!block.isEmpty() && !block.equals("sail") && !block.startsWith("copycat_wing"))
            problems.add("plane wing_block must be sail or copycat_wing");
        if (span != 0 && (span < 2 * MIN_HALF + 1 || span > 2 * MAX_HALF + 1))
            problems.add("plane span must be 0 (worked out) or " + (2 * MIN_HALF + 1) + " to " + (2 * MAX_HALF + 1) + " blocks");
        if (chord != 0 && (chord < 2 || chord > 7)) problems.add("plane chord must be 0 (worked out) or 2 to 7 blocks");
        if (!tips.isEmpty() && !tips.equals("square") && !tips.equals("round")) problems.add("plane tips must be square or round");
        if (!Airships.WOODS.contains(wood)) problems.add("plane wood must be one of " + String.join(", ", Airships.WOODS));
        if (!Airships.COLOURS.contains(colour)) problems.add("plane colour must be one of " + String.join(", ", Airships.COLOURS));
        // Copycat Wings unless sails were asked for: on wheel gear only they lift a plane off before the wheels' drag wins.
        return new Spec(seats, wood, colour, wings, !block.equals("sail"), span == 0 ? 0 : span | 1, chord,
                tips.equals("round"), engine.equals("jet"));
    }

    // ---------- the search ----------

    /** A tried layout: its wing, cells (from 0), how far they were moved there, and the check's verdict, figures and text. */
    record Try(Wing wing, Map<BlockPos, BlockState> cells, BlockPos shift, List<String> faults, Map<String, Double> figures, String text) {
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
                    if (spec.jet()) {
                        for (int lead = 1; lead <= 5; lead++) wings.add(new Wing(half, chord, lead, bi, 3, 0));
                        continue;
                    }
                    // A propeller plane's ailerons hinge on the wing's trailing edge, where their gearbox and the
                    // controls engine stand on the body behind the cockpit. The tail stands at a fixed row while the
                    // wing slides: lift doesn't change with the angle a wing meets the air in Sable, so the wing must
                    // sit over the centre of mass (lift ahead of it noses the plane up as it speeds up, and it rocks),
                    // and a tail that moved with the wing would drag the centre of mass after it.
                    for (int back : BACKS)
                        for (int te = bi ? 8 : 10; te <= back - 6; te++)
                            for (int tail : TAILS) wings.add(new Wing(half, chord, te - chord + 1, bi, tail, back));
                }
        wings.sort(java.util.Comparator.comparingInt(Wing::sails).thenComparingInt(w -> w.biplane() ? 1 : 0)
                .thenComparingInt(w -> w.tail() + w.back()));
        String key = spec.seats() + "/" + spec.wings() + "/" + spec.copycat() + "/" + spec.span() + "/" + spec.chord() + "/"
                + spec.round() + "/" + spec.jet();
        Wing known = CHOSEN.get(key);
        if (known != null) wings.add(0, known);
        Try best = null, nearest = null;
        String why = null;
        // Wing sizes that couldn't get off the ground or hold their height, with the tail and tail row they had: a bigger
        // tail or one farther back only adds weight and drag, so those of that size are skipped (a smaller tail may
        // still fly: the tails are tried by size, not lightest first, so a heavy one can fail before a light one passes).
        Map<String, List<int[]>> grounded = new HashMap<>();
        long until = System.currentTimeMillis() + SEARCH_MS;
        boolean late = false;
        VehicleCheck.SEARCH.set(true);
        try {
            for (Wing w : wings) {
                String size = w.half() + "/" + w.chord() + "/" + w.biplane();
                if (grounded.getOrDefault(size, List.of()).stream().anyMatch(g -> g[0] <= w.tail() && g[1] <= w.back())) continue;
                if (System.currentTimeMillis() > until) {
                    late = true;
                    break;
                }
                Try t = attempt(spec, w, parts, level);
                if (t == null) continue;
                if (Boolean.getBoolean("creategalore.planes.debug"))
                    System.out.println("[planes] " + w + " run " + t.run() + " " + t.figures + " " + t.text.lines().skip(1).findFirst().orElse("") + " " + t.faults);
                if (!t.faults.isEmpty()) {
                    if (why == null) why = t.faults.get(0);
                    if (t.faults.stream().anyMatch(f -> f.startsWith("it can't take off") || f.startsWith("it never gets off the ground")
                            || f.startsWith("it barely gets off the ground") || f.startsWith("it can't hold its height")))
                        grounded.computeIfAbsent(size, k -> new ArrayList<>()).add(new int[] {w.tail(), w.back()});
                    continue;
                }
                if (t.run() <= SHORT_RUN && t.ceiling() >= CEILING) {
                    best = t;
                    break;
                }
                if (nearest == null || t.ceiling() > nearest.ceiling()) nearest = t;
            }
        } finally {
            VehicleCheck.SEARCH.set(false);
        }
        if (best == null) best = nearest;
        if (best == null) {
            // Measured by the search: one deck of sails never holds a two-seater's height (the widest sank 3 blocks/s at full
            // power, held level), while a sail biplane 17 wide and 6 deep flies (flown in a test world: it lifts off, climbs
            // and answers the stick as the Copycat-winged one does).
            String hint = !spec.copycat() ? spec.wings() == 1 ? "; one deck of sail wings can't lift this plane: a sail lifts two "
                    + "thirds as much as a Copycat Wing (8m) for the same weight, and one sail wing big enough adds more weight and "
                    + "drag than lift; a sail biplane flies (wings 2), or use wing_block copycat_wing" : "; a sail lifts two thirds as "
                    + "much as a Copycat Wing (8m) for the same weight, so a sail-winged plane needs a far bigger wing; "
                    + (spec.span() != 0 ? "try span 0 so the server can pick a wider one, or " : "") + "use wing_block copycat_wing"
                    : spec.span() != 0 ? "; try span 0 so the server can pick a wider wing" : "";
            problems.add((late ? "no plane layout passed the vehicle check in the time a question allows" : "no plane layout passed "
                    + "the vehicle check") + (why == null ? "" : " (the smallest: " + why + ")") + hint);
            return null;
        }
        CHOSEN.put(key, best.wing());
        // Checked once more in full (the search skipped what a failed design needn't be told), for the player's text.
        Try told = attempt(spec, best.wing(), parts, level);
        return finish(spec, told != null ? told : best);
    }

    /** How long the layout search may hold the server thread, ms: the player's question waits on it, and the server too. */
    private static final long SEARCH_MS = Long.getLong("creategalore.planes.searchMs", 6000);
    /** The wing each spec came to, kept for the server's run: the same wishes (any wood or colour) lay out the same plane. */
    private static final Map<String, Wing> CHOSEN = new java.util.concurrent.ConcurrentHashMap<>();

    /** One wing tried: laid out, joined up as build_model places it, and judged with the vehicle check (null if it can't be). */
    static Try attempt(Spec spec, Wing w, Map<String, BlockState> parts, ServerLevel level) {
        Map<BlockPos, BlockState> raw = lay(spec, w, parts);
        BlockPos shift = minus(raw);
        Map<BlockPos, BlockState> cells = fromZero(raw);
        // Glue takes only blocks that share faces: parts that meet the rest only at edges get a step filled in.
        int[] size = size(cells);
        VehicleCheck.closeSteps(cells, size[0], size[1], size[2]);
        // Panes and fences join their neighbours as build_model will place them, so the check sees the same shapes
        // (a windscreen pane doesn't reach out to a wing beside it, which left a gap).
        Models.connect(cells, level);
        Map<BlockPos, Float> rpm = BuildCheck.rpm(cells, level, new ArrayList<>(), speeds(spec, shift));
        BuildCheck.Report r = VehicleCheck.check(cells, level, rpm, true, true);
        if (r == null) return null;
        List<String> faults = new ArrayList<>(r.faults());
        // The rotation check the book's answer goes through too (a part rotation never reaches is a PROBLEM there), so
        // a layout with one never goes out.
        BuildCheck.Report spin = BuildCheck.check(cells, level, speeds(spec, shift));
        if (spin != null) faults.addAll(0, spin.faults());
        return new Try(w, cells, shift, faults, new HashMap<>(VehicleCheck.FIGURES.get()), r.text());
    }

    // ---------- the blocks ----------

    /** Looks up every block state the layout uses; the first one this server lacks, or null. */
    static String parts(Spec spec, Map<String, BlockState> out) {
        String w = spec.wood();
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("gearEast", "offroad:wheel_mount[facing=east]");
        raw.put("gearWest", "offroad:wheel_mount[facing=west]");
        raw.put("planks", "minecraft:" + w + "_planks");
        raw.put("stairs", "minecraft:" + w + "_stairs[facing=south,half=bottom]");
        raw.put("stairsNoseW", "minecraft:" + w + "_stairs[facing=east,half=top]");
        raw.put("stairsNoseE", "minecraft:" + w + "_stairs[facing=west,half=top]");
        raw.put("ladder", "minecraft:ladder[facing=west]");
        raw.put("belly", "minecraft:" + w + "_stairs[facing=south,half=top]");
        raw.put("wallW", "minecraft:" + w + "_stairs[facing=east,half=top]");
        raw.put("wallE", "minecraft:" + w + "_stairs[facing=west,half=top]");
        raw.put("linkUp", "create:redstone_link[facing=up,receiver=true]");
        raw.put("pane", "minecraft:glass_pane[east=true,west=true]");
        raw.put("stand", "aeroworks:control_stand[facing=north]");
        raw.put("seat", "create:" + spec.colour() + "_seat");
        raw.put("engineWest", "simulated:red_portable_engine[facing=west]");
        raw.put("engineSouth", "simulated:red_portable_engine[facing=south]");
        raw.put("engineNorth", "simulated:red_portable_engine[facing=north]");
        raw.put("gearboxX", "create:gearbox[axis=x]");
        raw.put("gearboxY", "create:gearbox[axis=y]");
        raw.put("cog", "create:cogwheel[axis=x]");
        raw.put("shaft", "create:shaft[axis=x]");
        raw.put("linkSouth", "create:redstone_link[facing=south,receiver=true]");
        raw.put("sail", spec.copycat() ? "createpropulsion:copycat_wing_8[facing=up]" : "create:" + spec.colour() + "_sail[facing=up]");
        raw.put("fin", "simulated:" + spec.colour() + "_symmetric_sail[axis=x]");
        raw.put("stabiliser", "simulated:white_symmetric_sail[axis=y]");
        raw.put("assembler", "simulated:physics_assembler[facing=north]");
        raw.put("rsc", "create:rotation_speed_controller[axis=x]");
        raw.put("largeEncZ", "create:andesite_encased_large_cogwheel[axis=z,top_shaft=true,bottom_shaft=true]");
        raw.put("shaftEncZ", "create:andesite_encased_shaft[axis=z]");
        raw.put("transmissionZ", "simulated:analog_transmission[axis=z]");
        raw.put("cogEncZ", "create:andesite_encased_cogwheel[axis=z,top_shaft=true,bottom_shaft=true]");
        raw.put("propBearing", "aeronautics:propeller_bearing[facing=north]");
        raw.put("hub", "minecraft:" + w + "_planks");
        raw.put("blade", "create:" + spec.colour() + "_sail[facing=north]");
        raw.put("linkEast", "create:redstone_link[facing=east,receiver=true]");
        raw.put("linkNorth", "create:redstone_link[facing=north,receiver=true]");
        raw.put("tiltWest", "createpropulsion:tilt_adapter[axis=x,axis_along_first=false,facing=west]");
        raw.put("tiltEast", "createpropulsion:tilt_adapter[axis=x,axis_along_first=false,facing=east]");
        raw.put("swivelWest", "simulated:swivel_bearing[facing=west]");
        raw.put("swivelEast", "simulated:swivel_bearing[facing=east]");
        raw.put("flap", "simulated:white_symmetric_sail[axis=y]");
        if (spec.jet()) {
            raw.put("thruster", "create_flight_control:smart_vector_thruster[facing=south]");
            raw.put("tank", "create:fluid_tank");
            raw.put("fcc", "create_flight_control:flight_control_computer");
            raw.put("stabilizer", "create_flight_control:attitude_hold");
            raw.put("speedCoupler", "create_flight_control:velocity_coupler");
            raw.put("turnCoupler", "create_flight_control:angular_velocity_coupler");
            raw.put("proxy", "create_flight_control:flight_control_redstone_proxy[facing=north]");
            raw.put("gearboxZ", "create:gearbox[axis=z]");
            raw.put("linkWest", "create:redstone_link[facing=west,receiver=true]");
        }
        for (Map.Entry<String, String> e : raw.entrySet()) {
            BlockState s = Models.state(e.getValue());
            // An undyed sail or seat where the colour has none.
            if (s == null && (e.getKey().equals("sail") || e.getKey().equals("seat") || e.getKey().equals("fin") || e.getKey().equals("blade")))
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
        for (int z = BAY + 1; z <= w.tailRow(); z++) for (int y = 1; y <= 2; y++) c.put(new BlockPos(0, y, z), p.get("planks"));
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
        // The dash: an Aeroworks Control Stand ahead of the pilot, as in a real cockpit. Its modules (a stick, a throttle
        // lever and pedals; a jet's yoke and throttle) send each direction on its own Redstone Link frequency straight to
        // the receivers at what they work, so the cockpit needs no wiring of its own.
        c.put(new BlockPos(0, 2, 2), p.get("stand"));
        for (int z = 2; z <= 3; z++) {
            c.put(new BlockPos(-1, 2, z), p.get("wallW"));
            c.put(new BlockPos(1, 2, z), p.get("wallE"));
        }
        // The seats, in tandem. A propeller plane's pilot sits in the back one, as in an open-cockpit biplane: the
        // propeller's disc is level with their eyes, and from the front seat, close behind it, it hid 44% of the view out
        // (the check's limit is a quarter); from the back it hides little. A passenger sits in front, or that pit stays
        // open. A jet has nothing on its nose, so its pilot sits in front, by the stand.
        c.put(new BlockPos(0, 2, spec.jet() ? PILOT_JET : PILOT), p.get("seat"));
        if (spec.seats() > 1) c.put(new BlockPos(0, 2, spec.jet() ? PILOT : PILOT_JET), p.get("seat"));
        // The cockpit's sides: planks, with a gap on the left at the step in, a ladder up the side below it.
        for (int z = STEP; z <= BAY; z++) {
            c.put(new BlockPos(1, 2, z), p.get("wallE"));
            if (z != STEP) c.put(new BlockPos(-1, 2, z), p.get("wallW"));
        }
        // The ladder hangs on a plank (a stair's side is too narrow to hold one).
        c.put(new BlockPos(-1, 1, STEP), p.get("planks"));
        c.put(new BlockPos(-2, 1, STEP), p.get("ladder"));
        // Landing gear: a Wheel Mount under the nose and one under each side just behind the centre of mass, set into the
        // body's underside. A mount holds its tire on the side it faces, in the cell beside it, so each faces out where
        // that cell is open (the nose one to the right, the mains away from the body).
        c.put(new BlockPos(0, 0, 1), p.get("gearEast"));
        c.put(new BlockPos(-1, 0, w.gear()), p.get("gearWest"));
        c.put(new BlockPos(1, 0, w.gear()), p.get("gearEast"));
        // A keel plank between the main mounts joins them to each other and to the body (each mount's top holds its brake link).
        if (!spec.jet()) c.put(new BlockPos(0, 0, w.gear()), p.get("planks"));
        if (spec.jet()) {
            jet(w, c, p);
            wingAndTail(spec, w, c, p);
            return c;
        }
        drive(w, c, p);
        controls(w, c, p);
        wingAndTail(spec, w, c, p);
        return c;
    }

    /** The wing (one deck or two) and the tail, joined at the wing's root to the body. */
    private static void wingAndTail(Spec spec, Wing w, Map<BlockPos, BlockState> c, Map<String, BlockState> p) {
        // The wing, level with the drive line and the centre of mass, joined at its root to the body and the casings.
        // Sails join the blocks beside them at their middle, so the wing meets the body along its root.
        // A biplane's wings are above and below the drive line instead, the upper one joined to the casings' tops
        // and the lower one to the belly's sides, with the propellers between them.
        for (int y : w.biplane() ? new int[] {1, 3} : new int[] {2})
            for (int x = -w.half(); x <= w.half(); x++) {
                // A propeller biplane's upper wing runs across the body behind the cockpit (on the walls' tops), which
                // joins its halves to the plane; over the seats it leaves the cockpit open.
                // Behind the three-wide body the wing runs across the one-wide boom too, so it stays joined to it.
                boolean across = !spec.jet() && (y == 3 && w.biplane() || y < 3);
                if (Math.abs(x) <= 1 && !across) continue;
                for (int z = w.lead(); z < w.lead() + w.chord(); z++) {
                    // Round tips: the tip's front and back corners left off, as a wing's tip curves in.
                    boolean corner = z == w.lead() || z == w.lead() + w.chord() - 1;
                    if (spec.round() && w.chord() >= 3 && Math.abs(x) == w.half() && corner) continue;
                    if (Math.abs(x) <= 1 && (y == 3 ? z < COCKPIT_END : z <= BAY)) continue;
                    // The upper wing's middle is a plank beam on the body behind the cockpit: a wing laid on top of a
                    // block leaves a gap, but its halves join the beam's sides at mid-height.
                    if (x == 0 && y == 3) c.putIfAbsent(new BlockPos(x, y, z), p.get("planks"));
                    c.putIfAbsent(new BlockPos(x, y, z), p.get("sail"));
                }
            }
        // A jet's tail: a flat stabiliser either side of its end and an upright fin on it (its flight computer pitches
        // and turns it with the thrusters). A propeller plane's tail, with its elevator, is laid out by controls().
        if (!spec.jet()) return;
        for (int x = -3; x <= 3; x++) if (x != 0) c.put(new BlockPos(x, 2, TAIL), p.get("stabiliser"));
        for (int y = 3; y <= 4; y++) c.put(new BlockPos(0, y, TAIL), p.get("fin"));
    }

    /** The row of the Rotation Speed Controller and the two drive engines either side of it, under the pilot's floor. */
    static final int RSC_ROW = 3;
    /** Where the Physics Assembler sits: under the floor behind the drive, below the step in. */
    static final BlockPos ASSEMBLER = new BlockPos(0, 0, RSC_ROW + 1);

    /**
     * The drive, all on the middle line, under the pilot's floor: a Rotation Speed Controller set to 256 RPM reversed
     * (-256, which turns the propeller the way that pulls it forward) takes three Portable Engines at 32 RPM, one facing
     * it from the right and two facing each other into a gearbox on its left, and its encased large cogwheel (in the floor
     * above it) turns an encased shaft forward to an Analog Transmission in the nose (the throttle), which hands the drive
     * up through its cogwheel to an encased cogwheel and a Propeller Bearing on the nose with a twelve-sail propeller.
     * Three engines give 6144 stress units; twelve sails at 256 RPM take exactly that and push 0.2 x 12^1.5 x 256 = 2128
     * (measured in a test world: 2128, every engine running, nothing overstressed). With two engines and eight sails it
     * pushed 1159: its wheels then topped out at about the speed its wings lifted it, and it crept off the ground; with
     * the third it is off in about 35 blocks, flies at 28 blocks/s, loops from level flight and rolls twice as fast (a
     * roll's rate grows with speed). Their weight low and forward balances the tail's; they are fuelled from the ground
     * beside the plane, and nothing of the drive is above the floor.
     */
    private static void drive(Wing w, Map<BlockPos, BlockState> c, Map<String, BlockState> p) {
        c.put(new BlockPos(0, 0, RSC_ROW), p.get("rsc"));
        c.put(new BlockPos(1, 0, RSC_ROW), p.get("engineWest"));
        c.put(new BlockPos(-1, 0, RSC_ROW), p.get("gearboxY"));
        c.put(new BlockPos(-1, 0, RSC_ROW - 1), p.get("engineSouth"));
        c.put(new BlockPos(-1, 0, RSC_ROW + 1), p.get("engineNorth"));
        c.put(new BlockPos(0, 1, RSC_ROW), p.get("largeEncZ"));
        for (int z = 1; z < RSC_ROW; z++) c.put(new BlockPos(0, 1, z), p.get("shaftEncZ"));
        c.put(new BlockPos(0, 1, 0), p.get("transmissionZ"));
        c.put(new BlockPos(1, 1, 0), p.get("linkEast"));
        c.put(new BlockPos(0, 2, 0), p.get("cogEncZ"));
        c.put(new BlockPos(0, 2, -1), p.get("propBearing"));
        // The propeller: a plank hub, a cross of four two-sail blades and a sail in each corner between them, twelve in a
        // five-wide disc in front of the bearing (its push counts sails, not their shape; a wider one would strike the
        // ground, its lowest sail being level with the wheels' axles).
        c.put(new BlockPos(0, 2, -2), p.get("hub"));
        for (int r = 1; r <= 2; r++) {
            c.put(new BlockPos(0, 2 + r, -2), p.get("blade"));
            c.put(new BlockPos(0, 2 - r, -2), p.get("blade"));
            c.put(new BlockPos(r, 2, -2), p.get("blade"));
            c.put(new BlockPos(-r, 2, -2), p.get("blade"));
        }
        for (int x : new int[] {-1, 1}) for (int y : new int[] {1, 3}) c.put(new BlockPos(x, y, -2), p.get("blade"));
        c.put(ASSEMBLER, p.get("assembler"));
        // Brakes: a Redstone Link on top of each main Wheel Mount brakes it while powered; one alone steers on the ground.
        c.put(new BlockPos(-1, 1, w.gear()), p.get("linkUp"));
        c.put(new BlockPos(1, 1, w.gear()), p.get("linkUp"));
    }

    /** The height of the control lines: between a biplane's decks, on top of a monoplane's wing. */
    static int lineY(Wing w) {
        return w.biplane() ? 2 : 3;
    }

    /**
     * The row the ailerons hinge on: just behind the wing's trailing edge. The ailerons are Symmetric Sails, which steer
     * hard but lift nothing, so they trail behind a whole wing instead of taking its last row (which cost a biplane a
     * seventh of its lift and pushed its take-off speed up past what its wheels reach).
     */
    static int aileronRow(Wing w) {
        return w.te() + 1;
    }

    /**
     * How far out each aileron's Swivel Bearing sits: the aileron is the wing's outer half. Every flat block of a wing
     * resists a roll in proportion to its distance out squared, so a big wing needs long ailerons to roll briskly.
     */
    static int aileronAt(Wing w) {
        return w.half() / 2;
    }

    /** The tail's half-width, middle to elevator tip. */
    static int tailHalf(Wing w) {
        return w.tail();
    }

    /**
     * Elevator and ailerons, no gyroscope: nothing holds the plane level, the pilot flies it. A third Portable Engine
     * behind the seats turns a gearbox at the wing's trailing edge; either side of it a Tilt Adapter (Create
     * Propulsion) turns its output shaft to the angle its two Redstone Links ask for (up to 45° each way at signal 15)
     * and holds it there. The shafts run out to a cogwheel over a Swivel Bearing near each wingtip whose glued strip of
     * Symmetric Sails is the aileron. The gearbox's back sends the same engine's drive down the spine to a second
     * gearbox and pair of Tilt Adapters at the tail, which swing the two halves of the elevator behind a fixed
     * stabiliser. Measured: at the wing, a signal on either Tilt Adapter's north link swings its aileron's trailing edge
     * down and one on its south link swings it up. The tail's gearbox takes the spine's straight-through drive and turns
     * the other way from the wing's, which reverses its adapters: there the north links swing the elevator's trailing
     * edges up (nose up) and the south links down.
     */
    private static void controls(Wing w, Map<BlockPos, BlockState> c, Map<String, BlockState> p) {
        int yl = lineY(w), za = aileronRow(w), xa = aileronAt(w), tr = w.tailRow();
        c.put(new BlockPos(0, yl, za - 1), p.get("engineSouth"));
        pair(c, p, yl, za);
        for (int s : new int[] {-1, 1}) {
            for (int x = 2; x < xa; x++) c.put(new BlockPos(s * x, yl, za), p.get("shaft"));
            c.put(new BlockPos(s * xa, yl, za), p.get("cog"));
            // A biplane's cogwheel, between its decks, turns a Swivel Bearing under it and one over it, so both wings
            // have ailerons: the same push on twice the surface, against the same wing's resistance, rolls it twice as fast.
            for (int y : w.biplane() ? new int[] {yl - 1, yl + 1} : new int[] {yl - 1}) {
                c.put(new BlockPos(s * xa, y, za), p.get(s < 0 ? "swivelWest" : "swivelEast"));
                // Two rows behind the wing's trailing edge, out from the bearing's face (the hinge at their front).
                for (int x = xa + 1; x <= w.half(); x++)
                    for (int z = za; z <= za + 1; z++) c.put(new BlockPos(s * x, y, z), p.get("flap"));
            }
        }
        // The spine: the gearbox's back drives an encased shaft aft along the boom's top to the tail.
        for (int z = za + 1; z < tr; z++) c.put(new BlockPos(0, yl, z), p.get("shaftEncZ"));
        // The tail: the boom runs under the spine, a fixed stabiliser either side ahead of the elevator, the elevator's
        // two halves on Swivel Bearings at the boom's end, and the fin on top.
        for (int z = BAY + 1; z <= tr; z++) for (int y = 1; y < yl; y++) c.putIfAbsent(new BlockPos(0, y, z), p.get("planks"));
        pair(c, p, yl, tr);
        int half = tailHalf(w), ys = yl - 1;
        for (int s : new int[] {-1, 1}) {
            for (int x = 1; x <= half; x++)
                for (int z = tr - 2; z < tr; z++) c.put(new BlockPos(s * x, ys, z), p.get("stabiliser"));
            c.put(new BlockPos(s, ys, tr), p.get("stabiliser"));
            c.put(new BlockPos(2 * s, yl, tr), p.get("cog"));
            c.put(new BlockPos(2 * s, ys, tr), p.get(s < 0 ? "swivelWest" : "swivelEast"));
            for (int x = 3; x <= half; x++) for (int z = tr; z <= tr + 1; z++) c.put(new BlockPos(s * x, ys, z), p.get("flap"));
        }
        for (int y = yl + 1; y <= yl + 2; y++) for (int z = tr - 2; z <= tr; z++) c.put(new BlockPos(0, y, z), p.get("fin"));
    }

    /** A gearbox fed from the north and a Tilt Adapter either side of it, each with a link north and south. */
    private static void pair(Map<BlockPos, BlockState> c, Map<String, BlockState> p, int y, int z) {
        c.put(new BlockPos(0, y, z), p.get("gearboxY"));
        c.put(new BlockPos(-1, y, z), p.get("tiltWest"));
        c.put(new BlockPos(1, y, z), p.get("tiltEast"));
        for (int s : new int[] {-1, 1}) {
            c.put(new BlockPos(s, y, z - 1), p.get("linkNorth"));
            c.put(new BlockPos(s, y, z + 1), p.get("linkSouth"));
        }
    }

    /**
     * Where a jet's two Smart Vector Thrusters stand: either side of the tail boom, just ahead of the stabiliser. The
     * computer pitches the plane by swinging them up or down, so they go far from the centre of mass: on the wing's
     * trailing edge, a block or so behind it, holding level took so much thrust that the plane ran away past its set
     * speed and climbed off.
     */
    private static BlockPos thrusterAt(Wing w, int side) {
        return new BlockPos(side, 2, TAIL - 1);
    }

    /**
     * A jet's works, in place of the propeller drive and gyroscope: one Portable Engine behind the seats turns a gearbox
     * tower whose top shafts turn the three controllers in a row (a Smart Attitude Stabilizer between a Velocity Coupler
     * and an Angular Velocity Coupler; linked to a computer each takes a flat 64 stress units at any speed). The Flight
     * Control Computer sits on the Physics Assembler and a Redstone Proxy behind the stabilizer, with Redstone Link
     * receivers on its west, east and top faces for the console's yaw left, yaw right and forward. Two Smart Vector
     * Thrusters, nozzles aft, stand on the wing's trailing edge either side of the body, each with a one-block Fluid Tank
     * beside it (a tank links to a thruster within 2 blocks). Measured: this arrangement on a 21-wide test wing held level
     * within 1.5 degrees at 28 blocks/s, turned 15 degrees a second flat and burned about 25 mB of lava a minute a side.
     */
    private static void jet(Wing w, Map<BlockPos, BlockState> c, Map<String, BlockState> p) {
        c.put(new BlockPos(0, 2, 6), p.get("engineSouth"));
        c.put(new BlockPos(0, 2, 7), p.get("gearboxX"));
        c.put(new BlockPos(0, 3, 7), p.get("gearboxZ"));
        c.put(new BlockPos(-1, 3, 7), p.get("gearboxZ"));
        c.put(new BlockPos(1, 3, 7), p.get("gearboxZ"));
        c.put(new BlockPos(-1, 4, 7), p.get("speedCoupler"));
        c.put(new BlockPos(0, 4, 7), p.get("stabilizer"));
        c.put(new BlockPos(1, 4, 7), p.get("turnCoupler"));
        c.put(new BlockPos(0, 2, 8), p.get("assembler"));
        c.put(new BlockPos(0, 3, 8), p.get("fcc"));
        c.put(new BlockPos(0, 4, 8), p.get("proxy"));
        c.put(new BlockPos(-1, 4, 8), p.get("linkWest"));
        c.put(new BlockPos(1, 4, 8), p.get("linkEast"));
        c.put(new BlockPos(0, 5, 8), p.get("linkUp"));
        for (int s : new int[] {-1, 1}) {
            BlockPos t = thrusterAt(w, s);
            c.put(t, p.get("thruster"));
            c.put(t.offset(s, 0, 0), p.get("tank"));
        }
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

    /** Tail half-widths tried, and how far behind the wing's trailing edge the tail stands, smallest first. */
    private static final int[] TAILS = {4, 5, 6, 7}, BACKS = {16, 17, 18, 19, 20, 21};

    /** The offset fromZero moves the layout's frame by. */
    private static BlockPos minus(Map<BlockPos, BlockState> cells) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        for (BlockPos q : cells.keySet()) {
            x0 = Math.min(x0, q.getX());
            y0 = Math.min(y0, q.getY());
            z0 = Math.min(z0, q.getZ());
        }
        return new BlockPos(-x0, -y0, -z0);
    }

    /**
     * The Rotation Speed Controller's set speed, in the model's frame (none on a jet): -256, which turns the propeller
     * the way that pulls the plane forward (measured: at 256 it pushed it backwards). The check takes only its size.
     */
    static Map<BlockPos, Float> speeds(Spec spec, BlockPos shift) {
        return spec.jet() ? Map.of() : Map.of(new BlockPos(0, 0, RSC_ROW).offset(shift), -256f);
    }

    /** A Redstone Link frequency: two of one item, so it never matters which of a link's two slots takes which. */
    record Freq(String item, String name) {
    }

    static final Freq THROTTLE = new Freq("minecraft:redstone", "Redstone"), LEFT = new Freq("minecraft:stick", "Sticks"),
            RIGHT = new Freq("minecraft:feather", "Feathers"), NOSE_UP = new Freq("minecraft:paper", "Paper"),
            NOSE_DOWN = new Freq("minecraft:string", "String"), LEFT_BRAKE = new Freq("minecraft:flint", "Flint"),
            RIGHT_BRAKE = new Freq("minecraft:iron_nugget", "Iron Nuggets");

    /** The Wheel Mounts' suspension: its lowest, since a mount drags in proportion to it until the plane lifts off. */
    static final int SUSPENSION = 5;

    /** Where the Wheel Mounts stand, in the layout's frame: under the nose, and either side just behind the centre of mass. */
    private static List<BlockPos> gear(Wing w) {
        return List.of(new BlockPos(0, 0, 1), new BlockPos(-1, 0, w.gear()), new BlockPos(1, 0, w.gear()));
    }

    /**
     * Every Redstone Link receiver and its frequency, in the layout's frame. Measured in a test world: the wing's Tilt
     * Adapters swing an aileron's trailing edge down on their north link's signal and up on the south one's, so the left
     * one's north link and the right one's south link roll it right; the tail's turn the other way round (its gearbox
     * takes the spine's drive straight through), so both north links raise the elevator's trailing edge, nose up. Each
     * main mount brakes while the link on top of it is powered, and one braked alone turns it that way on the ground
     * (about 7° a second at 40% power): parked, it sits back on its main wheels and tail, nose up, so its nose wheel is
     * off the ground and steering it (links at its sides) did nothing. The transmission stops the propeller at 15 and
     * passes full speed at 0.
     */
    static Map<BlockPos, Freq> receivers(Spec spec, Wing w) {
        Map<BlockPos, Freq> m = new LinkedHashMap<>();
        if (spec.jet()) {
            m.put(new BlockPos(-1, 4, 8), LEFT);
            m.put(new BlockPos(1, 4, 8), RIGHT);
            m.put(new BlockPos(0, 5, 8), THROTTLE);
            return m;
        }
        int yl = lineY(w), za = aileronRow(w), tr = w.tailRow(), g = w.gear();
        m.put(new BlockPos(1, 1, 0), THROTTLE);
        m.put(new BlockPos(-1, yl, za + 1), LEFT);
        m.put(new BlockPos(1, yl, za - 1), LEFT);
        m.put(new BlockPos(-1, yl, za - 1), RIGHT);
        m.put(new BlockPos(1, yl, za + 1), RIGHT);
        m.put(new BlockPos(-1, yl, tr - 1), NOSE_UP);
        m.put(new BlockPos(1, yl, tr - 1), NOSE_UP);
        m.put(new BlockPos(-1, yl, tr + 1), NOSE_DOWN);
        m.put(new BlockPos(1, yl, tr + 1), NOSE_DOWN);
        m.put(new BlockPos(-1, 1, g), LEFT_BRAKE);
        m.put(new BlockPos(1, 1, g), RIGHT_BRAKE);
        return m;
    }

    /** The plane's wiring in the model's frame, for its schematic: every link's frequency and each mount's suspension. */
    private static com.google.gson.JsonArray wiring(Spec spec, Wing w, BlockPos shift) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        receivers(spec, w).forEach((p, f) -> a.add(Wiring.link(p.offset(shift), f.item(), f.item())));
        for (BlockPos g : gear(w)) a.add(Wiring.value(g.offset(shift), SUSPENSION));
        return a;
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

    /** A jet's drive, cockpit and how to fly it, for finish(). */
    private static void jetText(StringBuilder s, Wing w, java.util.function.Function<BlockPos, String> at) {
        BlockPos left = thrusterAt(w, -1), right = thrusterAt(w, 1);
        s.append(String.format(Locale.ROOT, "- Drive: two Smart Vector Thrusters (Create: Flight Control) either side of the tail "
                + "boom (%s and %s), nozzles aft, each with a Smart Vector Thruster Nozzle installed "
                + "(right-click it with one) and a Fluid Tank beside it (%s and %s) filled with lava buckets (or TFMG diesel, "
                + "gasoline or kerosene, which last longer). Each pushes up to 6000, far more than this plane needs, and swings "
                + "its nozzle to steer and level it. A Flight Control Computer (%s, on the Physics Assembler) decides how hard "
                + "each pushes and where it points, from three controllers in a row behind the cockpit: a Velocity Coupler "
                + "(%s, the speed), a Smart Attitude Stabilizer (%s, keeps it level) and an Angular Velocity Coupler (%s, the "
                + "turns). One Portable Engine behind the seats turns a gearbox tower into their bottom shafts (each takes "
                + "only 64 stress units). No gyroscope: the computer levels it.\n", at.apply(left), at.apply(right),
                at.apply(left.offset(-1, 0, 0)), at.apply(right.offset(1, 0, 0)), at.apply(new BlockPos(0, 3, 8)),
                at.apply(new BlockPos(-1, 4, 7)), at.apply(new BlockPos(0, 4, 7)), at.apply(new BlockPos(1, 4, 7))));
        s.append(String.format(Locale.ROOT, "- Linking (the model can't hold links; tell the player each step, all with the "
                + "Flight Control Tool Gun): select a thruster, right-click the computer; the same for the other thruster, the "
                + "stabilizer and both couplers. Select each tank, right-click its thruster. Select each coupler, right-click "
                + "the Redstone Proxy (%s). Then empty-handed: open the proxy and map the face with the receiver on its west "
                + "side to the Angular Velocity Coupler's yaw left, the east one to its yaw right and the top one to the "
                + "Velocity Coupler's Forward Velocity; open the Velocity Coupler and set its forward max speed to %.0f; open "
                + "the Angular Velocity Coupler and set its yaw rate to 0.3; open the stabilizer and pick Level (no yaw) and "
                + "the Medium preset (Weak lets it sag nose down at speed); open the computer, set Default ON and press Apply "
                + "Thrust.\n", at.apply(new BlockPos(0, 4, 8)), VehicleCheck.JET_SPEED));
        s.append(String.format(Locale.ROOT, "- Cockpit: an Aeroworks Control Stand at %s in front of the pilot's seat. Mount a "
                + "Yoke Module and a Throttle Quadrant on it, then Shift + right-click the stand to set them; each direction has "
                + "two frequency slots, so put two of its item in them: the yoke's Turn Left two %s (the receiver at %s), its "
                + "Turn Right two %s (%s); one throttle lever's Forward two %s (%s, on top of the proxy), latched so it stays "
                + "where it is set. The schematic sets those three receivers to match (built by hand, give them the same "
                + "items). Right-click the stand with an empty hand to take the controls.\n", at.apply(new BlockPos(0, 2, 2)),
                LEFT.name(), at.apply(new BlockPos(-1, 4, 8)), RIGHT.name(), at.apply(new BlockPos(1, 4, 8)), THROTTLE.name(),
                at.apply(new BlockPos(0, 5, 8))));
        s.append("- Flying it: build it on open flat ground or a runway pointed into the open, glue it all with Super Glue and "
                + "use the Physics Assembler at " + at.apply(new BlockPos(0, 2, 8)) + " with the engine cold (assembled with "
                + "its shaft turning, controllers went missing), then do the linking above; light the engine, sit in the front seat, take the controls and push the throttle lever forward: the thrusters bring it "
                + "up to its set speed, it lifts and climbs level by itself. The yoke turns it flat and level (about 8° a "
                + "second at speed), and with the yoke centred it holds its heading. To come down, pull the throttle back: the thrusters stop, it slows and "
                + "sinks; push it forward again to level out. Once down, leave it back and it rolls to a stop.\n");
    }

    private static Airships.Layout finish(Spec spec, Try t) {
        // The cells start at 0 already; the layout's own frame had the fuselage's middle at x 0.
        Map<BlockPos, BlockState> out = t.cells;
        int[] size = size(out);
        int sx = size[0], sy = size[1], sz = size[2];
        BlockPos shift = t.shift;
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
        s.append(String.format(Locale.ROOT, "- A small %s for %s: a %s fuselage %d blocks long%s, %s of %d %s (%d wide, %d deep, "
                        + "%s tips); %d x %d x %d in all, nose toward -z.\n", w.biplane() ? "biplane" : "monoplane",
                spec.seats() == 1 ? "one" : spec.jet() ? "two (pilot in front, passenger behind)" : "two (passenger in front, pilot behind)", spec.wood().replace('_', ' '),
                w.tailRow() + 1, spec.jet() ? "" : " with the propeller on its nose", w.biplane() ? "two wings" : "one wing",
                wingBlocks, spec.copycat() ? "Copycat Wings (8m)" : spec.colour().replace('_', ' ') + " sails", 2 * w.half() + 1,
                w.chord(), spec.round() ? "round" : "square", sx, sy, sz));
        if (spec.copycat()) s.append("- Copycat Wings (8m): each is two Copycat Wings stacked in one block (cut a Wing on a "
                + "Mechanical Saw or stonecutter for a Copycat Wing, place one, then use a second on it), facing up; it lifts "
                + "half again as much as a sail for the same weight, which is why this plane is smaller than a sail-winged one. "
                + "Use any block on a placed wing to give it that block's look (white wool or concrete for a classic wing); "
                + "the look doesn't change its weight.\n");
        List<BlockPos> gear = gear(w);
        s.append(String.format(Locale.ROOT, "- Landing gear: three Wheel Mounts, one under the nose (%s) and two just behind "
                + "the centre of mass (%s and %s), each facing sideways: put a Tire in each (right-click the mount's open side "
                + "with it). Each mount's suspension is at %d, its lowest (the schematic sets it; built by hand, scroll it "
                + "down): a wheel drags in proportion to that setting until the plane lifts off, and at the default 10 the run "
                + "is far longer.%s Board on the left: a ladder on the plank there leads up to the gap in the cockpit's side.\n",
                at.apply(gear.get(0)), at.apply(gear.get(1)), at.apply(gear.get(2)), SUSPENSION, spec.jet() ? ""
                        : " Parked, it sits back on its main wheels and the end of its tail, nose up a little, so the nose wheel "
                        + "only touches when the nose comes down."));
        if (spec.jet()) jetText(s, w, at);
        else propText(s, spec, w, f, at);
        boolean met = t.run() <= SHORT_RUN && t.ceiling() >= CEILING;
        s.append(String.format(Locale.ROOT, "- Worked out: from a field at about y 70 it lifts off at about %.0f blocks/s after a "
                        + "run of about %.0f blocks, tops out near %.0f blocks/s, ceiling about y %.0f; after touching down, %s "
                        + "stops it in about %.0f blocks (the full check below has the exact figures).\n", f.getOrDefault("liftoff", 0.0),
                f.getOrDefault("run", 0.0), f.getOrDefault("top", 0.0), f.getOrDefault("ceiling", 0.0),
                spec.jet() ? "rolling on its wheels" : "braking", f.getOrDefault("stop", 0.0)));
        if (!spec.jet()) s.append(String.format(Locale.ROOT, "- Why this one: " + choices(spec) + "wings %d to %d blocks across, "
                + "3 to 6 deep, at several places along the fuselage, with tails of several sizes and lengths, were each judged "
                + "with the vehicle check (flight, balance, rolls, loops, take-off and landing runs, steering, the pilot's view "
                + "and reach, boarding), and this is the smallest that passes with a take-off run of %.0f blocks or less and a "
                + "ceiling of y %.0f or more%s. Its three drive engines give all the push it has (twelve sails at 256 RPM take "
                + "all their stress), so more height or speed comes from less weight or more wing, not more propeller. Keep the "
                + "engines, gear, wing and tail where they are when you revise it (from_draft): they set its balance; light trim "
                + "(paint, a pane, a banner) changes little; check every version.\n",
                2 * MIN_HALF + 1, 2 * MAX_HALF + 1, SHORT_RUN, CEILING, met ? "" : String.format(Locale.ROOT, " (none did both, so "
                        + "this is the one that climbs highest, run %.0f blocks, ceiling y %.0f: tell the player)", t.run(), t.ceiling())));
        else s.append(String.format(Locale.ROOT, "- Why this one: " + choices(spec) + "wings %d to %d blocks across at five places "
                + "along the fuselage were each judged with the vehicle check, and this is the smallest that passes with a take-off "
                + "run of %.0f blocks or less and a ceiling of y %.0f or more%s. Its two thrusters could push it far faster; its "
                + "speed is what the Velocity Coupler asks for, and more speed means more lift, so it climbs faster. Keep the "
                + "thrusters, gear and wing where they are when you revise it (from_draft); check every version.\n",
                2 * MIN_HALF + 1, 2 * MAX_HALF + 1, SHORT_RUN, CEILING, met ? "" : String.format(Locale.ROOT, " (none did both, so "
                        + "this is the one that climbs highest, run %.0f blocks, ceiling y %.0f: tell the player)", t.run(), t.ceiling())));
        s.append("- Open cockpit: above y 128 the wind chills whoever is aboard and above y 192 it freezes them, so tell the "
                + "player to sew Windproof Lining onto each armour piece before climbing high.\n");
        return new Airships.Layout(out, sx, sy, sz, speeds(spec, shift), s.toString(), wiring(spec, w, shift));
    }

    /** A propeller plane's drive, controls, wiring, cockpit and how to build and fly it, for finish(). */
    private static void propText(StringBuilder s, Spec spec, Wing w, Map<String, Double> f,
                                 java.util.function.Function<BlockPos, String> at) {
        int yl = lineY(w), za = aileronRow(w), xa = aileronAt(w), tr = w.tailRow();
        s.append(String.format(Locale.ROOT, "- Drive, all under the pilot's floor: three Portable Engines turn a Rotation Speed "
                        + "Controller (%s), one facing it from the right (%s) and two facing each other into a gearbox on its left "
                        + "(%s and %s into %s). The controller is set to -256 (256 reversed: its value panel scrolled below zero; "
                        + "the schematic sets it), which turns the propeller the way that pulls the plane forward. Its large "
                        + "cogwheel on top (%s) turns an encased shaft forward to an Analog Transmission in the nose (%s, the "
                        + "throttle), whose cogwheel turns the encased cogwheel above it and the Propeller Bearing (%s). The "
                        + "propeller is twelve sails in front of the bearing: a plank hub, four two-sail blades in a cross and a "
                        + "sail in each corner between them (a wider one would strike the ground). Twelve sails at 256 RPM take "
                        + "exactly the three engines' 6144 stress units and push about 2100; if it ever blows backwards, set the "
                        + "controller to 256 instead.\n", at.apply(new BlockPos(0, 0, RSC_ROW)), at.apply(new BlockPos(1, 0, RSC_ROW)),
                at.apply(new BlockPos(-1, 0, RSC_ROW - 1)), at.apply(new BlockPos(-1, 0, RSC_ROW + 1)),
                at.apply(new BlockPos(-1, 0, RSC_ROW)), at.apply(new BlockPos(0, 1, RSC_ROW)), at.apply(new BlockPos(0, 1, 0)),
                at.apply(new BlockPos(0, 2, -1))));
        String swivels = w.biplane() ? String.format(Locale.ROOT, "%s and %s under the wing, %s and %s over it",
                at.apply(new BlockPos(-xa, yl - 1, za)), at.apply(new BlockPos(xa, yl - 1, za)), at.apply(new BlockPos(-xa, yl + 1, za)),
                at.apply(new BlockPos(xa, yl + 1, za))) : String.format(Locale.ROOT, "%s and %s",
                at.apply(new BlockPos(-xa, yl - 1, za)), at.apply(new BlockPos(xa, yl - 1, za)));
        s.append(String.format(Locale.ROOT, "- Ailerons and elevator, flown by the pilot: a fourth Portable Engine behind the "
                        + "seats (%s) turns a gearbox at the wing's trailing edge (%s) with a Tilt Adapter on each side; their shafts "
                        + "run out along the wing to a cogwheel turning the Swivel Bearings (%s), and each bearing's strip of "
                        + "Symmetric Sails, two rows behind the wing, is an aileron. The gearbox's back drives an encased shaft "
                        + "along the boom to a second gearbox and pair of Tilt Adapters at the tail (%s), which swing the elevator's "
                        + "two halves on their Swivel Bearings (%s and %s) behind a fixed stabiliser; the fin stands on top. A Tilt "
                        + "Adapter turns its shaft to the strength of the Redstone Links on its sides (up to 45° each way at 15) "
                        + "and holds it there. Nothing holds it level, which is what lets it roll and loop; an Aeroworks Gyroscope "
                        + "would (full strength at 256 RPM, 16 stress units per RPM, so two more engines) but it would stop both, "
                        + "so it is only an add-on for a player who asks for a plane that flies itself.\n",
                at.apply(new BlockPos(0, yl, za - 1)), at.apply(new BlockPos(0, yl, za)), swivels, at.apply(new BlockPos(0, yl, tr)),
                at.apply(new BlockPos(-2, yl - 1, tr)), at.apply(new BlockPos(2, yl - 1, tr))));
        Map<Freq, List<String>> by = new LinkedHashMap<>();
        receivers(spec, w).forEach((p, q) -> by.computeIfAbsent(q, k -> new ArrayList<>()).add(at.apply(p)));
        java.util.function.Function<Freq, String> on = q -> "two " + q.name() + " (" + String.join(" and ", by.get(q)) + ")";
        s.append("- Wiring: every Redstone Link on it is a receiver, each set to two of one item. The schematic carries each "
                + "link's frequency, the controller's -256 and each mount's suspension, so a Schematicannon prints them set; "
                + "built by hand, set them as listed. Throttle " + on.apply(THROTTLE) + ", beside the transmission. Roll left "
                + on.apply(LEFT) + " and roll right " + on.apply(RIGHT) + ", either side of the wing's Tilt Adapters. Nose up "
                + on.apply(NOSE_UP) + ", in front of the tail's Tilt Adapters, and nose down " + on.apply(NOSE_DOWN) + ", behind "
                + "them. Left brake " + on.apply(LEFT_BRAKE) + " and right brake " + on.apply(RIGHT_BRAKE) + ", on top of the "
                + "main wheels.\n");
        s.append(String.format(Locale.ROOT, "- Cockpit: an Aeroworks Control Stand at %s at the front of the cockpit, in reach "
                + "of the pilot's seat at %s, the back one, as in an open-cockpit biplane (from the front seat the nose "
                + "propeller fills the middle of the view; %s). Mount a Joystick Module in its large socket, a Lever Module in a small one and a Pedal Module in the left and right "
                + "pedal sockets, then Shift + right-click the stand to set each one: every direction has two frequency slots, "
                + "so put two of its item in them. The stick: Left %s, Right %s, Back %s (pull back, nose up), Forward %s; it "
                + "follows the mouse and springs back to the middle when let go. The lever is the throttle: Forward %s, with "
                + "Range set to Forward Only and Swap Idle and Full Power pressed, so with the lever all the way back the "
                + "propeller stands still and pushed fully forward it runs flat out (it stays wherever it is left). The "
                + "pedals are the brakes, one per main wheel, as in a real light plane: left pedal Press %s, right %s. The "
                + "lever and the pedals all start on the W and S keys, so give each one keys of its own in its settings (R and "
                + "V for the lever, Z and C for the pedals, say). Right-click the stand with an empty hand to take the "
                + "controls.\n", at.apply(new BlockPos(0, 2, 2)), at.apply(new BlockPos(0, 2, PILOT)), spec.seats() > 1
                ? "the passenger sits there" : "that pit is left open for a passenger seat", LEFT.name(), RIGHT.name(), NOSE_UP.name(), NOSE_DOWN.name(),
                THROTTLE.name(), LEFT_BRAKE.name(), RIGHT_BRAKE.name()));
        int strips = w.biplane() ? 6 : 4;
        s.append(String.format(Locale.ROOT, "- Building it: on open flat ground or a runway pointed into the open, with every "
                + "engine cold. Glue it with Super Glue in boxes that cover all of it but the %d control surfaces (each Swivel "
                + "Bearing's strip of Symmetric Sails, out from its face), and give each strip a small box of its own: glued in "
                + "with the rest, a strip stays put when its bearing turns. Use the Physics Assembler at %s, then right-click "
                + "each Swivel Bearing with an empty hand to give it its strip. Only then fill the four engines with fuel, from "
                + "the ground beside it (a propeller already turning when it is assembled loses its blades). It lands to "
                + "refuel: a Portable Engine's one slot takes a whole stack, and a stack of Coal Blocks lasts for hours.\n",
                strips, at.apply(ASSEMBLER)));
        s.append(String.format(Locale.ROOT, "- Flying it: sit in the back seat and take the controls. Brakes off, lever fully "
                + "forward: the propeller spins up over a couple of seconds and it rolls, lifting off by itself after about "
                + "%.0f blocks (no back stick needed). At full power it keeps nosing up (left alone it is 45° nose up and "
                + "nearly stalled within 5 s), so once the wheels are off bring the lever back to a little past halfway "
                + "(about 60%% power) and hold a gentle climb with a touch of forward stick. At about 60%% it flies level at "
                + "about 15 blocks/s%s (flown level it needs more than the hands-off figure in the check below, which is "
                + "slow and nose high). Turn by banking: stick to the side rolls it into a lean, a touch of back stick "
                + "holds the height through the turn, and stick the other way rolls it level again; left alone, a lean stays "
                + "and slowly tightens, so the pilot keeps the wings level. Loops and full rolls: as the check below says, "
                + "with height to spare. To land, line up with the field, bring the lever back to about a quarter forward "
                + "and hold the nose a little down: it sinks about 4 blocks a second at about 10 blocks/s. Just above the "
                + "ground ease the stick back to touch down, then pull the lever right back and press both pedals. On the "
                + "ground it steers with its brakes: hold one pedal with a little power on and it turns that way (about 7° "
                + "a second at 40%% power).\n", f.getOrDefault("run", 0.0), spec.copycat() ? "" : " (measured on the Copycat-winged "
                + "one; this sail-winged one is heavier, so find its level power by eye)"));
    }
}
