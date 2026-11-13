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
 * Helicopters laid out by the server (one to three seats, wood, colour), so the book never wires one by hand. The layout
 * follows what makes a rotor craft fly on Sable's and Aeronautics' rules, all measured on a test server:
 * <ul>
 * <li>Lift: one Gyroscopic Propeller Bearing facing up on a mast above the cabin, turning a rotor of Create sails. It
 * pushes 0.2 x sails^1.5 x RPM for 2 stress units per sail per RPM, so on the same engines a bigger, slower rotor lifts
 * more. Its push thins with the air like everything else, so the ceiling is where push x air pressure meets the weight,
 * and below it the craft hovers wherever the throttle balances its weight.</li>
 * <li>Staying upright: the gyroscopic bearing keeps its push pointing straight up (up to 12 degrees off its own axis)
 * whatever the craft does, and with the rotor well above the weight the craft hangs under it like a pendulum: kicked
 * to 14 degrees it rocked back level. A plain Propeller Bearing's push turns with the craft, and the same kick rolled
 * it over. Nothing else is needed to stay level, and Sable's rotors don't twist the body, so no anti-torque tail rotor
 * either.</li>
 * <li>Going places: the gyroscopic bearing keeps the rotor level, so tilting the craft doesn't drive it along as a real
 * helicopter's rotor does. A Wooden Propeller at the tail, geared up to 256 RPM and pushing level with the centre of
 * mass, drives it forward (the test craft cruised at 7 blocks a second on 96 RPM), and it hangs under a Swivel Bearing
 * that the Steering Wheel swings, which turns it, as on the airships.</li>
 * <li>Controls: the pilot sits behind the wheel with a Throttle Lever each side: the left one is the collective (an
 * Analog Transmission in the rotor's line: up, hover, down) and the right one the forward push (one in the tail
 * propeller's line).</li>
 * </ul>
 * Rotors of several sizes, two or four engines and a few tail lengths (the tail balances it fore and aft)
 * are each judged with the vehicle check itself (lift, ceiling, climb, balance at rest and at speed, steering, the
 * pilot's view and reach, boarding, refuelling), and the smallest that passes is kept.
 */
final class Helicopters {
    /** Ceiling wished for, y: over hills and most mountains, with room to climb. */
    private static final double CEILING = 160;
    /** Climb wished for, blocks a second, at full collective low down. */
    private static final double CLIMB = 2;
    /** Hangs this level or better at rest and at full speed, degrees. */
    private static final double LEVEL = 2.5;
    /** The rotor's RPM: a Portable Engine's own, straight through the transmission's cogwheel. */
    private static final int ROTOR_RPM = 32;
    /** Rows along the cabin: nose wall, helm, pilot, passengers, engines, chain drives, back wall. */
    private static final int Z_HELM = 1, Z_PILOT = 2, Z_SEATS = 3, Z_ENGINES = 4, Z_CHAIN = 5, Z_BACK = 6;
    /** Heights: skids, floor, the cabin's two rows, roof, the tail line, the rotor's bearing and its blades. */
    private static final int Y_FLOOR = 1, Y_ROOF = 4, Y_TAIL = 5, Y_BEARING = 7, Y_ROTOR = 8;
    /** The cabin's half width: walls at +-3, so the helm row has room for two throttles and their links. */
    private static final int HW = 3;
    /**
     * The tail's upright fin, sails high and long. It damps the yaw and turns the nose into the way it flies: with one of
     * 2 x 1 the test craft kept spinning up at full wheel and coasted half a turn after the wheel came back; with 2 x 3 it
     * settles at a steady turn within two seconds and stops within three of letting go.
     */
    private static final int FIN_HIGH = 2, FIN_LONG = 3;

    private Helicopters() {
    }

    /** What the player asked for. */
    record Spec(int seats, String wood, String colour) {
    }

    /** One layout tried: engines (2 or 4), rotor blade length and breadth, tail length (which balances it fore and aft). */
    record Knobs(int engines, int blade, boolean broad, int tail) {
        int sails() {
            return (broad ? 8 : 4) * blade;
        }

        int mastZ() {
            return Z_CHAIN;
        }

        /** The row of the tail's Swivel Bearing. */
        int swivelZ() {
            return 13 + tail;
        }
    }

    // ---------- the spec ----------

    static JsonObject definition() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("seats", WorldTools.prop("integer", "1-3."));
        p.add("wood", WorldTools.prop("string", "Wood."));
        p.add("colour", WorldTools.prop("string", "Dye."));
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
        if (seats < 1 || seats > 3) problems.add("helicopter seats must be 1 to 3");
        if (!Airships.WOODS.contains(wood)) problems.add("helicopter wood must be one of " + String.join(", ", Airships.WOODS));
        if (!Airships.COLOURS.contains(colour)) problems.add("helicopter colour must be one of " + String.join(", ", Airships.COLOURS));
        return new Spec(seats, wood, colour);
    }

    // ---------- the search ----------

    /** A tried layout: its knobs, cells (from zero), and the check's verdict and figures. */
    private record Try(Knobs k, Map<BlockPos, BlockState> cells, List<String> faults, Map<String, Double> figures, String text) {
        double get(String key) {
            return figures.getOrDefault(key, 0.0);
        }

        boolean meets() {
            return get("ceiling") >= CEILING && get("climb") >= CLIMB && level();
        }

        boolean level() {
            return Math.abs(get("hangPitch")) <= LEVEL && Math.abs(get("hangList")) <= LEVEL && Math.abs(get("cruisePitch")) <= LEVEL + 2;
        }

        /** How far it is from level, for picking among those that miss. */
        double tilt() {
            return Math.abs(get("hangPitch")) + Math.abs(get("hangList")) + 0.5 * Math.abs(get("cruisePitch"));
        }
    }

    /** Lays out the helicopter, or says why it can't (problems). Server thread: the check reads the block registry. */
    static Airships.Layout design(Spec spec, ServerLevel level, List<String> problems) {
        Map<String, BlockState> parts = new HashMap<>();
        String missing = parts(spec, parts);
        if (missing != null) {
            problems.add("the helicopter layout needs " + missing + ", which this server doesn't have");
            return null;
        }
        List<Knobs> knobs = new ArrayList<>();
        for (int engines : new int[] {2, 4})
            for (int blade = 3; blade <= 7; blade++)
                for (boolean broad : new boolean[] {false, true})
                    for (int tail = 0; tail <= 4; tail++) knobs.add(new Knobs(engines, blade, broad, tail));
        // Smallest first: fewest engines, then the fewest sails, then the shortest tail.
        knobs.sort(java.util.Comparator.comparingInt(Knobs::engines).thenComparingInt(Knobs::sails).thenComparingInt(Knobs::tail));
        Try best = null, nearest = null;
        String why = null;
        int sails = -1;
        for (Knobs k : knobs) {
            // Once a rotor size passes, only its other balancing layouts are worth trying.
            if (best != null && k.sails() != sails) break;
            Map<BlockPos, BlockState> laid = lay(spec, k, parts);
            // The rotor's disc must be clear: a fin or anything else up at the blades' height (or touching them from
            // below before they spin) is struck by them, and Sable reads it as joined to the ship through the blades.
            if (!rotorClear(laid, k)) continue;
            Map<BlockPos, BlockState> cells = fromZero(laid);
            int[] size = size(cells);
            VehicleCheck.closeSteps(cells, size[0], size[1], size[2]);
            Models.connect(cells, level);
            Map<BlockPos, Float> rpm = BuildCheck.rpm(cells, level, new ArrayList<>());
            BuildCheck.Report r = VehicleCheck.check(cells, level, rpm, true, false);
            if (r == null) continue;
            Try t = new Try(k, cells, new ArrayList<>(r.faults()), new HashMap<>(VehicleCheck.FIGURES.get()), r.text());
            // The rotation check the player's answer goes through too: a shaft ending against a part that doesn't take
            // rotation there is a PROBLEM in the answer, so a layout with one never goes out.
            BuildCheck.Report spin = BuildCheck.check(cells, level);
            if (spin != null) t.faults.addAll(0, spin.faults());
            if (Boolean.getBoolean("creategalore.helicopters.debug"))
                System.out.println("[helicopters] " + k + " " + t.figures + " " + t.faults);
            if (!t.faults.isEmpty()) {
                if (why == null) why = t.faults.get(0);
                continue;
            }
            if (t.meets()) {
                if (best == null || t.tilt() < best.tilt()) best = t;
                sails = k.sails();
                continue;
            }
            if (nearest == null || t.get("ceiling") > nearest.get("ceiling") + 1
                    || Math.abs(t.get("ceiling") - nearest.get("ceiling")) <= 1 && t.tilt() < nearest.tilt()) nearest = t;
        }
        boolean met = best != null;
        if (best == null) best = nearest;
        if (best == null) {
            problems.add("no helicopter layout passed the vehicle check" + (why == null ? "" : " (the smallest: " + why + ")"));
            return null;
        }
        return finish(spec, best, met);
    }

    /** Nothing but the rotor within its swept disc (and the row under it) out to a block past the blade tips. */
    private static boolean rotorClear(Map<BlockPos, BlockState> c, Knobs k) {
        double reach = k.blade() + (k.broad() ? 1.5 : 0.5) + 1;
        int zm = k.mastZ();
        for (BlockPos q : c.keySet()) {
            if (q.getY() < Y_ROTOR - 1 || q.getX() == 0 && q.getZ() == zm) continue;
            if (q.getY() == Y_ROTOR && isRotor(c.get(q))) continue;
            if (Math.hypot(q.getX(), q.getZ() - zm) <= reach) return false;
        }
        return true;
    }

    private static boolean isRotor(BlockState s) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return id.endsWith("_sail") && !id.endsWith("symmetric_sail") || id.equals("andesite_casing");
    }

    // ---------- the blocks ----------

    /** Looks up every block state the layout uses; the first one this server lacks, or null. */
    private static String parts(Spec spec, Map<String, BlockState> out) {
        String w = spec.wood(), trim = Airships.TRIM.getOrDefault(spec.colour(), spec.colour());
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("planks", "minecraft:" + w + "_planks");
        raw.put("slab", "minecraft:" + w + "_slab[type=bottom]");
        raw.put("skid", switch (w) {
            case "bamboo" -> "minecraft:stripped_bamboo_block[axis=z]";
            case "crimson", "warped" -> "minecraft:stripped_" + w + "_stem[axis=z]";
            default -> "minecraft:stripped_" + w + "_log[axis=z]";
        });
        raw.put("ladderWest", "minecraft:ladder[facing=west]");
        raw.put("ladderEast", "minecraft:ladder[facing=east]");
        raw.put("noseStairs", "minecraft:" + w + "_stairs[facing=south,half=bottom]");
        raw.put("paneX", "minecraft:glass_pane[east=true,west=true]");
        raw.put("paneZ", "minecraft:glass_pane[north=true,south=true]");
        raw.put("doorWestLow", "minecraft:" + w + "_door[facing=east,half=lower,hinge=left,open=false]");
        raw.put("doorWestHigh", "minecraft:" + w + "_door[facing=east,half=upper,hinge=left,open=false]");
        raw.put("doorEastLow", "minecraft:" + w + "_door[facing=west,half=lower,hinge=right,open=false]");
        raw.put("doorEastHigh", "minecraft:" + w + "_door[facing=west,half=upper,hinge=right,open=false]");
        raw.put("seat", "create:" + spec.colour() + "_seat");
        raw.put("wheel", "simulated:steering_wheel[facing=north]");
        raw.put("throttle", "simulated:throttle_lever[facing=north,inverted=true]");
        raw.put("linkOut", "create:redstone_link[facing=up,receiver=false]");
        raw.put("linkInWest", "create:redstone_link[facing=west,receiver=true]");
        raw.put("linkInUp", "create:redstone_link[facing=up,receiver=true]");
        raw.put("assembler", "simulated:physics_assembler[face=wall,facing=north]");
        raw.put("engine", "simulated:red_portable_engine[facing=south]");
        raw.put("chainZ", "create:encased_chain_drive[axis=z]");
        raw.put("gearboxX", "create:gearbox[axis=x]");
        raw.put("gearboxY", "create:gearbox[axis=y]");
        raw.put("gearboxZ", "create:gearbox[axis=z]");
        raw.put("transY", "simulated:analog_transmission[axis=y]");
        raw.put("transZ", "simulated:analog_transmission[axis=z]");
        raw.put("cogY", "create:cogwheel[axis=y]");
        raw.put("mastCog", "create:andesite_encased_cogwheel[axis=y,top_shaft=true,bottom_shaft=false]");
        raw.put("cogEncZ", "create:andesite_encased_cogwheel[axis=z,top_shaft=true,bottom_shaft=true]");
        raw.put("largeEncZ", "create:andesite_encased_large_cogwheel[axis=z,top_shaft=true,bottom_shaft=true]");
        raw.put("shaftY", "create:shaft[axis=y]");
        raw.put("shaftZ", "create:shaft[axis=z]");
        raw.put("encY", "create:andesite_encased_shaft[axis=y]");
        raw.put("encZ", "create:andesite_encased_shaft[axis=z]");
        raw.put("casing", "create:andesite_casing");
        raw.put("rotorBearing", "aeronautics:gyroscopic_propeller_bearing[facing=up]");
        raw.put("hub", "create:andesite_casing");
        raw.put("blade", "create:" + spec.colour() + "_sail[facing=up]");
        raw.put("bladeTip", "create:" + trim + "_sail[facing=up]");
        raw.put("swivel", "simulated:swivel_bearing[facing=down]");
        raw.put("prop", "aeronautics:wooden_propeller[facing=south]");
        raw.put("fin", "simulated:" + trim + "_symmetric_sail[axis=x]");
        raw.put("stabiliser", "simulated:" + trim + "_symmetric_sail[axis=y]");
        for (Map.Entry<String, String> e : raw.entrySet()) {
            BlockState s = Models.state(e.getValue());
            // An undyed sail or seat where the colour has none.
            if (s == null && (e.getKey().startsWith("blade") || e.getKey().equals("seat") || e.getKey().equals("fin")
                    || e.getKey().equals("stabiliser")))
                s = Models.state(e.getValue().replaceAll("(create|simulated):[a-z_]+?_(sail|seat|symmetric_sail)", "$1:white_$2"));
            if (s == null) return e.getValue();
            out.put(e.getKey(), s);
        }
        return null;
    }

    /**
     * The helicopter's cells, nose toward -z (north) at z 0, the cabin's middle at x 0, the skids at y 0.
     * <pre>
     * Cabin rows (z): 0 nose (sill, windscreen), 1 helm (link, collective, wheel, push throttle, link), 2 pilot (doors),
     * 3 passengers, 4 engines (and the rotor's gearbox), 5 chain drives, 6 back wall. Behind it the tail gearbox steps
     * the push line up to 256 RPM and to the boom's height, and the boom runs aft to the swivel and its propeller.
     * </pre>
     */
    static Map<BlockPos, BlockState> lay(Spec spec, Knobs k, Map<String, BlockState> p) {
        Map<BlockPos, BlockState> c = new LinkedHashMap<>();
        // Skids: two log rails under the cabin's floor, a block in from its sides.
        for (int z = 0; z <= Z_BACK; z++)
            for (int x : new int[] {-2, 2}) c.put(new BlockPos(x, 0, z), p.get("skid"));
        // The cabin: a plank floor, walls two high, a plank roof.
        for (int x = -HW; x <= HW; x++)
            for (int z = 0; z <= Z_BACK; z++) {
                c.put(new BlockPos(x, Y_FLOOR, z), p.get("planks"));
                c.put(new BlockPos(x, Y_ROOF, z), p.get("planks"));
            }
        for (int y = Y_FLOOR + 1; y < Y_ROOF; y++) {
            for (int z = 0; z <= Z_BACK; z++)
                for (int x : new int[] {-HW, HW}) c.put(new BlockPos(x, y, z), p.get("planks"));
            for (int x = -HW; x <= HW; x++) {
                c.put(new BlockPos(x, y, 0), p.get("planks"));
                c.put(new BlockPos(x, y, Z_BACK), p.get("planks"));
            }
        }
        // The windscreen over a sill, so the controls sit below the glass and the pilot sees out ahead; a band of
        // windows along each side, its jambs planks beside the doors.
        for (int x = -HW + 1; x <= HW - 1; x++) c.put(new BlockPos(x, Y_FLOOR + 2, 0), p.get("paneX"));
        for (int z = 1; z < Z_BACK; z++) {
            if (Math.abs(z - Z_PILOT) <= 1) continue;
            for (int x : new int[] {-HW, HW}) c.put(new BlockPos(x, Y_FLOOR + 2, z), p.get("paneZ"));
        }
        c.put(new BlockPos(-HW, Y_FLOOR + 1, Z_PILOT), p.get("doorWestLow"));
        c.put(new BlockPos(-HW, Y_FLOOR + 2, Z_PILOT), p.get("doorWestHigh"));
        c.put(new BlockPos(HW, Y_FLOOR + 1, Z_PILOT), p.get("doorEastLow"));
        c.put(new BlockPos(HW, Y_FLOOR + 2, Z_PILOT), p.get("doorEastHigh"));
        // A ladder on the floor's edge under each door: the doorstep is a block above the skids.
        c.put(new BlockPos(-HW - 1, Y_FLOOR, Z_PILOT), p.get("ladderWest"));
        c.put(new BlockPos(HW + 1, Y_FLOOR, Z_PILOT), p.get("ladderEast"));
        // The helm: the wheel ahead of the pilot, the collective's Throttle Lever on the left and the push's on the right,
        // each with its Redstone Link sender beside it; the wheel's shaft goes down into the floor and aft along it.
        int y1 = Y_FLOOR + 1;
        c.put(new BlockPos(0, y1, Z_HELM), p.get("wheel"));
        c.put(new BlockPos(-1, y1, Z_HELM), p.get("throttle"));
        c.put(new BlockPos(-2, y1, Z_HELM), p.get("linkOut"));
        c.put(new BlockPos(1, y1, Z_HELM), p.get("throttle"));
        c.put(new BlockPos(2, y1, Z_HELM), p.get("linkOut"));
        c.put(new BlockPos(0, y1, Z_PILOT), p.get("seat"));
        if (spec.seats() >= 2) c.put(new BlockPos(-2, y1, Z_SEATS), p.get("seat"));
        if (spec.seats() >= 3) c.put(new BlockPos(2, y1, Z_SEATS), p.get("seat"));
        c.put(new BlockPos(-1, Y_FLOOR, Z_PILOT), p.get("assembler"));
        // The wheel's line turns out to the side under the wheel and runs aft in the floor beside the middle, clear of
        // the rotor's gearbox standing on the floor.
        c.put(new BlockPos(0, Y_FLOOR, Z_HELM), p.get("gearboxZ"));
        c.put(new BlockPos(1, Y_FLOOR, Z_HELM), p.get("gearboxY"));
        for (int z = Z_HELM + 1; z <= Z_BACK; z++) c.put(new BlockPos(1, Y_FLOOR, z), p.get("encZ"));
        // The engine room: Portable Engines facing aft into a row of chain drives, which turn as one. The middle one
        // turns the rotor through a gearbox ahead of it (up to the collective's Analog Transmission, whose cogwheel
        // turns the mast's) and the push line through the back wall behind it.
        int span = k.engines() / 2;
        for (int x = -span; x <= span; x++) {
            if (x != 0) c.put(new BlockPos(x, y1, Z_ENGINES), p.get("engine"));
            c.put(new BlockPos(x, y1, Z_CHAIN), p.get("chainZ"));
        }
        c.put(new BlockPos(0, y1, Z_ENGINES), p.get("gearboxX"));
        c.put(new BlockPos(0, y1 + 1, Z_ENGINES), p.get("transY"));
        c.put(new BlockPos(-1, y1 + 1, Z_ENGINES), p.get("linkInWest"));
        // The mast: a cogwheel beside the transmission's (encased, with no shaft down into the chain drive under it), up
        // through the roof (cased, so the roof stays shut) to the gyroscopic bearing, the hub and the blades.
        int zm = k.mastZ();
        c.put(new BlockPos(0, y1 + 1, zm), p.get("mastCog"));
        c.put(new BlockPos(0, Y_ROOF, zm), p.get("encY"));
        for (int y = Y_TAIL; y < Y_BEARING; y++) c.put(new BlockPos(0, y, zm), p.get("shaftY"));
        c.put(new BlockPos(0, Y_BEARING, zm), p.get("rotorBearing"));
        BlockPos hub = new BlockPos(0, Y_ROTOR, zm);
        c.put(hub, p.get("hub"));
        for (int d = 1; d <= k.blade(); d++) {
            BlockState s = p.get(d == k.blade() ? "bladeTip" : "blade");
            c.put(hub.offset(d, 0, 0), s);
            c.put(hub.offset(-d, 0, 0), s);
            c.put(hub.offset(0, 0, d), s);
            c.put(hub.offset(0, 0, -d), s);
            if (k.broad()) {
                // Two sails broad, set round the hub like a propeller's blades.
                c.put(hub.offset(d, 0, 1), s);
                c.put(hub.offset(-d, 0, -1), s);
                c.put(hub.offset(-1, 0, d), s);
                c.put(hub.offset(1, 0, -d), s);
            }
        }
        // The push line: out through the back wall, the push throttle's Analog Transmission (its link on top), then
        // three large-to-small steps in encased cogwheels (32 to 256 RPM) up to the boom's height, a casing in each
        // step's corner so every part joins the next by a face.
        c.put(new BlockPos(0, y1, Z_BACK), p.get("encZ"));
        int zt = Z_BACK + 1;
        c.put(new BlockPos(0, y1, zt), p.get("transZ"));
        c.put(new BlockPos(0, y1 + 1, zt), p.get("linkInUp"));
        c.put(new BlockPos(-1, y1, zt), p.get("cogEncZ"));
        c.put(new BlockPos(-1, y1, zt + 1), p.get("largeEncZ"));
        c.put(new BlockPos(0, y1, zt + 1), p.get("casing"));
        c.put(new BlockPos(0, y1 + 1, zt + 1), p.get("cogEncZ"));
        c.put(new BlockPos(0, y1 + 1, zt + 2), p.get("largeEncZ"));
        c.put(new BlockPos(0, y1 + 2, zt + 2), p.get("casing"));
        c.put(new BlockPos(-1, y1 + 2, zt + 2), p.get("cogEncZ"));
        c.put(new BlockPos(-1, y1 + 2, zt + 3), p.get("largeEncZ"));
        c.put(new BlockPos(0, y1 + 2, zt + 3), p.get("casing"));
        c.put(new BlockPos(0, Y_TAIL, zt + 3), p.get("cogEncZ"));
        // The boom: the push line in encased shafts over a row of planks, to a gearbox over the Swivel Bearing.
        int zs = k.swivelZ();
        for (int z = zt + 4; z < zs; z++) {
            c.put(new BlockPos(0, Y_TAIL, z), p.get("encZ"));
            c.put(new BlockPos(0, Y_TAIL - 1, z), p.get("planks"));
        }
        c.put(new BlockPos(0, Y_TAIL, zs), p.get("gearboxX"));
        c.put(new BlockPos(0, Y_TAIL - 1, zs), p.get("swivel"));
        c.put(new BlockPos(0, Y_TAIL - 2, zs), p.get("gearboxX"));
        c.put(new BlockPos(0, Y_TAIL - 2, zs + 1), p.get("prop"));
        // The tail: the upright fin hangs under the boom ahead of the swivel, clear of the rotor's disc whatever the
        // tail's length, and a flat stabiliser either side of the boom. Nothing of the ship touches the gearbox under
        // the swivel but the swivel itself: Sable re-checks what holds together when that gearbox leaves with the
        // propeller, and a block touching it there could leave the swivel cut off from the boom.
        for (int y = Y_TAIL - 1 - FIN_HIGH; y < Y_TAIL - 1; y++)
            for (int z = zs - FIN_LONG - 1; z < zs - 1; z++) c.putIfAbsent(new BlockPos(0, y, z), p.get("fin"));
        // and a row on top of the boom at the very tail, one block under the blades' height
        for (int z = zs - FIN_LONG + 1; z <= zs; z++) c.put(new BlockPos(0, Y_TAIL + 1, z), p.get("fin"));
        for (int x : new int[] {-2, -1, 1, 2}) c.put(new BlockPos(x, Y_TAIL - 1, zs - 1), p.get("stabiliser"));
        // The wheel's line: out of the floor behind the cabin, up to the boom's height and aft beside it to a gearbox
        // over a cogwheel against the swivel's.
        c.put(new BlockPos(1, Y_FLOOR, zt), p.get("gearboxX"));
        for (int y = y1; y < Y_TAIL; y++) c.put(new BlockPos(1, y, zt), p.get("shaftY"));
        c.put(new BlockPos(1, Y_TAIL, zt), p.get("gearboxX"));
        for (int z = zt + 1; z < zs; z++) c.put(new BlockPos(1, Y_TAIL, z), p.get("shaftZ"));
        c.put(new BlockPos(1, Y_TAIL, zs), p.get("gearboxX"));
        c.put(new BlockPos(1, Y_TAIL - 1, zs), p.get("cogY"));
        return c;
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

    private static Airships.Layout finish(Spec spec, Try t, boolean met) {
        Map<BlockPos, BlockState> out = t.cells;
        int[] size = size(out);
        Knobs k = t.k;
        // The layout's own frame had the cabin's middle at x 0 and the nose at z 0; the rotor reaches past both.
        int half = Math.max(HW, k.blade());
        int front = Math.max(0, k.blade() - k.mastZ());
        BlockPos shift = new BlockPos(half, 0, front);
        java.util.function.Function<BlockPos, String> at = q -> {
            BlockPos m = q.offset(shift);
            return m.getX() + "," + m.getY() + "," + m.getZ();
        };
        int y1 = Y_FLOOR + 1, zt = Z_BACK + 1, zs = k.swivelZ();
        StringBuilder s = new StringBuilder("Laid out by the server from helicopter (for you: tell the player what it is and how "
                + "to build and fly it):\n");
        s.append(String.format(Locale.ROOT, "- A %s helicopter: a closed %s cabin 7 wide and 7 long on log skids, a %d-sail "
                        + "rotor %d blocks across on a mast above it, a tail boom to a pushing propeller; %d x %d x %d in all, nose toward -z.\n",
                spec.seats() == 1 ? "one-seat" : spec.seats() == 2 ? "two-seat" : "three-seat", spec.wood().replace('_', ' '),
                k.sails(), 2 * k.blade() + 1, size[0], size[1], size[2]));
        s.append(String.format(Locale.ROOT, "- Lift: a Gyroscopic Propeller Bearing facing up at %s turns the rotor (%d blades of %s "
                        + "sails, %d long%s, tips in the trim colour) at %d RPM. It keeps its push pointing straight up whatever the "
                        + "cabin does (up to 12 degrees off its own axis), so the cabin hangs under it like a pendulum and rights itself: "
                        + "that is what keeps a helicopter upright here, and a plain Propeller Bearing would roll it over. The rotor "
                        + "doesn't twist the cabin, so it needs no tail rotor against that.\n", at.apply(new BlockPos(0, Y_BEARING, k.mastZ())),
                4, spec.colour().replace('_', ' '), k.blade(), k.broad() ? ", two sails broad" : "", ROTOR_RPM));
        s.append(String.format(Locale.ROOT, "- Drive: %d Portable Engines in the cabin's back row, each facing aft into a row of "
                        + "encased chain drives that turn as one. The middle chain drive turns a gearbox ahead of it, up into an Analog "
                        + "Transmission whose cogwheel turns the mast's cogwheel (the collective), and a shaft aft through the back wall "
                        + "to a second transmission (the push), then three large-to-small steps of encased cogwheels up to 256 RPM and "
                        + "the boom's height, along the boom to a gearbox over the Swivel Bearing at the tail, which carries a gearbox "
                        + "and the Wooden Propeller pushing aft. The engines' %d stress units cover what the rotor and the propeller draw.\n",
                k.engines(), 2048 * k.engines()));
        s.append(String.format(Locale.ROOT, "- Controls, all at the pilot's seat %s: the Steering Wheel %s swings the tail propeller "
                        + "on its swivel (its shaft runs down into the floor, aft in it beside the middle, up behind the cabin to the boom and "
                        + "aft beside it to a gearbox over a cogwheel against the swivel's); the Throttle Lever on the left %s is the collective: forward climbs, back sinks, and in between it "
                        + "hovers; the one on the right %s sets the push forward. Redstone Link frequencies (two items each, the same on "
                        + "sender and receiver): collective Redstone + Redstone (sender %s, receiver on the rotor's transmission %s); push "
                        + "Stick + Redstone (sender %s, receiver on the push transmission %s).\n",
                at.apply(new BlockPos(0, y1, Z_PILOT)), at.apply(new BlockPos(0, y1, Z_HELM)), at.apply(new BlockPos(-1, y1, Z_HELM)),
                at.apply(new BlockPos(1, y1, Z_HELM)), at.apply(new BlockPos(-2, y1, Z_HELM)), at.apply(new BlockPos(-1, y1 + 1, Z_ENGINES)),
                at.apply(new BlockPos(2, y1, Z_HELM)), at.apply(new BlockPos(0, y1 + 1, zt))));
        s.append(String.format(Locale.ROOT, "- Worked out: it climbs about %.1f blocks/s at full collective low down, its ceiling "
                        + "is about y %.0f, it flies at about %.1f blocks/s and turns about %.0f°/s; it hangs %.1f° fore and aft and "
                        + "%.1f° side to side at rest, %.1f° at full speed (the full check below has the exact figures).\n",
                t.get("climb"), t.get("ceiling"), t.get("top"), t.get("turn"), Math.abs(t.get("hangPitch")), Math.abs(t.get("hangList")),
                Math.abs(t.get("cruisePitch"))));
        BlockPos bearing = new BlockPos(0, Y_BEARING, k.mastZ()), swivel = new BlockPos(0, Y_TAIL - 1, zs);
        s.append("- Building it: build it on flat ground with every engine off. Set the Gyroscopic Propeller Bearing at "
                + at.apply(bearing) + " to \"Push air when clockwise\" (its value panel, or click it with a wrench): the engines "
                + "turn it the way that, on the other setting, presses the helicopter into the ground. Glue it with Super Glue "
                + "in two boxes that leave out the gearbox and Wooden Propeller under the Swivel Bearing at " + at.apply(swivel)
                + " (one over everything ahead of the swivel, one over the tail from the swivel's height up), and give those "
                + "two their own small box; glued in with the rest they stay behind when the swivel takes its propeller. Then "
                + "use the Physics Assembler at " + at.apply(new BlockPos(-1, Y_FLOOR, Z_PILOT)) + ", and right-click the Swivel "
                + "Bearing with an empty hand to give it its propeller. Only then fuel and light the engines (a bearing already "
                + "turning when it is assembled loses its blades); the rotor spins up over a few seconds.\n");
        s.append("- Flying it: sit in the pilot's seat. Both levers give full power pushed all the way forward. Push the "
                + "collective forward until it lifts; it hovers with the collective pulled back about halfway near the ground "
                + "and only a notch or two back high up, where the air is thin. Push forward with the right lever and steer "
                + "with the wheel: at full wheel it swings round at about " + Math.round(t.get("turn")) + " degrees a second and stops turning a second or two "
                + "after the wheel comes back, but it slides on the way it was going for a few seconds (nothing grips the air "
                + "sideways), so steer early and expect to lose speed in a turn. To land, bring the push back and ease the "
                + "collective down. It rocks a little when it starts or stops pushing and settles on its own.\n");
        s.append(String.format(Locale.ROOT, "- Why this one: rotors of %d to %d blades' length, one or two sails broad, on two or "
                        + "four engines, and five tail lengths, were each judged "
                        + "with the vehicle check (lift, ceiling, climb, balance at rest and at speed, steering, the pilot's view and "
                        + "reach, boarding, refuelling); this is the smallest that passes, with a ceiling of y %.0f or more, a climb of "
                        + "%.0f blocks/s and hanging within %.1f°%s. Keep the engines, mast and tail where they are when you revise it "
                        + "(from_draft): the rotor must stay over the centre of mass; light trim changes little; check every version.\n",
                3, 7, CEILING, CLIMB, LEVEL, met ? "" : " (none did all of it, so this is the nearest: tell the player what it "
                        + "falls short of)"));
        return new Airships.Layout(out, size[0], size[1], size[2], Map.of(), s.toString());
    }
}
