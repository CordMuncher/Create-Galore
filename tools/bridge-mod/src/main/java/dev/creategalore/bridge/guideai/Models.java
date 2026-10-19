package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The book's 3D models: a small build the model writes block by block (or in boxes), which the book screen shows as
 * a hologram the player can turn and zoom (client/ModelView). The server checks every block state against this
 * server's blocks and passes the model on as JSON with each state written out in full, sharing the drawings'
 * numbering so [diagram N] places it in an answer.
 */
public final class Models {
    public static final int MAX_SIZE = 16, MAX_ENTRIES = 256, MAX_LABELS = 12;

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

        JsonObject props = new JsonObject();
        props.add("title", WorldTools.prop("string", "A few words naming what the model shows, or an empty string."));
        props.add("size_x", WorldTools.prop("integer", "Width in blocks, 1 to " + MAX_SIZE + "."));
        props.add("size_y", WorldTools.prop("integer", "Height in blocks, 1 to " + MAX_SIZE + "."));
        props.add("size_z", WorldTools.prop("integer", "Depth in blocks, 1 to " + MAX_SIZE + "."));
        JsonObject blocks = new JsonObject();
        blocks.addProperty("type", "array");
        blocks.addProperty("description", "Placed in order, later entries over earlier ones. At most " + MAX_ENTRIES
                + "; use boxes for floors, walls and runs of shaft.");
        blocks.add("items", block);
        props.add("blocks", blocks);
        JsonObject labels = new JsonObject();
        labels.addProperty("type", "array");
        labels.addProperty("description", "Short labels floating at cells, at most " + MAX_LABELS + "; often none.");
        labels.add("items", label);
        props.add("labels", labels);
        props.add("caption", WorldTools.prop("string", "One short sentence under the model, or an empty string."));
        return WorldTools.tool("build_model", "Builds a small 3D model out of real blocks, which the book shows as a "
                + "hologram the player can turn around and zoom into. For things a flat drawing shows badly: how machines "
                + "connect in space, which way blocks face, the shape of a build. Costs more than a drawing, so use it only "
                + "when the third dimension matters or the player asks. It returns the model's number; write [diagram N] "
                + "on a line of its own where it should appear, or put it inside a drawing with a shape of kind model.", props);
    }

    private static JsonArray required(JsonObject properties) {
        JsonArray a = new JsonArray();
        properties.keySet().forEach(a::add);
        return a;
    }

    /**
     * The model as the book stores and shows it, or what to fix. Server thread (reads the block registry).
     */
    static Result build(JsonObject input) {
        List<String> problems = new ArrayList<>();
        int sx = Diagrams.intOr(input, "size_x", 0), sy = Diagrams.intOr(input, "size_y", 0), sz = Diagrams.intOr(input, "size_z", 0);
        if (sx < 1 || sx > MAX_SIZE || sy < 1 || sy > MAX_SIZE || sz < 1 || sz > MAX_SIZE)
            return Result.problem("Not built: size_x, size_y and size_z must each be 1 to " + MAX_SIZE + ".");
        JsonArray in = input.has("blocks") && input.get("blocks").isJsonArray() ? input.getAsJsonArray("blocks") : new JsonArray();
        if (in.isEmpty() || in.size() > MAX_ENTRIES) problems.add("use 1 to " + MAX_ENTRIES + " block entries");
        JsonArray out = new JsonArray();
        for (int i = 0; i < in.size() && i < MAX_ENTRIES && problems.size() < 10; i++) {
            if (!in.get(i).isJsonObject()) {
                problems.add("entry " + i + " is not an object");
                continue;
            }
            JsonObject b = in.get(i).getAsJsonObject();
            String raw = GuideText.string(b, "block").strip();
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), raw, false).blockState();
            } catch (CommandSyntaxException e) {
                problems.add("entry " + i + ": " + raw + " is not a block state on this server (" + e.getRawMessage().getString() + ")");
                continue;
            }
            int x = Diagrams.intOr(b, "x", -1), y = Diagrams.intOr(b, "y", -1), z = Diagrams.intOr(b, "z", -1);
            int x2 = Diagrams.intOr(b, "x2", x), y2 = Diagrams.intOr(b, "y2", y), z2 = Diagrams.intOr(b, "z2", z);
            if (!inside(x, sx) || !inside(y, sy) || !inside(z, sz) || !inside(x2, sx) || !inside(y2, sy) || !inside(z2, sz)) {
                problems.add("entry " + i + " (" + raw + "): a corner is outside the " + sx + "x" + sy + "x" + sz + " model");
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("state", BlockStateParser.serialize(state));
            o.addProperty("x", Math.min(x, x2));
            o.addProperty("y", Math.min(y, y2));
            o.addProperty("z", Math.min(z, z2));
            o.addProperty("x2", Math.max(x, x2));
            o.addProperty("y2", Math.max(y, y2));
            o.addProperty("z2", Math.max(z, z2));
            out.add(o);
        }
        JsonArray labelsIn = input.has("labels") && input.get("labels").isJsonArray() ? input.getAsJsonArray("labels") : new JsonArray();
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
        JsonObject model = new JsonObject();
        model.addProperty("type", "model");
        model.addProperty("title", GuideText.string(input, "title"));
        model.addProperty("caption", GuideText.string(input, "caption"));
        JsonArray size = new JsonArray();
        size.add(sx);
        size.add(sy);
        size.add(sz);
        model.add("size", size);
        model.add("blocks", out);
        model.add("labels", labels);
        return new Result(model, null);
    }

    record Result(JsonObject model, String problem) {
        static Result problem(String problem) {
            return new Result(null, problem);
        }
    }

    private static boolean inside(int v, int size) {
        return v >= 0 && v < size;
    }

    static boolean isModel(JsonObject o) {
        return GuideText.string(o, "type").equals("model");
    }
}
