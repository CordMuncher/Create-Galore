package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * The book's drawings: a small pixel canvas the model draws on freely with a few primitives (lines, arrows,
 * freehand paths and polygons, boxes, ellipses, text and real item icons). Only the look is fixed: book pixels and
 * the book's palette. The server only checks a drawing and
 * passes it on as JSON; the book screen paints it (client/DiagramPainter).
 */
public final class Diagrams {
    /** Canvas width in book pixels; height is chosen per drawing. Each book pixel is drawn as a 2x2 block at most sizes. */
    public static final int WIDTH = 160, MAX_HEIGHT = 120, MAX_SHAPES = 120;
    public static final List<String> COLORS = List.of("ink", "faded", "tan", "red", "orange", "gold", "green", "teal", "blue",
            "purple", "brown", "gray", "white");
    public static final List<String> KINDS = List.of("line", "arrow", "path", "rect", "ellipse", "text", "item", "model");
    /** A drawing can hold this many 3D models (each one's blocks travel inside the drawing). */
    static final int MAX_EMBEDDED = 2;
    public static final List<String> FILLS = List.of("none", "solid", "dither");

    private Diagrams() {
    }

    static JsonObject definition() {
        JsonObject shape = new JsonObject();
        shape.addProperty("type", "object");
        JsonObject p = new JsonObject();
        p.add("kind", enumOf("string", KINDS, false, "line and arrow go from x,y to x2,y2 (arrow has a head at x2,y2). path joins "
                + "its points with lines (with fill solid or dither and 3 or more points, it is a filled polygon; a single point "
                + "is one pixel, so paths can draw anything pixel by pixel). rect and ellipse fill the box x,y,w,h. text writes "
                + "text with its top left at x,y (a line is 5 px tall, about 3 px per character; text must fit on the canvas and "
                + "not touch other text, icons, lines or arrows). item draws the real icon of "
                + "an item (8x8 px) with x,y as its top left. model draws a 3D model made earlier in this answer with build_model "
                + "into the box x,y,w,h, seen at a fixed angle; its text is the model's number."));
        p.add("x", nullable("integer", "Left (or start) in book pixels, 0 to " + (WIDTH - 1) + "."));
        p.add("y", nullable("integer", "Top (or start) in book pixels, 0 to the drawing's height."));
        p.add("x2", nullable("integer", "End x for line and arrow, else null."));
        p.add("y2", nullable("integer", "End y for line and arrow, else null."));
        p.add("w", nullable("integer", "Width for rect, ellipse and model, else null."));
        p.add("h", nullable("integer", "Height for rect, ellipse and model, else null."));
        p.add("color", enumOf("string", COLORS, true, "Palette colour; null means ink."));
        p.add("fill", enumOf("string", FILLS, true, "For rect and ellipse: none (outline), solid, or dither (see-through "
                + "checkerboard, good for overlaps). Null means none."));
        p.add("text", nullable("string", "The words for text (keep it short; \\n starts a new line), the model's number "
                + "for model, else null."));
        p.add("item", nullable("string", "Item or block id for item, such as create:cogwheel, else null."));
        JsonObject points = new JsonObject();
        JsonArray pointsType = new JsonArray();
        pointsType.add("array");
        pointsType.add("null");
        points.add("type", pointsType);
        JsonObject point = new JsonObject();
        point.addProperty("type", "array");
        JsonObject coord = new JsonObject();
        coord.addProperty("type", "integer");
        point.add("items", coord);
        points.add("items", point);
        points.addProperty("description", "For path: the points as [x, y] pairs, at most 200. Else null.");
        p.add("points", points);
        shape.add("properties", p);
        JsonArray required = new JsonArray();
        p.keySet().forEach(required::add);
        shape.add("required", required);
        shape.addProperty("additionalProperties", false);

        JsonObject props = new JsonObject();
        props.add("title", WorldTools.prop("string", "A few words naming what the drawing shows, or an empty string."));
        props.add("height", WorldTools.prop("integer", "Canvas height in book pixels, 16 to " + MAX_HEIGHT + ". The width is always "
                + WIDTH + "."));
        JsonObject shapes = new JsonObject();
        shapes.addProperty("type", "array");
        shapes.addProperty("description", "Painted in order, later shapes on top. At most " + MAX_SHAPES + ".");
        shapes.add("items", shape);
        props.add("shapes", shapes);
        props.add("caption", WorldTools.prop("string", "One short sentence under the drawing, or an empty string."));
        return WorldTools.tool("draw_diagram", "Draws a picture on the book page: a small pixel canvas "
                + "in the book's palette, drawn with shapes, freehand paths, text and real item icons. Draw whatever explains "
                + "it best. It returns the drawing's number; write [diagram N] on a line of its own in your answer where it "
                + "should appear.", props);
    }

    private static JsonObject nullable(String type, String description) {
        JsonObject o = new JsonObject();
        JsonArray t = new JsonArray();
        t.add(type);
        t.add("null");
        o.add("type", t);
        o.addProperty("description", description);
        return o;
    }

    private static JsonObject enumOf(String type, List<String> values, boolean orNull, String description) {
        JsonObject o = orNull ? nullable(type, description) : WorldTools.prop(type, description);
        JsonArray e = new JsonArray();
        values.forEach(e::add);
        if (orNull) e.add(JsonNull.INSTANCE);
        o.add("enum", e);
        return o;
    }

    /**
     * Null when the drawing can be painted as given, else what to fix. A model shape gets the model it names (one of
     * the answer's earlier drawings) copied in as "embedded", so pass a copy of the tool input, not the input itself.
     * Runs on the server thread (reads the item registry).
     */
    static String check(JsonObject input, List<String> earlier) {
        List<String> problems = new ArrayList<>();
        int embedded = 0;
        int height = intOr(input, "height", -1);
        if (height < 16 || height > MAX_HEIGHT) problems.add("height must be 16 to " + MAX_HEIGHT);
        if (!input.has("shapes") || !input.get("shapes").isJsonArray()) return "Not drawn: shapes is missing.";
        JsonArray shapes = input.getAsJsonArray("shapes");
        if (shapes.isEmpty() || shapes.size() > MAX_SHAPES) problems.add("use 1 to " + MAX_SHAPES + " shapes");
        for (int i = 0; i < shapes.size() && problems.size() < 10; i++) {
            if (!shapes.get(i).isJsonObject()) {
                problems.add("shape " + i + " is not an object");
                continue;
            }
            JsonObject s = shapes.get(i).getAsJsonObject();
            String kind = GuideText.string(s, "kind");
            String where = "shape " + i + " (" + kind + ")";
            if (!KINDS.contains(kind)) {
                problems.add(where + ": unknown kind");
                continue;
            }
            int x = intOr(s, "x", -999), y = intOr(s, "y", -999);
            if (kind.equals("path")) x = y = 0;
            if (x < 0 || x >= WIDTH || y < 0 || y >= Math.max(height, 1)) problems.add(where + ": x,y outside the canvas");
            switch (kind) {
                case "line", "arrow" -> {
                    int x2 = intOr(s, "x2", -999), y2 = intOr(s, "y2", -999);
                    if (x2 < 0 || x2 >= WIDTH || y2 < 0 || y2 >= Math.max(height, 1)) problems.add(where + ": x2,y2 outside the canvas");
                }
                case "rect", "ellipse" -> {
                    if (intOr(s, "w", 0) < 2 || intOr(s, "h", 0) < 2) problems.add(where + ": needs w and h of 2 or more");
                }
                case "model" -> {
                    if (intOr(s, "w", 0) < 16 || intOr(s, "h", 0) < 16) problems.add(where + ": needs w and h of 16 or more");
                    JsonObject model = null;
                    try {
                        int n = Integer.parseInt(GuideText.string(s, "text").strip().replace("diagram", "").strip());
                        if (n >= 1 && n <= earlier.size()) {
                            JsonObject o = com.google.gson.JsonParser.parseString(earlier.get(n - 1)).getAsJsonObject();
                            if (Models.isModel(o)) model = o;
                        }
                    } catch (RuntimeException ignored) {
                        // reported below
                    }
                    if (model == null) problems.add(where + ": text must be the number of a model built earlier in this answer");
                    else if (++embedded > MAX_EMBEDDED) problems.add("a drawing holds at most " + MAX_EMBEDDED + " models");
                    else s.add("embedded", model);
                }
                case "text" -> {
                    String t = GuideText.string(s, "text");
                    if (t.isBlank() || t.length() > 80) problems.add(where + ": text must be 1 to 80 characters");
                }
                case "item" -> {
                    if (!isItem(GuideText.string(s, "item"))) problems.add(where + ": not an item on this server: " + GuideText.string(s, "item"));
                }
                case "path" -> {
                    if (!s.has("points") || !s.get("points").isJsonArray() || s.getAsJsonArray("points").isEmpty()
                            || s.getAsJsonArray("points").size() > 200) {
                        problems.add(where + ": needs 1 to 200 points");
                        break;
                    }
                    for (JsonElement pt : s.getAsJsonArray("points")) {
                        JsonArray a = pt.isJsonArray() ? pt.getAsJsonArray() : null;
                        if (a == null || a.size() != 2 || !inside(a.get(0), WIDTH) || !inside(a.get(1), height)) {
                            problems.add(where + ": every point is [x, y] inside the canvas");
                            break;
                        }
                    }
                }
                default -> {
                }
            }
        }
        if (GuideText.string(input, "title").length() > 60) problems.add("title longer than 60 characters");
        if (GuideText.string(input, "caption").length() > 240) problems.add("caption longer than 240 characters");
        if (problems.isEmpty()) clipping(shapes, height, problems);
        return problems.isEmpty() ? null : "Not drawn. Fix these and call again: " + String.join("; ", problems) + ".";
    }

    /**
     * Words must stay readable: each text fits on the canvas, no two texts or icons sit on each other, and no line or
     * arrow runs through words. Text is measured the way the book paints it, about 3 px per character and 5 px per line.
     */
    private static void clipping(JsonArray shapes, int height, List<String> problems) {
        List<int[]> boxes = new ArrayList<>(); // x, y, w, h, shape index, 1 for text
        List<int[]> areas = new ArrayList<>(); // rects, ellipses and models: x, y, w, h, shape index
        for (int i = 0; i < shapes.size(); i++) {
            JsonObject s = shapes.get(i).getAsJsonObject();
            String kind = GuideText.string(s, "kind");
            int x = intOr(s, "x", 0), y = intOr(s, "y", 0);
            if (kind.equals("text")) {
                String[] lines = GuideText.string(s, "text").split("\n", -1);
                int longest = 0;
                for (String l : lines) longest = Math.max(longest, l.length());
                int w = longest * TEXT_ADVANCE, h = lines.length * TEXT_LINE;
                if (x + w > WIDTH || y + h > height)
                    problems.add("shape " + i + " (text \"" + clipped(s) + "\") runs off the canvas: it is about " + w + "x" + h
                            + " px, so move it, shorten it or break it with \\n");
                boxes.add(new int[] {x, y, w, h, i, 1});
            } else if (kind.equals("item")) {
                boxes.add(new int[] {x, y, 8, 8, i, 0});
            } else if (kind.equals("rect") || kind.equals("ellipse") || kind.equals("model")) {
                areas.add(new int[] {x, y, intOr(s, "w", 2), intOr(s, "h", 2), i});
            }
        }
        // Words may sit inside a box or beside it, not across its edge.
        for (int[] t : boxes) {
            if (t[5] != 1) continue;
            for (int[] r : areas) {
                boolean touches = t[0] < r[0] + r[2] && r[0] < t[0] + t[2] && t[1] < r[1] + r[3] && r[1] < t[1] + t[3];
                boolean within = t[0] > r[0] && t[1] > r[1] && t[0] + t[2] < r[0] + r[2] && t[1] + t[3] < r[1] + r[3];
                if (touches && !within && problems.size() < 10) {
                    problems.add("the text of shape " + t[4] + " crosses the edge of shape " + r[4] + "; put it fully inside or "
                            + "fully outside");
                }
            }
        }
        for (int a = 0; a < boxes.size() && problems.size() < 10; a++)
            for (int b = a + 1; b < boxes.size(); b++) {
                int[] p = boxes.get(a), q = boxes.get(b);
                if ((p[5] == 1 || q[5] == 1) && p[0] < q[0] + q[2] && q[0] < p[0] + p[2] && p[1] < q[1] + q[3] && q[1] < p[1] + p[3])
                    problems.add("shapes " + p[4] + " and " + q[4] + " overlap (text is about 3 px per character, 5 px per line)");
            }
        for (int i = 0; i < shapes.size() && problems.size() < 10; i++) {
            JsonObject s = shapes.get(i).getAsJsonObject();
            String kind = GuideText.string(s, "kind");
            if (!kind.equals("line") && !kind.equals("arrow")) continue;
            int x0 = intOr(s, "x", 0), y0 = intOr(s, "y", 0), x1 = intOr(s, "x2", 0), y1 = intOr(s, "y2", 0);
            for (int[] box : boxes) {
                if (box[5] != 1 || !crosses(x0, y0, x1, y1, kind.equals("arrow"), box)) continue;
                problems.add("shape " + i + " (" + kind + ") runs over the text of shape " + box[4] + "; end it a few pixels "
                        + "short of the words");
                break;
            }
        }
    }

    static final int TEXT_ADVANCE = 3, TEXT_LINE = 5;

    private static String clipped(JsonObject s) {
        String t = GuideText.string(s, "text").replace("\n", " ");
        return t.length() > 20 ? t.substring(0, 20) + "..." : t;
    }

    /** Whether the line (and an arrow's head, about 4 px around its tip) touches the box. */
    private static boolean crosses(int x0, int y0, int x1, int y1, boolean arrow, int[] box) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            if (x0 >= box[0] && x0 < box[0] + box[2] && y0 >= box[1] && y0 < box[1] + box[3]) return true;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
        return arrow && x1 + 3 >= box[0] && x1 - 3 < box[0] + box[2] && y1 + 3 >= box[1] && y1 - 3 < box[1] + box[3];
    }

    private static boolean inside(JsonElement e, int max) {
        try {
            int v = e.getAsInt();
            return v >= 0 && v < max;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean isItem(String id) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.tryParse(id.strip().toLowerCase(Locale.ROOT)) : null;
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }

    static int intOr(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
