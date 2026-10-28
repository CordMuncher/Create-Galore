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
    static final String[] SHAPES = {"box", "hollow_box", "ellipsoid", "ellipsoid_shell", "repaint"};

    /** A model built for the answer being written, kept so the next version can start from it (from_draft). */
    record Draft(Map<BlockPos, BlockState> cells, int sx, int sy, int sz, JsonArray labels, JsonArray moving, String title,
                 String caption, JsonArray speeds) {
    }

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
        JsonObject shape = WorldTools.prop("string", "What part of the box (x, y, z to x2, y2, z2) this fills: box (all of it), "
                + "hollow_box (its outer walls, one block thick), ellipsoid (the rounded solid that fits inside it) or "
                + "ellipsoid_shell (that rounded solid's outer skin, one block thick: an envelope, dome or rounded hull in one entry; "
                + "clear its underside with an air box where a balloon needs its open bottom) or repaint (only the cells in the box "
                + "that already hold a block, which take this one instead: a stripe round an envelope, a different wood for a trim).");
        JsonArray shapes = new JsonArray();
        for (String k : SHAPES) shapes.add(k);
        shape.add("enum", shapes);
        p.add("shape", shape);
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

        JsonObject shift = new JsonObject();
        shift.addProperty("type", "object");
        JsonObject sp = new JsonObject();
        sp.add("x1", WorldTools.prop("integer", "Model cell x of one corner of the box of blocks to move."));
        sp.add("y1", WorldTools.prop("integer", "See x1."));
        sp.add("z1", WorldTools.prop("integer", "See x1."));
        sp.add("x2", WorldTools.prop("integer", "Model cell x of the opposite corner."));
        sp.add("y2", WorldTools.prop("integer", "See x2."));
        sp.add("z2", WorldTools.prop("integer", "See x2."));
        sp.add("dx", WorldTools.prop("integer", "How many cells to move it along x (negative moves it toward x = 0)."));
        sp.add("dy", WorldTools.prop("integer", "Along y."));
        sp.add("dz", WorldTools.prop("integer", "Along z."));
        shift.add("properties", sp);
        shift.add("required", required(sp));
        shift.addProperty("additionalProperties", false);

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
        props.add("from_draft", WorldTools.prop("integer", "0, or the number of a model built earlier for this answer (each "
                + "result names it) to start from instead of writing it all again: its blocks, labels, moving parts, title and "
                + "caption come along (labels, moving, title and caption you write replace them), size 0 keeps its size, and "
                + "move, layers and blocks then write only the changes (a layer's '.' leaves the cell as it was, minecraft:air "
                + "takes a block out). Use it for every revision of a design."));
        JsonObject moves = new JsonObject();
        moves.addProperty("type", "array");
        moves.addProperty("description", "Empty, or boxes of blocks to slide, applied in order before layers and blocks: everything "
                + "in the box moves together and the cells it leaves become air (a balloon one block back, an engine pod "
                + "up a block, the gondola toward the bow), so a design is shifted without rewriting it.");
        moves.add("items", shift);
        props.add("move", moves);
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
        props.add("draft", WorldTools.prop("boolean", "True for a try that isn't shown."));
        props.add("goal", WorldTools.prop("string", "What it makes, if it makes an item: that item's id first, then from "
                + "what (create:brass_ingot from copper and zinc). Its check proves the model makes it."));
        props.add("shows_a_fault", WorldTools.prop("boolean", "True only when the model is meant to show a build that "
                + "doesn't work (such as the player's own broken build); then it is built even when the rotation check finds faults."));
        props.add("for_later", WorldTools.prop("boolean", "True only when the player asked for a build to make later on; "
                + "then parts they can't make yet don't stop it being built."));
        props.add("moving", MotionCheck.definition());
        JsonObject speed = new JsonObject();
        speed.addProperty("type", "object");
        JsonObject vp = new JsonObject();
        vp.add("x", WorldTools.prop("integer", "Its cell."));
        vp.add("y", WorldTools.prop("integer", "See x."));
        vp.add("z", WorldTools.prop("integer", "See x."));
        vp.add("rpm", WorldTools.prop("integer", "-256 to 256."));
        speed.add("properties", vp);
        speed.add("required", required(vp));
        speed.addProperty("additionalProperties", false);
        JsonObject speeds = new JsonObject();
        speeds.addProperty("type", "array");
        speeds.addProperty("description", "Each Rotation Speed Controller's set speed; one left out is at 16 RPM.");
        speeds.add("items", speed);
        props.add("speeds", speeds);
        props.add("part_of_vehicle", WorldTools.prop("boolean", "True only when the model shows one part of a vehicle (an "
                + "engine room, a rudder, a burner and its balloon) rather than the whole of it: the vehicle check's numbers "
                + "come back but don't stop it being built. Leave it false for a whole boat, submarine, airship or car."));
        return WorldTools.tool("build_model", "Builds a 3D model out of real blocks, up to " + MAX_SIZE + " a side, which "
                + "the book shows as a hologram the player can turn around and zoom into, and works out how it behaves. "
                + "Write a detailed build as layers; fences, walls, panes and bars join their neighbours (fences also "
                + "diagonally) and stairs turn corners by themselves, as when placed. Every block keeps the facing or axis "
                + "you write and takes its default when you leave it out, often the wrong way, so write it on every block "
                + "that has one. Try a design with draft true first: you get pictures of it and its check, and nothing goes "
                + "in the answer. Revise with from_draft and write only the changes (move slides a part over). Before a "
                + "model is shown the server runs it by the mods' own rules: rotation (which faces take a shaft, cogs, "
                + "belts, sources, speeds, water on wheels), where machines send their output, sail rigs, posts, moving "
                + "parts, and for a vehicle (boat, submarine, airship, plane or Offroad car) its mass, lift or buoyancy, "
                + "trim, speed, turning, ceiling and how its crew walks, reaches, boards and keeps warm. A model with a "
                + "PROBLEM is refused with what to change. Every block of a vehicle must share a face with the rest (Super "
                + "Glue refuses edge and corner contact); edge and corner steps are filled for you and the result says "
                + "where. Parts that move as Create contraptions go in moving, with the Super Glue the player puts on; "
                + "write pistons retracted, poles behind them. Water and lava: a source is level=0, flowing 1 to 7, "
                + "falling 8. To change a build of the player's, copy it in with from_world (bounds from trace_build or "
                + "read_area) and write only what changes; you get the changes back in world coordinates. It returns the "
                + "model's number: write [diagram N] on a line of its own where it should appear, or put it inside a "
                + "drawing with a shape of kind model.", props);
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
        return build(input, level, copier, player, null);
    }

    /** Builds the model; with {@code drafts}, from_draft can start from one of them and the new one is added to them. */
    static Result build(JsonObject input, ServerLevel level, Copier copier, net.minecraft.server.level.ServerPlayer player,
                        List<Draft> drafts) {
        return build(input, level, copier, player, drafts, null);
    }

    /**
     * Builds the model; {@code briefs} holds the kinds of build whose design brief this answer already got, and the
     * first model of a new kind (a build to copy, a vehicle, an airship...) comes back with that kind's brief.
     */
    static Result build(JsonObject input, ServerLevel level, Copier copier, net.minecraft.server.level.ServerPlayer player,
                        List<Draft> drafts, java.util.Set<String> briefs) {
        // The checks name only drive parts this player can make, and read the model's speed controller settings.
        return Drives.asking(player, Map.of(), () -> buildAs(input, level, copier, player, drafts, briefs));
    }

    private static Result buildAs(JsonObject input, ServerLevel level, Copier copier, net.minecraft.server.level.ServerPlayer player,
                                  List<Draft> drafts, java.util.Set<String> briefs) {
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
        int fromDraft = Diagrams.intOr(input, "from_draft", 0);
        Draft draft0 = null;
        if (fromDraft != 0) {
            if (base != null) return Result.problem("Not built: use from_world or from_draft, not both.");
            int have = drafts == null ? 0 : drafts.size();
            if (fromDraft < 1 || fromDraft > have)
                return Result.problem("Not built: there is no model " + fromDraft + " to start from (" + (have == 0 ? "none built yet"
                        : "models 1 to " + have + " so far") + ").");
            draft0 = drafts.get(fromDraft - 1);
            cells.putAll(draft0.cells());
            if (sx == 0) sx = draft0.sx();
            if (sy == 0) sy = draft0.sy();
            if (sz == 0) sz = draft0.sz();
        }
        if (sx < 1 || sx > MAX_SIZE || sy < 1 || sy > MAX_SIZE || sz < 1 || sz > MAX_SIZE)
            return Result.problem("Not built: size_x, size_y and size_z must each be 1 to " + MAX_SIZE + ".");
        JsonArray speedsIn = array(input, "speeds");
        if (speedsIn.isEmpty() && draft0 != null && draft0.speeds() != null) speedsIn = draft0.speeds();
        Map<BlockPos, Float> set = new HashMap<>();
        for (JsonElement e : speedsIn) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            set.put(new BlockPos(Diagrams.intOr(o, "x", 0), Diagrams.intOr(o, "y", 0), Diagrams.intOr(o, "z", 0)),
                    (float) Math.max(-256, Math.min(256, Diagrams.intOr(o, "rpm", 16))));
        }
        Drives.setModel(set);
        JsonArray moves = array(input, "move");
        for (int i = 0; i < moves.size() && problems.size() < 10; i++) {
            if (!moves.get(i).isJsonObject()) continue;
            JsonObject m = moves.get(i).getAsJsonObject();
            int x1 = Diagrams.intOr(m, "x1", 0), y1 = Diagrams.intOr(m, "y1", 0), z1 = Diagrams.intOr(m, "z1", 0);
            int x2 = Diagrams.intOr(m, "x2", 0), y2 = Diagrams.intOr(m, "y2", 0), z2 = Diagrams.intOr(m, "z2", 0);
            BlockPos d = new BlockPos(Diagrams.intOr(m, "dx", 0), Diagrams.intOr(m, "dy", 0), Diagrams.intOr(m, "dz", 0));
            Map<BlockPos, BlockState> lifted = new HashMap<>();
            for (BlockPos q : BlockPos.betweenClosed(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2),
                    Math.max(y1, y2), Math.max(z1, z2))) {
                BlockState st = cells.remove(q);
                if (st != null) lifted.put(q.immutable(), st);
            }
            String out = null;
            for (BlockPos q : lifted.keySet()) {
                BlockPos t = q.offset(d);
                if (!inside(t.getX(), sx) || !inside(t.getY(), sy) || !inside(t.getZ(), sz)) out = t.getX() + "," + t.getY() + "," + t.getZ();
            }
            if (out != null) {
                problems.add("move " + i + " would put a block at " + out + ", outside the " + sx + "x" + sy + "x" + sz + " model (make it bigger)");
                cells.putAll(lifted);
                continue;
            }
            lifted.forEach((q, st) -> cells.put(q.offset(d), st));
        }
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
            String shape = GuideText.string(b, "shape");
            for (BlockPos p : BlockPos.betweenClosed(x, y, z, x2, y2, z2)) {
                if (!inShape(shape, p, x, y, z, x2, y2, z2) || shape.equals("repaint") && !cells.containsKey(p)) continue;
                if (state.isAir()) cells.remove(p);
                else cells.put(p.immutable(), state);
            }
        }
        if (cells.isEmpty() && problems.isEmpty()) problems.add("the model has no blocks: fill layers or blocks");
        for (BlockPos q : cells.keySet())
            if (!inside(q.getX(), sx) || !inside(q.getY(), sy) || !inside(q.getZ(), sz)) {
                problems.add("a block at " + q.getX() + "," + q.getY() + "," + q.getZ() + " is outside the " + sx + "x" + sy + "x" + sz
                        + " model (keep the size, or move it in)");
                break;
            }
        JsonArray labelsIn = array(input, "labels");
        if (labelsIn.isEmpty() && draft0 != null) labelsIn = draft0.labels();
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
        String unbuilt = missingPart(GuideText.string(input, "caption"), cells, null);
        if (unbuilt != null) problems.add("the caption names " + unbuilt + ", but the model has none: build the part in, or "
                + "leave it out of the caption; the caption only names what the model really has");
        if (!problems.isEmpty()) return Result.problem("Not built. Fix these and call again: " + String.join("; ", problems) + ".");
        // What the model changes on the copied build, before blocks join up (a fence that now meets a new post isn't a change).
        Map<BlockPos, Boolean> changes = new HashMap<>();
        if (base != null) {
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet())
                if (!e.getValue().equals(base.get(e.getKey()))) changes.put(e.getKey(), false);
            for (BlockPos p : base.keySet()) if (!cells.containsKey(p)) changes.put(p, true);
        }
        for (BlockPos p : set.keySet()) {
            BlockState at = cells.get(p);
            if (at == null || !BuiltInRegistries.BLOCK.getKey(at.getBlock()).toString().equals("create:rotation_speed_controller"))
                return Result.problem("Not built: speeds names cell " + p.getX() + "," + p.getY() + "," + p.getZ()
                        + ", which holds no Rotation Speed Controller.");
        }
        boolean showsFault = input.has("shows_a_fault") && input.get("shows_a_fault").isJsonPrimitive()
                && input.get("shows_a_fault").getAsBoolean();
        // A vehicle's corner steps are filled so it glues as one piece; the book is told where, so it builds them in.
        List<BlockPos> steps = showsFault || base != null ? List.of() : VehicleCheck.closeSteps(cells, sx, sy, sz);
        if (level != null) connect(cells, level);
        // Moving parts first: the blocks that ride on them leave the network they sit against, so the rotation check
        // only looks at the blocks that stay put.
        List<String> motionProblems = new ArrayList<>();
        JsonArray movingIn = array(input, "moving");
        if (movingIn.isEmpty() && draft0 != null) movingIn = draft0.moving();
        MotionCheck.Result motion = MotionCheck.check(cells, movingIn, level, sx, sy, sz, motionProblems);
        if (!motionProblems.isEmpty()) return Result.problem("Not built. Fix these and call again: " + String.join("; ", motionProblems) + ".");
        Map<BlockPos, BlockState> fixed = cells;
        if (motion != null) {
            fixed = new HashMap<>(cells);
            motion.riding().forEach(fixed::remove);
        }
        // One pipeline, three lenses: does it do its job, can a player use it, can this player build it now.
        BuildCheck.Report works = join(level == null ? null : BuildCheck.check(fixed, level), RigCheck.check(cells));
        if (level != null) works = join(works, OutputCheck.check(fixed, level));
        net.minecraft.world.item.Item goal = GoalCheck.goal(input);
        if (goal != null && level != null) works = join(works, GoalCheck.check(cells, goal, GuideText.string(input, "goal"), level));
        if (motion != null) works = join(works, motion.report());
        works = join(works, vehicle(cells, level, input.has("part_of_vehicle") && input.get("part_of_vehicle").isJsonPrimitive()
                && input.get("part_of_vehicle").getAsBoolean()));
        BuildCheck.Report usable = PlayerCheck.posts(cells);
        BuildCheck.Report makeable = null;
        boolean forLater = input.has("for_later") && input.get("for_later").isJsonPrimitive() && input.get("for_later").getAsBoolean();
        if (level != null && !forLater) {
            // Parts the player would place: on their own build, only what the model adds.
            java.util.Set<net.minecraft.world.level.block.Block> built = new java.util.HashSet<>();
            if (base != null) base.values().forEach(st -> built.add(st.getBlock()));
            List<BlockState> added = new ArrayList<>();
            for (BlockState st : cells.values()) if (!built.contains(st.getBlock())) added.add(st);
            if (player != null) makeable = Reach.parts(player, level.getServer(), added, goal);
            if (goal != null) makeable = join(makeable, Reach.selfMade(player, level.getServer(), added, goal));
        }
        BuildCheck.Report check = lenses(works, usable, makeable);
        boolean draft = input.has("draft") && input.get("draft").isJsonPrimitive() && input.get("draft").getAsBoolean();
        String title = GuideText.string(input, "title"), caption = GuideText.string(input, "caption");
        if (draft0 != null && title.isEmpty()) title = draft0.title();
        if (draft0 != null && caption.isEmpty()) caption = draft0.caption();
        // Kept for the next version, refused or not: fixing a fault is a few changes on top of it.
        String saved = "";
        if (drafts != null) {
            drafts.add(new Draft(new HashMap<>(cells), sx, sy, sz, labels, movingIn, title, caption, speedsIn));
            saved = "\nThis is model " + drafts.size() + ": start its next version with from_draft " + drafts.size()
                    + " and write only what changes.";
        }
        // The design briefs for kinds of build this answer hasn't had yet; a model only explaining a point gets none.
        if (briefs != null && !showsFault && base == null) {
            List<String> brief = DesignBriefs.forModel(cells, briefs);
            if (!brief.isEmpty()) saved += "\n\nDesign brief for this kind of build (for you; follow it in this version and every "
                    + "later one):\n" + String.join("\n", brief);
        }
        if (check != null && !check.faults().isEmpty() && !showsFault && !draft) {
            return Result.problem("Not built: blocks in it wouldn't work as placed. Fix the model and call again (or "
                    + "set shows_a_fault if it is meant to show a broken build).\n" + check.text() + saved);
        }
        JsonObject model = new JsonObject();
        model.addProperty("type", "model");
        model.addProperty("title", title);
        model.addProperty("caption", caption);
        JsonArray size = new JsonArray();
        size.add(sx);
        size.add(sy);
        size.add(sz);
        model.add("size", size);
        compact(cells, sx, sy, sz, model);
        model.add("labels", labels);
        if (motion != null) model.add("moving", motion.moving());
        String report = check == null ? "" : check.text();
        if (!steps.isEmpty()) report = (report.isEmpty() ? "" : report + "\n") + stepReport(steps, cells);
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
        report += saved;
        byte[] picture = !draft ? null : motion == null ? Preview.render(cells, sx, sy, sz) : Preview.render(cells, sx, sy, sz, motion.moved());
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
        PARTS.put("belt", id -> id.contains("belt"));
        PARTS.put("deployer", id -> id.contains("deployer"));
        PARTS.put("funnel", id -> id.contains("funnel"));
        PARTS.put("arm", id -> id.endsWith("_arm"));
        PARTS.put("depot", id -> id.contains("depot"));
        PARTS.put("basin", id -> id.contains("basin"));
        PARTS.put("mixer", id -> id.contains("mixer"));
        PARTS.put("spout", id -> id.contains("spout"));
        PARTS.put("fan", id -> id.contains("fan"));
        PARTS.put("saw", id -> id.contains("saw"));
        PARTS.put("drill", id -> id.contains("drill"));
    }

    /**
     * The part a label's words name when no block near its cell is that part, else null. Near is within two blocks, or
     * anywhere straight above or below the cell and its neighbours: a label on a roof or deck honestly names the barrels
     * or engine under it, though the label can only point at the outside. With no cell (a caption), anywhere in the model.
     */
    private static String missingPart(String text, Map<BlockPos, BlockState> cells, BlockPos at) {
        if (text == null || text.isEmpty()) return null;
        for (String w : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z]+")) {
            String word = w.endsWith("s") && PARTS.containsKey(w.substring(0, w.length() - 1)) ? w.substring(0, w.length() - 1) : w;
            java.util.function.Predicate<String> is = PARTS.get(word);
            if (is == null) continue;
            boolean found = false;
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                BlockPos q = e.getKey();
                // With no cell (a caption), anywhere in the model counts.
                int dx = at == null ? 0 : Math.abs(q.getX() - at.getX()), dy = at == null ? 0 : Math.abs(q.getY() - at.getY()),
                        dz = at == null ? 0 : Math.abs(q.getZ() - at.getZ());
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

    /** Whether a cell of the box x..x2, y..y2, z..z2 (corners in any order) is part of the named shape. */
    static boolean inShape(String shape, BlockPos p, int x, int y, int z, int x2, int y2, int z2) {
        int ax = Math.min(x, x2), bx = Math.max(x, x2), ay = Math.min(y, y2), by = Math.max(y, y2), az = Math.min(z, z2), bz = Math.max(z, z2);
        return switch (shape) {
            case "hollow_box" -> p.getX() == ax || p.getX() == bx || p.getY() == ay || p.getY() == by || p.getZ() == az || p.getZ() == bz;
            case "ellipsoid" -> inEllipsoid(p.getX(), p.getY(), p.getZ(), ax, bx, ay, by, az, bz);
            case "ellipsoid_shell" -> {
                if (!inEllipsoid(p.getX(), p.getY(), p.getZ(), ax, bx, ay, by, az, bz)) yield false;
                // On the skin when any neighbour, edge and corner ones too, is outside it: then the skin is joined face to
                // face all round, with no steps that meet only at an edge.
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (!inEllipsoid(p.getX() + dx, p.getY() + dy, p.getZ() + dz, ax, bx, ay, by, az, bz)) yield true;
                yield false;
            }
            default -> true;
        };
    }

    /** The cell's centre inside the ellipsoid that fits the box (a box one cell thick on an axis is flat along it). */
    private static boolean inEllipsoid(int x, int y, int z, int ax, int bx, int ay, int by, int az, int bz) {
        if (x < ax || x > bx || y < ay || y > by || z < az || z > bz) return false;
        double sum = 0;
        int[][] axes = {{x, ax, bx}, {y, ay, by}, {z, az, bz}};
        for (int[] a : axes) {
            double r = (a[2] - a[1] + 1) / 2.0, c = (a[1] + a[2] + 1) / 2.0;
            if (r > 0.5) sum += Math.pow((a[0] + 0.5 - c) / r, 2);
        }
        return sum <= 1.0001;
    }

    /** Where corner steps were filled, so the book's next version has them and its words match the model. */
    private static String stepReport(List<BlockPos> steps, Map<BlockPos, BlockState> cells) {
        List<String> where = new ArrayList<>();
        for (int i = 0; i < Math.min(steps.size(), 24); i++) {
            BlockPos p = steps.get(i);
            where.add(p.getX() + "," + p.getY() + "," + p.getZ() + " (" + BuiltInRegistries.BLOCK.getKey(cells.get(p).getBlock()).getPath() + ")");
        }
        return "Filled " + steps.size() + " corner step" + (steps.size() == 1 ? "" : "s") + " where blocks met only along an edge or "
                + "at a corner, so the whole vehicle shares faces and Super Glue takes it as one (they are in this model, and "
                + "from_draft keeps them; count them in the materials): " + String.join("; ", where)
                + (steps.size() > 24 ? "; and more" : "") + ".";
    }

    /** The three lenses' reports as one, each under its heading; null when none has anything to say. */
    private static BuildCheck.Report lenses(BuildCheck.Report works, BuildCheck.Report usable, BuildCheck.Report makeable) {
        String[] headings = {"WORKS (does it do its job)", "USABLE (can a player reach, use and get around it)",
                "MAKEABLE (can this player build it now)"};
        BuildCheck.Report[] reports = {works, usable, makeable};
        StringBuilder text = new StringBuilder();
        List<String> faults = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (reports[i] == null) continue;
            if (!text.isEmpty()) text.append('\n');
            text.append("== ").append(headings[i]).append(" ==\n").append(reports[i].text());
            faults.addAll(reports[i].faults());
        }
        return text.isEmpty() ? null : new BuildCheck.Report(text.toString(), faults);
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
        for (BlockState s : cells.values())
            count.merge(BuiltInRegistries.BLOCK.getKey(dev.creategalore.bridge.DiagonalJoins.plain(s.getBlock())).toString(), 1, Integer::sum);
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
            // A fence becomes the twin its item places (Diagonal Fences), so it joins diagonally as in the world.
            BlockState state = dev.creategalore.bridge.DiagonalJoins.placed(parsed.blockState());
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
            if (was != null) was = dev.creategalore.bridge.DiagonalJoins.plain(was);
            if (now != null) now = dev.creategalore.bridge.DiagonalJoins.plain(now);
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
