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
    public static final List<String> KINDS = List.of("line", "arrow", "path", "rect", "ellipse", "text", "item");
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
                + "text with its top left at x,y (a line is 5 px tall, about 3 px per character). item draws the real icon of "
                + "an item (8x8 px) with x,y as its top left."));
        p.add("x", nullable("integer", "Left (or start) in book pixels, 0 to " + (WIDTH - 1) + "."));
        p.add("y", nullable("integer", "Top (or start) in book pixels, 0 to the drawing's height."));
        p.add("x2", nullable("integer", "End x for line and arrow, else null."));
        p.add("y2", nullable("integer", "End y for line and arrow, else null."));
        p.add("w", nullable("integer", "Width for rect and ellipse, else null."));
        p.add("h", nullable("integer", "Height for rect and ellipse, else null."));
        p.add("color", enumOf("string", COLORS, true, "Palette colour; null means ink."));
        p.add("fill", enumOf("string", FILLS, true, "For rect and ellipse: none (outline), solid, or dither (see-through "
                + "checkerboard, good for overlaps). Null means none."));
        p.add("text", nullable("string", "The words for text (keep it short; \\n starts a new line), else null."));
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

    /** Null when the drawing can be painted as given, else what to fix. Runs on the server thread (reads the item registry). */
    static String check(JsonObject input) {
        List<String> problems = new ArrayList<>();
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
        return problems.isEmpty() ? null : "Not drawn. Fix these and call again: " + String.join("; ", problems) + ".";
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
