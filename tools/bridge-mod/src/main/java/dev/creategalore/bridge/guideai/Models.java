package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The book's 3D models: a build the model writes as layers of palette characters and/or blocks and boxes, which the
 * book screen shows as a hologram the player can turn and zoom (client/ModelView). The server checks every block state
 * against this server's blocks, tries the machines in it (BuildCheck) and passes the model on as compact JSON (a
 * palette and runs), sharing the drawings' numbering so [diagram N] places it in an answer. A draft is not added to
 * the answer: the model gets pictures of it (Preview) to look at and fix first.
 */
public final class Models {
    public static final int MAX_SIZE = 32, MAX_ENTRIES = 256, MAX_LABELS = 12, MAX_PALETTE = 60;
    private static final int MIN_HOLDINGS = 24;

    private Models() {
    }

    static JsonObject definition() {
        JsonObject block = new JsonObject();
        block.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("block", WorldTools.prop("string", "A block state as in commands, such as create:shaft[axis=x], "
                + "minecraft:oak_stairs[facing=north,half=bottom] or minecraft:stone. Properties left out take the block's default. "
                + "minecraft:air clears cells that earlier entries filled."));
        p.add("x", WorldTools.prop("integer", "Cell x, 0 to size_x - 1 (x runs left to right as the player first sees it)."));
        p.add("y", WorldTools.prop("integer", "Cell y, 0 to size_y - 1 (y is up)."));
        p.add("z", WorldTools.prop("integer", "Cell z, 0 to size_z - 1 (z runs from the back towards the player; north is the back)."));
        p.add("x2", WorldTools.prop("integer", "With y2 and z2, the opposite corner of a box filled with this block; the same as x, y, z for one block."));
        p.add("y2", WorldTools.prop("integer", "See x2."));
        p.add("z2", WorldTools.prop("integer", "See x2."));
        block.add("properties", p);
        block.add("required", required(p));
        block.addProperty("additionalProperties", false);

        JsonObject label = new JsonObject();
        label.addProperty("type", "object");
        JsonObject lp = new JsonObject();
        lp.add("text", WorldTools.prop("string", "A few words, such as 'input' or 'to the press'."));
        lp.add("x", WorldTools.prop("integer", "The cell the label points at."));
        lp.add("y", WorldTools.prop("integer", "See x."));
        lp.add("z", WorldTools.prop("integer", "See x."));
        label.add("properties", lp);
        label.add("required", required(lp));
        label.addProperty("additionalProperties", false);

        JsonObject key = new JsonObject();
        key.addProperty("type", "object");
        JsonObject kp = new JsonObject();
        kp.add("key", WorldTools.prop("string", "One character that stands for this block in layers (not '.' or a space)."));
        kp.add("block", WorldTools.prop("string", "The block state it stands for, written as in blocks."));
        key.add("properties", kp);
        key.add("required", required(kp));
        key.addProperty("additionalProperties", false);

        JsonObject copy = new JsonObject();
        copy.addProperty("type", "object");
        JsonObject cp = new JsonObject();
        cp.add("x1", WorldTools.prop("integer", "World x of one corner of the box to copy, as trace_build, read_area or "
                + "find_placed_blocks give it."));
        cp.add("y1", WorldTools.prop("integer", "See x1."));
        cp.add("z1", WorldTools.prop("integer", "See x1."));
        cp.add("x2", WorldTools.prop("integer", "World x of the opposite corner."));
        cp.add("y2", WorldTools.prop("integer", "See x2."));
        cp.add("z2", WorldTools.prop("integer", "See x2."));
        cp.add("x", WorldTools.prop("integer", "The model cell the box's lowest corner (smallest x, y and z) goes to; 0 unless "
                + "you need room on that side for something new."));
        cp.add("y", WorldTools.prop("integer", "See x."));
        cp.add("z", WorldTools.prop("integer", "See x."));
        copy.add("properties", cp);
        copy.add("required", required(cp));
        copy.addProperty("additionalProperties", false);

        JsonObject row = new JsonObject();
        row.addProperty("type", "string");
        JsonObject layer = new JsonObject();
        layer.addProperty("type", "array");
        layer.add("items", row);

        JsonObject props = new JsonObject();
        props.add("title", WorldTools.prop("string", "A few words naming what the model shows, or an empty string."));
        props.add("size_x", WorldTools.prop("integer", "Width in blocks, 1 to " + MAX_SIZE + "; 0 with from_world to fit "
                + "the copied box."));
        props.add("size_y", WorldTools.prop("integer", "Height in blocks, 1 to " + MAX_SIZE + "; 0 with from_world to fit it."));
        props.add("size_z", WorldTools.prop("integer", "Depth in blocks, 1 to " + MAX_SIZE + "; 0 with from_world to fit it."));
        JsonObject fromWorld = new JsonObject();
        fromWorld.addProperty("type", "array");
        fromWorld.addProperty("description", "Empty, or one box of the player's world to copy into the model before layers "
                + "and blocks apply, so you can change a build of theirs instead of writing it again: the blocks players "
                + "placed in it, with the water and lava, the ground they touch and what was on the player's screen. Model "
                + "x, y and z follow the world's (x east, y up, z south), so a world block at (x1 + a, y1 + b, z1 + c) "
                + "lands at cell (x + a, y + b, z + c). Layers and blocks then write only the changes: a layer's '.' "
                + "leaves the copied cell as it is, and minecraft:air takes a block out. The changes are outlined on the "
                + "model for the player and listed back to you in world coordinates.");
        fromWorld.add("items", copy);
        props.add("from_world", fromWorld);
        JsonObject palette = new JsonObject();
        palette.addProperty("type", "array");
        palette.addProperty("description", "The characters layers use, at most " + MAX_PALETTE + "; empty when layers is.");
        palette.add("items", key);
        props.add("palette", palette);
        JsonObject layers = new JsonObject();
        layers.addProperty("type", "array");
        layers.addProperty("description", "The build drawn slice by slice, bottom (y = 0) first: each layer is a list of "
                + "rows from the back (z = 0) towards the player, each row a string with one palette character per cell "
                + "from x = 0 rightwards, '.' or a space for air. Rows and layers left out are air. The best way to write "
                + "a detailed build; can be empty when blocks says it all.");
        layers.add("items", layer);
        props.add("layers", layers);
        JsonObject blocks = new JsonObject();
        blocks.addProperty("type", "array");
        blocks.addProperty("description", "Single blocks and boxes, placed in order over the layers, later entries over earlier "
                + "ones. At most " + MAX_ENTRIES + "; boxes suit floors, walls and runs of shaft. Can be empty when layers says it all.");
        blocks.add("items", block);
        props.add("blocks", blocks);
        JsonObject labels = new JsonObject();
        labels.addProperty("type", "array");
        labels.addProperty("description", "Short labels floating at cells, at most " + MAX_LABELS + "; often none.");
        labels.add("items", label);
        props.add("labels", labels);
        props.add("caption", WorldTools.prop("string", "One short sentence under the model, or an empty string."));
        props.add("draft", WorldTools.prop("boolean", "True to try the model first: you get pictures of it (two corner "
                + "views, front and side) and the machine check, and nothing is added to the answer. Look, fix what looks "
                + "wrong, then call again with draft false to put it in the answer."));
        props.add("shows_a_fault", WorldTools.prop("boolean", "True only when the model is meant to show a build that "
                + "doesn't work (such as the player's own broken build); then it is built even when the rotation check finds faults."));
        props.add("part_of_vehicle", WorldTools.prop("boolean", "True only when the model shows one part of a vehicle (an "
                + "engine room, a rudder, a burner and its balloon) rather than the whole of it: the vehicle check's numbers "
                + "come back but don't stop it being built. Leave it false for a whole boat, submarine, airship or car."));
        return WorldTools.tool("build_model", "Builds a 3D model out of real blocks, up to " + MAX_SIZE + " a side, which "
                + "the book shows as a hologram the player can turn around and zoom into. Two uses. To explain (how machines "
                + "connect in space, which way blocks face): keep it to the blocks that matter. To show a build (a boat, a "
                + "house, a bridge, a tower) the player can copy or take ideas from: build it as a skilled Minecraft builder "
                + "would, at a size that gives it shape (a small sailing boat is about 12 to 20 blocks long), with stairs, "
                + "slabs, trapdoors, fences, walls and buttons for curves and detail, two or three materials that go "
                + "together and that the player holds where a block they hold would look as good (building_materials "
                + "counts them; the result lists what the model takes against what they hold), depth and trim instead of flat walls, and no plain boxes. Fences, walls, panes and bars join their "
                + "neighbours and stairs turn corners by themselves, as when placed. Write such builds as layers. Try a "
                + "build first with draft true and look at the pictures: fix whatever looks blocky, lopsided or wrong, "
                + "then call again with draft false. Before a model is shown the server tries its Create machines with "
                + "Create's own rules (which faces take a shaft, cogs, belts, sources, water on wheels) and refuses one "
                + "with faults, saying what is wrong, so fix it and call again. Each block's facing decides which faces "
                + "take rotation (a saw, press or drill takes its shaft at its back, the face opposite its facing): get it "
                + "right rather than guessing (lookup_recipes or read_guide help). Every block keeps the facing or axis you write "
                + "and takes its default when you leave it out, often the wrong way (a Windcatcher panel's default lies flat), "
                + "so write it on every block that has one. Sail rigs are checked the same way, with Winds & Weather's rules: "
                + "Masts stack straight up, Yards run sideways out from a Mast along their axis, Sail Kit cloth hangs straight "
                + "below a run of Yards facing across it, and Windcatcher panels stand upright with a sideways facing. Vehicles (a "
                + "boat, submarine, airship, plane or Offroad car) are worked out from their blocks, "
                + "with every engine at its usual speed: mass against buoyancy or lift, whether it floats upright or "
                + "capsizes, how it sits at rest (bow or stern down, leaning to a side) or hangs in the air, with how much "
                + "weight to move to level it, its centre of mass against its lift, thrust, top speed, turn rate, climb and ceiling, and "
                + "problems when it would sink, capsize, not lift, not move or turn, or be too slow or clumsy to be of use. Every "
                + "block of a vehicle must share a face with the rest, since Super Glue refuses blocks that touch only "
                + "diagonally: shape a hull's curves with stairs and slabs, not blocks stepped at their edges. "
                + "Whoever drives stands on the vehicle: a Boat Engine's helmsman stands two blocks out from the side it faces, "
                + "so it goes at the stern facing into the boat with deck under that spot, and every Steering Wheel, Throttle "
                + "Lever or lever needs a spot on board within reach to stand at. "
                + "Fix those before recommending it, and give the player its numbers. Water and lava can be shown flowing: a "
                + "source is level=0, flowing water level=1 to 7 (higher = further from the source), falling water "
                + "level=8. To improve or fix something the player built, copy it in with from_world (find its bounds with "
                + "trace_build or read_area first) and write only what changes; the player then sees their own build "
                + "with the changes outlined, and you get the changes in world coordinates to name in your answer. "
                + "It returns the model's number; write [diagram N] on a line of its own where it should appear, "
                + "or put it inside a drawing with a shape of kind model.", props);
    }

    private static JsonArray required(JsonObject properties) {
        JsonArray a = new JsonArray();
        properties.keySet().forEach(a::add);
        return a;
    }

    /**
     * The model as the book stores and shows it, or what to fix. Server thread (reads the block registry, and tries
     * the model's machines in a scratch copy of {@code level}).
     */
    static Result build(JsonObject input, ServerLevel level, Copier copier, net.minecraft.server.level.ServerPlayer player) {
        List<String> problems = new ArrayList<>();
        int sx = Diagrams.intOr(input, "size_x", 0), sy = Diagrams.intOr(input, "size_y", 0), sz = Diagrams.intOr(input, "size_z", 0);
        Map<BlockPos, BlockState> cells = new HashMap<>();
        // A build copied from the world: its blocks are the starting point, and what changes on them is marked.
        Map<BlockPos, BlockState> base = null;
        BlockPos worldMin = null, offset = null;
        JsonArray copies = array(input, "from_world");
        if (copies.size() > 1) return Result.problem("Not built: from_world takes one box at most.");
        if (copies.size() == 1 && copies.get(0).isJsonObject()) {
            JsonObject c = copies.get(0).getAsJsonObject();
            if (copier == null) return Result.problem("Not built: the player's world can't be read for this question.");
            Builds.Copy copy = copier.copy(new BlockPos(Diagrams.intOr(c, "x1", 0), Diagrams.intOr(c, "y1", 0), Diagrams.intOr(c, "z1", 0)),
                    new BlockPos(Diagrams.intOr(c, "x2", 0), Diagrams.intOr(c, "y2", 0), Diagrams.intOr(c, "z2", 0)), MAX_SIZE);
            if (copy.problem() != null) return Result.problem("Not built: " + copy.problem() + ".");
            offset = new BlockPos(Diagrams.intOr(c, "x", 0), Diagrams.intOr(c, "y", 0), Diagrams.intOr(c, "z", 0));
            worldMin = copy.min();
            int bx = Math.abs(Diagrams.intOr(c, "x2", 0) - Diagrams.intOr(c, "x1", 0)) + 1,
                    by = Math.abs(Diagrams.intOr(c, "y2", 0) - Diagrams.intOr(c, "y1", 0)) + 1,
                    bz = Math.abs(Diagrams.intOr(c, "z2", 0) - Diagrams.intOr(c, "z1", 0)) + 1;
            if (sx == 0) sx = offset.getX() + bx;
            if (sy == 0) sy = offset.getY() + by;
            if (sz == 0) sz = offset.getZ() + bz;
            if (offset.getX() < 0 || offset.getY() < 0 || offset.getZ() < 0 || offset.getX() + bx > sx || offset.getY() + by > sy
                    || offset.getZ() + bz > sz)
                return Result.problem("Not built: the copied " + bx + "x" + by + "x" + bz + " box placed at cell " + offset.getX() + " "
                        + offset.getY() + " " + offset.getZ() + " doesn't fit in a " + sx + "x" + sy + "x" + sz + " model.");
            base = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> e : copy.blocks().entrySet()) base.put(e.getKey().offset(offset), e.getValue());
            cells.putAll(base);
        }
        if (sx < 1 || sx > MAX_SIZE || sy < 1 || sy > MAX_SIZE || sz < 1 || sz > MAX_SIZE)
            return Result.problem("Not built: size_x, size_y and size_z must each be 1 to " + MAX_SIZE + ".");
        Map<Character, BlockState> keys = new HashMap<>();
        java.util.Set<String> undirected = new java.util.TreeSet<>();
        JsonArray palette = array(input, "palette");
        if (palette.size() > MAX_PALETTE) problems.add("at most " + MAX_PALETTE + " palette entries");
        for (int i = 0; i < Math.min(MAX_PALETTE, palette.size()) && problems.size() < 10; i++) {
            if (!palette.get(i).isJsonObject()) continue;
            JsonObject k = palette.get(i).getAsJsonObject();
            String key = GuideText.string(k, "key");
            if (key.length() != 1 || key.equals(".") || key.equals(" ")) {
                problems.add("palette entry " + i + ": key must be one character other than '.' and a space");
                continue;
            }
            BlockState state = parse(GuideText.string(k, "block"), "palette '" + key + "'", problems, undirected);
            if (state != null) keys.put(key.charAt(0), state);
        }
        JsonArray layers = array(input, "layers");
        if (layers.size() > sy) problems.add("layers has " + layers.size() + " layers but size_y is " + sy);
        for (int y = 0; y < Math.min(sy, layers.size()) && problems.size() < 10; y++) {
            JsonArray rows = layers.get(y).isJsonArray() ? layers.get(y).getAsJsonArray() : new JsonArray();
            if (rows.size() > sz) problems.add("layer " + y + " has " + rows.size() + " rows but size_z is " + sz);
            for (int z = 0; z < Math.min(sz, rows.size()) && problems.size() < 10; z++) {
                String r = rows.get(z).isJsonPrimitive() ? rows.get(z).getAsString() : "";
                if (r.length() > sx) problems.add("layer " + y + " row " + z + " is " + r.length() + " long but size_x is " + sx);
                for (int x = 0; x < Math.min(sx, r.length()); x++) {
                    char c = r.charAt(x);
                    if (c == '.' || c == ' ') continue;
                    BlockState state = keys.get(c);
                    if (state == null) {
                        if (problems.size() < 10) problems.add("layer " + y + " row " + z + ": '" + c + "' is not in the palette");
                    } else if (state.isAir()) {
                        cells.remove(new BlockPos(x, y, z));
                    } else {
                        cells.put(new BlockPos(x, y, z), state);
                    }
                }
            }
        }
        JsonArray in = array(input, "blocks");
        if (in.size() > MAX_ENTRIES) problems.add("use at most " + MAX_ENTRIES + " block entries");
        for (int i = 0; i < in.size() && i < MAX_ENTRIES && problems.size() < 10; i++) {
            if (!in.get(i).isJsonObject()) {
                problems.add("entry " + i + " is not an object");
                continue;
            }
            JsonObject b = in.get(i).getAsJsonObject();
            String raw = GuideText.string(b, "block").strip();
            BlockState state = parse(raw, "entry " + i, problems, undirected);
            if (state == null) continue;
            int x = Diagrams.intOr(b, "x", -1), y = Diagrams.intOr(b, "y", -1), z = Diagrams.intOr(b, "z", -1);
            int x2 = Diagrams.intOr(b, "x2", x), y2 = Diagrams.intOr(b, "y2", y), z2 = Diagrams.intOr(b, "z2", z);
            if (!inside(x, sx) || !inside(y, sy) || !inside(z, sz) || !inside(x2, sx) || !inside(y2, sy) || !inside(z2, sz)) {
                problems.add("entry " + i + " (" + raw + "): a corner is outside the " + sx + "x" + sy + "x" + sz + " model");
                continue;
            }
            for (BlockPos p : BlockPos.betweenClosed(x, y, z, x2, y2, z2)) {
                if (state.isAir()) cells.remove(p);
                else cells.put(p.immutable(), state);
            }
        }
        if (cells.isEmpty() && problems.isEmpty()) problems.add("the model has no blocks: fill layers or blocks");
        JsonArray labelsIn = array(input, "labels");
        if (labelsIn.size() > MAX_LABELS) problems.add("at most " + MAX_LABELS + " labels");
        JsonArray labels = new JsonArray();
        for (int i = 0; i < Math.min(MAX_LABELS, labelsIn.size()); i++) {
            JsonElement e = labelsIn.get(i);
            if (!e.isJsonObject()) continue;
            JsonObject l = e.getAsJsonObject();
            String text = GuideText.string(l, "text").strip();
            int x = Diagrams.intOr(l, "x", -1), y = Diagrams.intOr(l, "y", -1), z = Diagrams.intOr(l, "z", -1);
            if (text.isEmpty() || text.length() > 40) problems.add("label " + i + ": text must be 1 to 40 characters");
            else if (!inside(x, sx) || !inside(y, sy) || !inside(z, sz)) problems.add("label " + i + " is outside the model");
            else {
                String part = missingPart(text, cells, new BlockPos(x, y, z));
                if (part != null) problems.add("label " + i + " ('" + text + "') names " + part + ", but no block near "
                        + x + "," + y + "," + z + " is one (" + describeCell(cells, new BlockPos(x, y, z)) + "): point it at the "
                        + "real part, build the part in, or drop the label; a label only names what the model really has");
                JsonObject o = new JsonObject();
                o.addProperty("text", text);
                o.addProperty("x", x);
                o.addProperty("y", y);
                o.addProperty("z", z);
                labels.add(o);
            }
        }
        if (GuideText.string(input, "title").length() > 60) problems.add("title longer than 60 characters");
        if (GuideText.string(input, "caption").length() > 240) problems.add("caption longer than 240 characters");
        if (!problems.isEmpty()) return Result.problem("Not built. Fix these and call again: " + String.join("; ", problems) + ".");
        // What the model changes on the copied build, before blocks join up (a fence that now meets a new post isn't a change).
        Map<BlockPos, Boolean> changes = new HashMap<>();
        if (base != null) {
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet())
                if (!e.getValue().equals(base.get(e.getKey()))) changes.put(e.getKey(), false);
            for (BlockPos p : base.keySet()) if (!cells.containsKey(p)) changes.put(p, true);
        }
        if (level != null) connect(cells, level);
        BuildCheck.Report check = join(level == null ? null : BuildCheck.check(cells, level), RigCheck.check(cells));
        check = join(check, vehicle(cells, level, input.has("part_of_vehicle") && input.get("part_of_vehicle").isJsonPrimitive()
                && input.get("part_of_vehicle").getAsBoolean()));
        boolean showsFault = input.has("shows_a_fault") && input.get("shows_a_fault").isJsonPrimitive()
                && input.get("shows_a_fault").getAsBoolean();
        boolean draft = input.has("draft") && input.get("draft").isJsonPrimitive() && input.get("draft").getAsBoolean();
        if (check != null && !check.faults().isEmpty() && !showsFault && !draft) {
            return Result.problem("Not built: blocks in it wouldn't work as placed. Fix the model and call again (or "
                    + "set shows_a_fault if it is meant to show a broken build).\n" + check.text());
        }
        JsonObject model = new JsonObject();
        model.addProperty("type", "model");
        model.addProperty("title", GuideText.string(input, "title"));
        model.addProperty("caption", GuideText.string(input, "caption"));
        JsonArray size = new JsonArray();
        size.add(sx);
        size.add(sy);
        size.add(sz);
        model.add("size", size);
        compact(cells, sx, sy, sz, model);
        model.add("labels", labels);
        String report = check == null ? "" : check.text();
        report = (report.isEmpty() ? "" : report + "\n") + tally(cells);
        // A build to copy, not a few machines explaining a point: what it takes of the player's own materials.
        String holdings = cells.size() < MIN_HOLDINGS || level == null ? null : Materials.against(cells, player, level.getServer());
        if (holdings != null) report += "\n" + holdings;
        if (!undirected.isEmpty()) report += "\nWritten without a facing or axis, so they took their default (check each is "
                + "meant that way): " + String.join("; ", undirected) + ".";
        if (base != null) {
            model.add("changes", changeRuns(changes, sx, sy, sz));
            report = (report.isEmpty() ? "" : report + "\n") + changeReport(changes, cells, base, worldMin.subtract(offset), base.size());
        }
        byte[] picture = draft ? Preview.render(cells, sx, sy, sz) : null;
        return new Result(model, null, report, draft, picture);
    }

    /**
     * Joins the blocks to their neighbours the way placing them in the world would: fences, walls, panes and bars
     * connect, stairs turn corners, chests pair up. A block the update would remove (a torch with nothing to stand on)
     * stays as written.
     */
    private static void connect(Map<BlockPos, BlockState> cells, ServerLevel level) {
        try {
            SchematicLevel world = new SchematicLevel(level);
            cells.forEach((pos, state) -> world.setBlock(pos, state, 0));
            for (int pass = 0; pass < 2; pass++) {
                for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockState state = world.getBlockState(pos);
                    for (Direction d : Direction.values()) {
                        BlockPos next = pos.relative(d);
                        BlockState updated = state.updateShape(d, world.getBlockState(next), world, pos, next);
                        if (!updated.isAir()) state = updated;
                    }
                    world.setBlock(pos, state, 0);
                }
            }
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) e.setValue(world.getBlockState(e.getKey()));
        } catch (RuntimeException | LinkageError e) {
            // Without Create's scratch level, or if a block's update fails, the blocks stay as written.
        }
    }

    /**
     * Words a label uses for a working part, and the blocks that are that part: a label with one of them has to point at
     * (or right next to) such a block, so the book can't name a cannon, engine or rudder the model doesn't have.
     */
    private static final Map<String, java.util.function.Predicate<String>> PARTS = new java.util.LinkedHashMap<>();

    static {
        java.util.function.Predicate<String> cannon = id -> id.startsWith("createbigcannons:") || id.contains("cannon");
        PARTS.put("cannon", cannon);
        PARTS.put("gun", cannon);
        PARTS.put("keel", id -> id.contains("keel"));
        PARTS.put("rudder", id -> id.contains("rudder"));
        PARTS.put("sail", id -> id.contains("sail") || id.contains("windcatcher"));
        PARTS.put("engine", id -> id.contains("engine"));
        PARTS.put("motor", id -> id.contains("engine") || id.contains("motor"));
        PARTS.put("tiller", id -> id.equals("create_high_seas:boat_engine"));
        PARTS.put("helm", id -> id.equals("create_high_seas:boat_engine") || id.contains("steering_wheel"));
        PARTS.put("propeller", id -> id.contains("propeller"));
        PARTS.put("anchor", id -> id.contains("anchor"));
        PARTS.put("mast", id -> id.contains("mast"));
        PARTS.put("burner", id -> id.contains("burner"));
        PARTS.put("thruster", id -> id.contains("thruster"));
        PARTS.put("assembler", id -> id.contains("assembler"));
        PARTS.put("seat", id -> id.contains("seat") || id.contains("bench") || id.contains("chair") || id.contains("stair"));
        PARTS.put("vane", id -> id.contains("vane"));
        PARTS.put("winch", id -> id.contains("winch"));
        java.util.function.Predicate<String> storage = id -> id.contains("chest") || id.contains("barrel") || id.contains("vault");
        PARTS.put("chest", storage);
        PARTS.put("barrel", storage);
        PARTS.put("furnace", id -> id.contains("furnace") || id.contains("smoker"));
        PARTS.put("lever", id -> id.contains("lever"));
    }

    /**
     * The part a label's words name when no block near its cell is that part, else null. Near is within two blocks, or
     * anywhere straight above or below the cell and its neighbours: a label on a roof or deck honestly names the barrels
     * or engine under it, though the label can only point at the outside.
     */
    private static String missingPart(String text, Map<BlockPos, BlockState> cells, BlockPos at) {
        for (String w : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+")) {
            String word = w.endsWith("s") && PARTS.containsKey(w.substring(0, w.length() - 1)) ? w.substring(0, w.length() - 1) : w;
            java.util.function.Predicate<String> is = PARTS.get(word);
            if (is == null) continue;
            boolean found = false;
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                BlockPos q = e.getKey();
                int dx = Math.abs(q.getX() - at.getX()), dy = Math.abs(q.getY() - at.getY()), dz = Math.abs(q.getZ() - at.getZ());
                if ((dx <= 1 && dz <= 1 || dx <= 2 && dy <= 2 && dz <= 2)
                        && is.test(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString())) {
                    found = true;
                    break;
                }
            }
            if (!found) return "a " + word;
        }
        return null;
    }

    private static String describeCell(Map<BlockPos, BlockState> cells, BlockPos at) {
        BlockState s = cells.get(at);
        return s == null ? "that cell is empty" : "that cell holds " + s.getBlock().getName().getString();
    }

    private static final java.util.Set<String> DIRECTIONS = java.util.Set.of("facing", "axis", "rotation");

    private static <T extends Comparable<T>> String name(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    /**
     * The vehicle check on a model, with every engine turning at its usual speed. A model of only part of a vehicle
     * (an engine room, a rudder) still gets the numbers, but they don't stop it being built.
     */
    private static BuildCheck.Report vehicle(Map<BlockPos, BlockState> cells, ServerLevel level, boolean part) {
        List<String> assumed = new ArrayList<>();
        Map<BlockPos, Float> rpm = level == null ? Map.of() : BuildCheck.rpm(cells, level, assumed);
        BuildCheck.Report r = VehicleCheck.check(cells, level, rpm, true);
        if (r == null) return null;
        String text = r.text();
        int nl = text.indexOf('\n');
        if (!assumed.isEmpty() && nl > 0)
            text = text.substring(0, nl + 1) + "- Speeds worked out with " + String.join(", ", assumed) + ".\n" + text.substring(nl + 1);
        if (!part) return new BuildCheck.Report(text, r.faults());
        return new BuildCheck.Report(text.replace("- PROBLEM: ", "- As a whole vehicle these blocks alone would fail: ")
                + "\n- (Marked part_of_vehicle: check the whole vehicle with its own model before recommending it.)", List.of());
    }

    /** Both checks' reports as one; either may be null (nothing of its kind in the model). */
    private static BuildCheck.Report join(BuildCheck.Report a, BuildCheck.Report b) {
        if (a == null || b == null) return a == null ? b : a;
        List<String> faults = new ArrayList<>(a.faults());
        faults.addAll(b.faults());
        return new BuildCheck.Report(a.text() + "\n" + b.text(), faults);
    }

    /** What the model is made of, so a block that should have gone (or never come) shows up in the numbers. */
    private static String tally(Map<BlockPos, BlockState> cells) {
        Map<String, Integer> count = new java.util.TreeMap<>();
        for (BlockState s : cells.values()) count.merge(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        count.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue())
                .forEach(e -> parts.add(e.getValue() + " " + e.getKey()));
        return "Blocks in the model: " + String.join(", ", parts) + ".";
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static BlockState parse(String raw, String where, List<String> problems, java.util.Set<String> undirected) {
        try {
            BlockStateParser.BlockResult parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), raw.strip(), false);
            BlockState state = parsed.blockState();
            // A direction left out takes the block's default, which is often not what was meant (a flat sail panel).
            for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
                if (DIRECTIONS.contains(prop.getName()) && !parsed.properties().containsKey(prop))
                    undirected.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " (" + prop.getName() + "=" + name(state, prop) + ")");
            }
            return state;
        } catch (CommandSyntaxException e) {
            problems.add(where + ": " + raw + " is not a block state on this server (" + e.getRawMessage().getString() + ")");
            return null;
        }
    }

    /**
     * The cells as the book stores them: a palette of states and runs along x as [palette index, x, y, z, x2, y2, z2],
     * which keeps a detailed build small enough for a page.
     */
    private static void compact(Map<BlockPos, BlockState> cells, int sx, int sy, int sz, JsonObject model) {
        Map<BlockState, Integer> index = new HashMap<>();
        JsonArray palette = new JsonArray(), blocks = new JsonArray();
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    BlockState state = cells.get(new BlockPos(x, y, z));
                    if (state == null) continue;
                    int end = x;
                    while (end + 1 < sx && state.equals(cells.get(new BlockPos(end + 1, y, z)))) end++;
                    Integer i = index.get(state);
                    if (i == null) {
                        i = index.size();
                        index.put(state, i);
                        palette.add(BlockStateParser.serialize(state));
                    }
                    JsonArray run = new JsonArray();
                    for (int v : new int[] {i, x, y, z, end, y, z}) run.add(v);
                    blocks.add(run);
                    x = end;
                }
        model.add("palette", palette);
        model.add("blocks", blocks);
    }

    /** Reads a box of the asking player's world for from_world (Builds.copy, with their view and the placed blocks). */
    interface Copier {
        Builds.Copy copy(BlockPos a, BlockPos b, int maxSide);
    }

    /** Changed cells as runs along x: [x, y, z, x2, y2, z2, kind], kind 0 for a block added or replaced, 1 for one taken out. */
    private static JsonArray changeRuns(Map<BlockPos, Boolean> changes, int sx, int sy, int sz) {
        JsonArray runs = new JsonArray();
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    Boolean removed = changes.get(new BlockPos(x, y, z));
                    if (removed == null) continue;
                    int end = x;
                    while (end + 1 < sx && removed.equals(changes.get(new BlockPos(end + 1, y, z)))) end++;
                    JsonArray run = new JsonArray();
                    for (int v : new int[] {x, y, z, end, y, z, removed ? 1 : 0}) run.add(v);
                    runs.add(run);
                    x = end;
                }
        return runs;
    }

    /** The changes in world coordinates (cell + origin), for the model to tell the player what to place and break. */
    private static String changeReport(Map<BlockPos, Boolean> changes, Map<BlockPos, BlockState> cells, Map<BlockPos, BlockState> base,
                                       BlockPos origin, int copied) {
        StringBuilder out = new StringBuilder("Copied " + copied + " blocks from the world (world = cell + " + origin.getX() + " "
                + origin.getY() + " " + origin.getZ() + "). ");
        if (changes.isEmpty()) return out.append("Nothing in it changes yet.").toString();
        out.append(changes.size()).append(changes.size() == 1 ? " cell changes" : " cells change").append(", outlined for the "
                + "player (world x y z):\n");
        List<BlockPos> sorted = new ArrayList<>(changes.keySet());
        sorted.sort(java.util.Comparator.comparingInt((BlockPos q) -> q.getY()).thenComparingInt(q -> q.getZ()).thenComparingInt(q -> q.getX()));
        int lines = 0;
        for (BlockPos p : sorted) {
            if (lines++ >= 80) {
                out.append("...and ").append(changes.size() - 80).append(" more\n");
                break;
            }
            BlockPos w = p.offset(origin);
            String at = w.getX() + " " + w.getY() + " " + w.getZ();
            BlockState was = base.get(p), now = cells.get(p);
            if (changes.get(p)) out.append("- break ").append(BlockStateParser.serialize(was)).append(" at ").append(at).append('\n');
            else if (was == null) out.append("- place ").append(BlockStateParser.serialize(now)).append(" at ").append(at).append('\n');
            else out.append("- replace ").append(BlockStateParser.serialize(was)).append(" with ").append(BlockStateParser.serialize(now))
                    .append(" at ").append(at).append('\n');
        }
        return out.toString().strip();
    }

    /** The model, or why it wasn't built; check is the rotation check's report for a built model (may be empty). */
    record Result(JsonObject model, String problem, String check, boolean draft, byte[] picture) {
        static Result problem(String problem) {
            return new Result(null, problem, "", false, null);
        }
    }

    private static boolean inside(int v, int size) {
        return v >= 0 && v < size;
    }

    static boolean isModel(JsonObject o) {
        return GuideText.string(o, "type").equals("model");
    }
}
