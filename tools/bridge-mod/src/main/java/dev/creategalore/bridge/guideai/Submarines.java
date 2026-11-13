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
 * Small submarines laid out by the server (crew, beds, storage, hull, windows), so the book never wires one by hand.
 * The layout follows Deep Seas' rules as measured in a test pool:
 * <ul>
 * <li>It is a submarine only while an Oxygen Diffuser runs (a lever beside it and oxygen in it); otherwise it is a boat
 * that floods inside and tilts under water. An Electrolyzer on its own engine makes the oxygen from sea water: a pump
 * draws water from a Ballast Vent in the floor into it, another pumps its oxygen to the Diffuser. Neither may have a
 * block on top (they break themselves).</li>
 * <li>Ballast Tanks hold it at a depth by pushing through its centre of mass toward a climb or sink rate set by how
 * full they are; each drives about 75 mass, so it carries mass / 75 of them and no more (every extra tank is 8000 mB
 * more to pump before it dives). Surfaced, the tanks ride at the waterline, so they go in the roof and the hull and
 * propellers stay under. A Pump Controller on its own engine, piped to the tanks and a Ballast Vent, fills and empties
 * them; the Onboard Computer at the helm runs it to hold the depth the pilot sets.</li>
 * <li>Submarine Propellers in a 2x2 (or 3x3) square push as one when any one block of it is driven, 3 x side^4 x RPM,
 * along their axis by the way they spin; wrenched to reversed, this layout's push the bow ahead. One square each side,
 * each on its own Portable Engine through an Analog Transmission: the Throttle Lever slows both, and a comparator each
 * side of the Steering Wheel slows one side to turn (a Winds and Weather Rudder spun and tipped the test sub).</li>
 * <li>Full blocks, machines and shut trapdoors seal; the hatch is an iron trapdoor on the roof, with a ladder down.</li>
 * </ul>
 * Each candidate is judged with the vehicle check itself, and the smallest that passes is kept.
 */
final class Submarines {
    /** The fixed rows: props at 0, stern cap 1, hatch 3, life support 5 to 9, ballast pump 7 to 10, roof tanks from 8. */
    private static final int TANKS_FROM = 8, ROOMS_FROM = 11;

    private Submarines() {
    }

    /** What the player asked for. */
    record Spec(int crew, int beds, int storage, boolean iron, boolean windows, String colour, double speed) {
    }

    static JsonObject definition() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("crew", WorldTools.prop("integer", "1 to 4 aboard."));
        p.add("beds", WorldTools.prop("integer", "0 to 4."));
        p.add("storage", WorldTools.prop("integer", "Barrels, 0 to 8."));
        p.add("hull", WorldTools.prop("string", "copper (85 m) or iron (101 m)."));
        p.add("windows", WorldTools.prop("boolean", "Pressurized Glass window at the bow."));
        p.add("colour", WorldTools.prop("string", "Seat and bed dye."));
        p.add("speed", WorldTools.prop("number", "Wished blocks/s under water, 0 any."));
        o.add("properties", p);
        JsonArray req = new JsonArray();
        p.keySet().forEach(req::add);
        o.add("required", req);
        o.addProperty("additionalProperties", false);
        JsonObject a = new JsonObject();
        a.addProperty("type", "array");
        a.addProperty("description", "Empty, or one submarine the server lays out (hull, air, ballast, drive, helm, hatch) in place of layers and blocks.");
        a.add("items", o);
        return a;
    }

    static Spec spec(JsonObject o, List<String> problems) {
        int crew = Diagrams.intOr(o, "crew", 2), beds = Diagrams.intOr(o, "beds", 0), storage = Diagrams.intOr(o, "storage", 0);
        String hull = GuideText.string(o, "hull").strip().toLowerCase(Locale.ROOT);
        String colour = GuideText.string(o, "colour").strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace("grey", "gray");
        if (colour.isEmpty()) colour = "white";
        boolean windows = !o.has("windows") || o.get("windows").isJsonNull() || o.get("windows").getAsBoolean();
        double speed = o.has("speed") && o.get("speed").isJsonPrimitive() ? o.get("speed").getAsDouble() : 0;
        if (crew < 1 || crew > 4) problems.add("submarine crew must be 1 to 4");
        if (beds < 0 || beds > 4) problems.add("submarine beds must be 0 to 4");
        if (storage < 0 || storage > 8) problems.add("submarine storage must be 0 to 8 barrels");
        if (!hull.isEmpty() && !hull.equals("copper") && !hull.equals("iron")) problems.add("submarine hull must be copper or iron");
        if (!Airships.COLOURS.contains(colour)) problems.add("submarine colour must be one of " + String.join(", ", Airships.COLOURS));
        if (speed < 0 || speed > 8) problems.add("submarine speed must be 0 (any) to 8 blocks/s");
        return new Spec(crew, beds, storage, hull.equals("iron"), windows, colour, speed);
    }

    /** One tried shape: extra room rows, roof tank rows, propeller square side. */
    private record Shape(int extra, int tankRows, int props) {
        int bow() {
            return 16 + extra;
        }
    }

    private record Try(Shape shape, Map<BlockPos, BlockState> cells, List<String> faults, Map<String, Double> figures) {
    }

    /** Rows the rooms need beyond the two free ones every layout has (beds two long each on the left wall). */
    private static int roomRows(Spec spec) {
        int rows = Math.max(2 * spec.beds(), Math.max((spec.storage() + 1) / 2, spec.crew() / 2));
        return Math.max(0, rows - 2);
    }

    static Airships.Layout design(Spec spec, ServerLevel level, List<String> problems) {
        Map<String, BlockState> parts = new HashMap<>();
        String missing = parts(spec, parts);
        if (missing != null) {
            problems.add("the submarine layout needs " + missing + ", which this server doesn't have");
            return null;
        }
        String why = null;
        Try slow = null;
        int base = roomRows(spec);
        for (int extra = base; extra <= base + 3; extra++)
            for (int props : new int[] {2, 3})
                for (int rows = 1; TANKS_FROM + rows <= 16 + extra - 1; rows++) {
                    Shape s = new Shape(extra, rows, props);
                    Map<BlockPos, BlockState> cells = lay(spec, s, parts);
                    Map<BlockPos, Float> rpm = BuildCheck.rpm(cells, level, new ArrayList<>());
                    BuildCheck.Report r = VehicleCheck.check(cells, level, rpm, true, true);
                    if (r == null) continue;
                    Try t = new Try(s, cells, r.faults(), new HashMap<>(VehicleCheck.FIGURES.get()));
                    if (Boolean.getBoolean("creategalore.subs.debug"))
                        System.out.println("[subs] " + s + " " + t.figures + " " + t.faults);
                    if (!t.faults.isEmpty()) {
                        // Too few tanks is the cheap fault to fix: more rows next; keep the first other one to report.
                        if (why == null && t.faults.stream().noneMatch(f -> f.startsWith("too few Ballast Tanks"))) why = t.faults.get(0);
                        continue;
                    }
                    // With no speed asked, a brisk 4 blocks/s (the 3x3 squares: they turn it far better too).
                    double want = spec.speed() > 0 ? spec.speed() : 4;
                    if (t.figures.getOrDefault("subSpeed", 0.0) < want) {
                        if (slow == null || t.figures.getOrDefault("subSpeed", 0.0) > slow.figures.getOrDefault("subSpeed", 0.0)) slow = t;
                        continue;
                    }
                    return finish(spec, t, false);
                }
        if (slow != null) return finish(spec, slow, spec.speed() > 0);
        problems.add("no submarine layout passed the vehicle check" + (why == null ? "" : " (the smallest: " + why + ")"));
        return null;
    }

    private static String parts(Spec spec, Map<String, BlockState> out) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("hull", spec.iron() ? "minecraft:iron_block" : "minecraft:waxed_copper_block");
        raw.put("glass", spec.iron() ? "create_submarine:iron_pressurizer" : "create_submarine:copper_pressurizer");
        raw.put("tank", "create_submarine:ballast_tank");
        raw.put("vent", "create_submarine:ballast_vent");
        raw.put("prop", "create_submarine:submarine_propeller[facing=north,reversed=true]");
        raw.put("shaft", "create:andesite_encased_shaft[axis=z]");
        raw.put("cog", "create:cogwheel[axis=z]");
        raw.put("engine", "simulated:red_portable_engine[facing=north]");
        raw.put("transmission", "simulated:analog_transmission[axis=z]");
        raw.put("linkUp", "create:redstone_link[facing=up,receiver=true]");
        raw.put("linkDown", "create:redstone_link[facing=down,receiver=true]");
        raw.put("wheel", "simulated:steering_wheel[facing=north,on_floor=true]");
        raw.put("compWest", "minecraft:comparator[facing=east]");
        raw.put("compEast", "minecraft:comparator[facing=west]");
        raw.put("sendWest", "create:redstone_link[facing=west,receiver=false]");
        raw.put("sendEast", "create:redstone_link[facing=east,receiver=false]");
        raw.put("throttle", "simulated:throttle_lever[face=floor,facing=north,inverted=true]");
        raw.put("computer", "create_submarine:command_sub[facing=north]");
        raw.put("electrolyzer", "create_submarine:electrolyzer[facing=north,alternator=true]");
        raw.put("pumpSouth", "create:mechanical_pump[facing=south]");
        raw.put("pumpNorth", "create:mechanical_pump[facing=north]");
        raw.put("pipe", "create:fluid_pipe");
        raw.put("diffuser", "create_submarine:oxygene_diffuser");
        raw.put("lever", "minecraft:lever[face=floor,facing=north,powered=true]");
        raw.put("controller", "create_submarine:pump_controller[facing=north]");
        raw.put("ladder", "minecraft:ladder[facing=south]");
        raw.put("hatch", "minecraft:iron_trapdoor[half=bottom,open=false,facing=north]");
        raw.put("assembler", "simulated:physics_assembler[facing=north]");
        raw.put("bedFoot", "minecraft:" + spec.colour() + "_bed[part=foot,facing=south]");
        raw.put("bedHead", "minecraft:" + spec.colour() + "_bed[part=head,facing=south]");
        raw.put("barrel", "minecraft:barrel[facing=west]");
        raw.put("seat", "create:" + spec.colour() + "_seat");
        for (Map.Entry<String, String> e : raw.entrySet()) {
            BlockState s = Models.state(e.getValue());
            if (s == null && e.getKey().equals("seat")) s = Models.state("create:white_seat");
            if (s == null) return e.getValue();
            out.put(e.getKey(), s);
        }
        return null;
    }

    /** The cells: 9 wide (x 0 to 8), keel at y 0, floor 1, cabin 2 and 3, roof 4; propellers at z 0, bow toward +z. */
    private static Map<BlockPos, BlockState> lay(Spec spec, Shape s, Map<String, BlockState> p) {
        Map<BlockPos, BlockState> c = new LinkedHashMap<>();
        int bow = s.bow(), t = bow - 3;
        BlockState hull = p.get("hull");
        // The hull: keel, floor, walls and roof round a cabin 7 wide and 2 high; a stern cap and the bow with its window.
        for (int z = 2; z < bow; z++) {
            for (int x = 1; x <= 7; x++) {
                c.put(new BlockPos(x, 0, z), hull);
                c.put(new BlockPos(x, 1, z), hull);
                c.put(new BlockPos(x, 4, z), hull);
            }
            for (int y = 1; y <= 3; y++) {
                c.put(new BlockPos(0, y, z), hull);
                c.put(new BlockPos(8, y, z), hull);
            }
        }
        for (int x = 1; x <= 7; x++)
            for (int y = 0; y <= 3; y++) {
                c.put(new BlockPos(x, y, 1), hull);
                c.put(new BlockPos(x, y, bow), spec.windows() && x >= 2 && x <= 6 && y >= 2 ? p.get("glass") : hull);
            }
        // Ballast Tanks in the roof over the middle, rows from TANKS_FROM.
        for (int z = TANKS_FROM; z < TANKS_FROM + s.tankRows(); z++)
            for (int x = 2; x <= 6; x++) c.put(new BlockPos(x, 4, z), p.get("tank"));
        // Propellers: a square each side at the stern, its lowest row's inner block on the drive line.
        // Their middle level with the centre of mass (about 2.3 up): lower, they tip the bow up and drive it to the surface.
        int lo = s.props() == 2 ? 1 : 0;
        for (int x = lo; x < lo + s.props(); x++)
            for (int y = 1; y <= s.props(); y++) {
                c.put(new BlockPos(x, y, 0), p.get("prop"));
                c.put(new BlockPos(8 - x, y, 0), p.get("prop"));
            }
        // Drive: an engine each side at the helm end, its Analog Transmission's cogwheel turning the encased shaft that runs
        // along the floor to its square.
        for (int z = 1; z < t; z++) {
            c.put(new BlockPos(2, 1, z), p.get("shaft"));
            c.put(new BlockPos(6, 1, z), p.get("shaft"));
        }
        for (int side : new int[] {1, 7}) {
            int in = side == 1 ? 2 : 6;
            c.put(new BlockPos(side, 1, t + 1), p.get("engine"));
            c.put(new BlockPos(side, 1, t), p.get("transmission"));
            c.put(new BlockPos(in, 1, t), p.get("cog"));
            c.put(new BlockPos(side, 2, t), p.get("linkUp"));
            c.put(new BlockPos(side, 0, t), p.get("linkDown"));
        }
        // Helm: wheel ahead of the pilot with a comparator each side and their senders, throttle on the left, computer on
        // the right.
        c.put(new BlockPos(4, 2, bow - 1), p.get("wheel"));
        c.put(new BlockPos(3, 2, bow - 1), p.get("compWest"));
        c.put(new BlockPos(5, 2, bow - 1), p.get("compEast"));
        c.put(new BlockPos(2, 2, bow - 1), p.get("sendWest"));
        c.put(new BlockPos(6, 2, bow - 1), p.get("sendEast"));
        c.put(new BlockPos(3, 2, bow - 2), p.get("throttle"));
        c.put(new BlockPos(2, 2, bow - 2), p.get("sendWest"));
        c.put(new BlockPos(5, 2, bow - 2), p.get("computer"));
        // Life support on the right: engine, Electrolyzer, a cogwheel turning both pumps; water from a vent, oxygen to the
        // Diffuser with its lever beside it (nothing on top of either).
        c.put(new BlockPos(7, 2, 9), p.get("engine"));
        c.put(new BlockPos(7, 2, 8), p.get("electrolyzer"));
        c.put(new BlockPos(7, 2, 7), p.get("cog"));
        c.put(new BlockPos(6, 2, 7), p.get("pumpSouth"));
        for (BlockPos q : List.of(new BlockPos(6, 2, 8), new BlockPos(6, 2, 6), new BlockPos(5, 2, 6), new BlockPos(5, 1, 6)))
            c.put(q, p.get("pipe"));
        c.put(new BlockPos(5, 0, 6), p.get("vent"));
        c.put(new BlockPos(7, 1, 7), p.get("pumpNorth"));
        c.put(new BlockPos(7, 1, 8), p.get("pipe"));
        c.put(new BlockPos(7, 1, 6), p.get("pipe"));
        c.put(new BlockPos(7, 2, 6), p.get("diffuser"));
        c.put(new BlockPos(7, 2, 5), p.get("lever"));
        // Ballast on the left: engine, shaft, cogwheel under the Pump Controller; pipes down to a vent and up to the tanks.
        c.put(new BlockPos(1, 2, 10), p.get("engine"));
        c.put(new BlockPos(1, 2, 9), p.get("shaft"));
        c.put(new BlockPos(1, 2, 8), p.get("cog"));
        c.put(new BlockPos(1, 3, 8), p.get("controller"));
        for (BlockPos q : List.of(new BlockPos(1, 3, 9), new BlockPos(1, 4, 9), new BlockPos(1, 3, 7), new BlockPos(1, 2, 7),
                new BlockPos(1, 1, 7))) c.put(q, p.get("pipe"));
        c.put(new BlockPos(1, 0, 7), p.get("vent"));
        // Hatch: an iron trapdoor on the roof at the stern end, a ladder down to the floor, a lip round it.
        for (int y = 2; y <= 3; y++) c.put(new BlockPos(4, y, 2), hull);
        for (int y = 2; y <= 4; y++) c.put(new BlockPos(4, y, 3), p.get("ladder"));
        for (BlockPos q : List.of(new BlockPos(3, 5, 3), new BlockPos(5, 5, 3), new BlockPos(4, 5, 2), new BlockPos(4, 5, 4)))
            c.put(q, hull);
        c.put(new BlockPos(4, 5, 3), p.get("hatch"));
        c.put(new BlockPos(6, 2, 2), p.get("assembler"));
        // Rooms between the machinery and the helm: beds along the left wall, barrels along the right, seats in between.
        int z = ROOMS_FROM;
        for (int i = 0; i < spec.beds(); i++, z += 2) {
            c.put(new BlockPos(1, 2, z), p.get("bedFoot"));
            c.put(new BlockPos(1, 2, z + 1), p.get("bedHead"));
        }
        for (int i = 0; i < spec.storage(); i++) c.put(new BlockPos(7, 2 + i % 2, ROOMS_FROM + i / 2), p.get("barrel"));
        for (int i = 0; i < spec.crew() - 1; i++) c.put(new BlockPos(i % 2 == 0 ? 3 : 5, 2, ROOMS_FROM + i / 2), p.get("seat"));
        return c;
    }

    private static Airships.Layout finish(Spec spec, Try t, boolean slow) {
        Shape s = t.shape;
        Map<BlockPos, BlockState> out = t.cells;
        int bow = s.bow(), tr = bow - 3, sx = 9, sy = 6, sz = bow + 1;
        Map<String, Double> f = t.figures;
        long tanks = 5L * s.tankRows();
        StringBuilder b = new StringBuilder("Laid out by the server from submarine (for you: tell the player what it is and how to "
                + "build and use it):\n");
        b.append(String.format(Locale.ROOT, "- A submarine for %d: a %s hull %d x %d x %d (bow toward +z), a cabin 7 wide and 2 "
                        + "high%s%s%s; %d Ballast Tanks in the roof; a %dx%d Submarine Propeller square each side at the stern.\n",
                spec.crew(), spec.iron() ? "Block of Iron (rated 101 m)" : "waxed Block of Copper (rated 85 m)", sx, sy, sz,
                spec.beds() > 0 ? ", " + spec.beds() + " bed" + (spec.beds() == 1 ? "" : "s") : "",
                spec.storage() > 0 ? ", " + spec.storage() + " barrel" + (spec.storage() == 1 ? "" : "s") : "",
                spec.windows() ? ", a window of " + (spec.iron() ? "Iron Pressurized Glass (eight glass round an Industrial Iron "
                        + "Block)" : "Copper Pressurized Glass (eight glass round a Block of Copper)") + " at the bow, which holds far "
                        + "deeper than the hull (plain glass gives way at 28 m)" : ", no window", tanks, s.props(), s.props()));
        b.append("- Why it is a submarine: Deep Seas counts it as one only while the Oxygen Diffuser runs; until then it is a boat "
                + "that floods inside and tilts under water. The Electrolyzer at 7,2,8 (put a Shaft in its module slot, then switch "
                + "it on in its screen) makes oxygen from sea water on its own engine at 7,2,9: the pump at 6,2,7 draws water from "
                + "the Ballast Vent at 5,0,6, the pump at 7,1,7 sends the oxygen to the Diffuser at 7,2,6. Flip the Diffuser's lever "
                + "(7,2,5) on before diving. Nothing may go on top of the Electrolyzer or the Diffuser: they break themselves.\n");
        b.append(String.format(Locale.ROOT, "- Diving and surfacing: set a depth on the Onboard Computer at 5,2,%d; it runs the Pump "
                + "Controller at 1,3,8 (its own engine at 1,2,10), which pumps sea water between the Ballast Vent at 1,0,7 and the "
                + "tanks. Each tank lifts about 75 mass, so %d carry it; more would only make it slower to dive. On the computer's "
                + "fast setting it starts down about %.0f s after a depth is set from the surface, holds that depth, and comes back "
                + "up in under a minute. Surfaced, the tanks float at the waterline, so the hull and propellers stay under.\n",
                bow - 2, tanks, f.getOrDefault("subDiveStart", 0.0)));
        b.append(String.format(Locale.ROOT, "- Drive and helm: a Portable Engine each side (1,1,%d and 7,1,%d) turns an Analog "
                        + "Transmission whose cogwheel drives the encased shaft along the floor to that side's propeller square "
                        + "(one driven block turns the whole square). The propellers are wrenched to reversed so they push the "
                        + "bow ahead; if it backs away when built, wrench each square's blocks back. The pilot stands at 4,2,%d: "
                        + "Steering Wheel ahead (4,2,%d), Throttle Lever on the left (3,2,%d: pushed fully forward is full speed), "
                        + "computer on the right. Redstone Link frequencies (the same two items on a sender and its receivers): "
                        + "throttle Redstone + Redstone (sender 2,2,%d, receivers 1,0,%d and 7,0,%d under the transmissions); left "
                        + "turn Stick + Redstone (comparator side sender 2,2,%d to 1,2,%d); right turn Redstone + Stick (6,2,%d to "
                        + "7,2,%d). A full turn of the wheel stops one side, so it turns on the spot; a little turn eases one side.\n",
                tr + 1, tr + 1, bow - 2, bow - 1, bow - 2, bow - 2, tr, tr, bow - 1, tr, bow - 1, tr));
        b.append("- Boarding: the iron trapdoor on the roof at 4,5,3 is the hatch, above the waterline when surfaced; a ladder "
                + "leads down. Keep it shut under water. Fuel all four engines from inside before setting out.\n");
        b.append(String.format(Locale.ROOT, "- Worked out: about %.1f blocks/s under water, turning about %.0f°/s; it hangs %.1f° "
                        + "fore and aft under water. Build it on the water's edge or in shallow water, glue it all with Super Glue "
                        + "and use the Physics Assembler at 6,2,2.%s\n", f.getOrDefault("subSpeed", 0.0), f.getOrDefault("turn", 0.0),
                Math.abs(f.getOrDefault("subPitch", 0.0)), slow ? String.format(Locale.ROOT, " It is slower than the %.1f blocks/s "
                        + "asked for: the biggest propeller squares on Portable Engines give this much; tell the player.", spec.speed()) : ""));
        b.append("- Why this one: room rows for what was asked, then the fewest roof tanks and the smaller propeller squares that "
                + "pass the vehicle check (dives, surfaces, holds level, dry cabin, air, steering, throttle, reach). Keep the "
                + "machinery, tanks and propellers where they are when you revise it (from_draft); check every version.\n");
        return new Airships.Layout(out, sx, sy, sz, Map.of(), b.toString());
    }
}
