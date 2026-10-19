package dev.creategalore.bridge.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.creategalore.bridge.guideai.Diagrams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Paints the book's drawings. The book draws freely; this only fixes the look: every shape lands on a grid of
 * chunky book pixels in the book's palette, items are their real icons, and text is the game's own font.
 */
final class DiagramPainter {
    /** The book's palette, in the order of {@link Diagrams#COLORS}. */
    private static final int[] PALETTE = {0xFF2B2014, 0xFF6E5E48, 0xFFC8B48A, 0xFF9E2B1E, 0xFFC0651E, 0xFFC9A227, 0xFF4E7A2E,
            0xFF2E6E6A, 0xFF2E5AAC, 0xFF7A3C9A, 0xFF6B4423, 0xFF7C7C7C, 0xFFF4EEDC};
    private static final int CANVAS = 0xFFE6DABA, FRAME = 0xFF8A7250;

    private record Icon(ItemStack stack, int x, int y) {
    }

    private record Label(String text, int x, int y, int color) {
    }

    private record Embedded(ModelView model, int x, int y, int w, int h) {
    }

    /** One drawing, rasterised once; painting it is a handful of rectangle fills, icons and labels. */
    static final class Drawing {
        final String title, caption;
        final int height;
        private final int[] pixels;
        private final List<Icon> icons = new ArrayList<>();
        private final List<Label> labels = new ArrayList<>();
        private final List<Embedded> models = new ArrayList<>();

        private Drawing(String title, String caption, int height) {
            this.title = title;
            this.caption = caption;
            this.height = height;
            this.pixels = new int[Diagrams.WIDTH * height];
        }

        private void plot(int x, int y, int color) {
            if (x >= 0 && y >= 0 && x < Diagrams.WIDTH && y < height) pixels[y * Diagrams.WIDTH + x] = color;
        }

        private void dot(int x, int y, int color, String fill) {
            if (!fill.equals("dither") || ((x + y) & 1) == 0) plot(x, y, color);
        }

        private void line(int x0, int y0, int x1, int y1, int color) {
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true) {
                plot(x0, y0, color);
                if (x0 == x1 && y0 == y1) return;
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
        }

        private void polygon(int[][] pts, int color, String fill) {
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int[] p : pts) {
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
            for (int y = minY; y <= maxY; y++) {
                List<Double> xs = new ArrayList<>();
                for (int i = 0; i < pts.length; i++) {
                    int[] a = pts[i], b = pts[(i + 1) % pts.length];
                    if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y))
                        xs.add(a[0] + (double) (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
                }
                xs.sort(Double::compare);
                for (int i = 0; i + 1 < xs.size(); i += 2)
                    for (int x = (int) Math.ceil(xs.get(i)); x <= (int) Math.floor(xs.get(i + 1)); x++) dot(x, y, color, fill);
            }
            for (int i = 0; i < pts.length; i++) line(pts[i][0], pts[i][1], pts[(i + 1) % pts.length][0], pts[(i + 1) % pts.length][1], color);
        }

        private void ellipse(int x, int y, int w, int h, int color, String fill) {
            double cx = x + (w - 1) / 2.0, cy = y + (h - 1) / 2.0, rx = w / 2.0, ry = h / 2.0;
            boolean[][] in = new boolean[h][w];
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    double nx = (x + i - cx) / rx, ny = (y + j - cy) / ry;
                    in[j][i] = nx * nx + ny * ny <= 1.0;
                }
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    if (!in[j][i]) continue;
                    boolean edge = i == 0 || j == 0 || i == w - 1 || j == h - 1 || !in[j][i - 1] || !in[j][i + 1] || !in[j - 1][i] || !in[j + 1][i];
                    if (edge) plot(x + i, y + j, color);
                    else if (!fill.equals("none")) dot(x + i, y + j, color, fill);
                }
        }

        private void rect(int x, int y, int w, int h, int color, String fill) {
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) {
                    boolean edge = i == 0 || j == 0 || i == w - 1 || j == h - 1;
                    if (edge) plot(x + i, y + j, color);
                    else if (!fill.equals("none")) dot(x + i, y + j, color, fill);
                }
        }

        private void arrow(int x0, int y0, int x1, int y1, int color) {
            line(x0, y0, x1, y1, color);
            double dx = x1 - x0, dy = y1 - y0, len = Math.max(1e-6, Math.hypot(dx, dy));
            double ux = dx / len, uy = dy / len;
            int bx = (int) Math.round(x1 - ux * 4), by = (int) Math.round(y1 - uy * 4);
            int[][] head = {{x1, y1}, {(int) Math.round(bx - uy * 3), (int) Math.round(by + ux * 3)},
                    {(int) Math.round(bx + uy * 3), (int) Math.round(by - ux * 3)}};
            polygon(head, color, "solid");
        }

        /** Height on screen at this pixel scale, title and caption included. */
        int screenHeight(Font font, int width, int scale) {
            int h = height * scale + 4;
            if (!title.isEmpty()) h += font.lineHeight + 2;
            if (!caption.isEmpty()) h += font.split(Component.literal(caption), width).size() * font.lineHeight + 2;
            return h;
        }

        /** Paints at (left, top) within width; returns the item under the mouse, if any, for its tooltip. */
        ItemStack paint(GuiGraphics g, Font font, int left, int top, int width, int scale, int mouseX, int mouseY) {
            int canvasW = Diagrams.WIDTH * scale, x0 = left + (width - canvasW) / 2, y = top;
            if (!title.isEmpty()) {
                g.drawString(font, title, left + (width - font.width(title)) / 2, y, PALETTE[0], false);
                y += font.lineHeight + 2;
            }
            int y0 = y + 2;
            g.fill(x0 - scale, y0 - scale, x0 + canvasW + scale, y0 + height * scale + scale, FRAME);
            g.fill(x0, y0, x0 + canvasW, y0 + height * scale, CANVAS);
            for (int row = 0; row < height; row++) {
                int start = 0;
                while (start < Diagrams.WIDTH) {
                    int c = pixels[row * Diagrams.WIDTH + start], end = start + 1;
                    while (end < Diagrams.WIDTH && pixels[row * Diagrams.WIDTH + end] == c) end++;
                    if (c != 0) g.fill(x0 + start * scale, y0 + row * scale, x0 + end * scale, y0 + (row + 1) * scale, c);
                    start = end;
                }
            }
            // Models the drawing holds, seen from the book's usual angle (turning them is for models on their own).
            for (Embedded m : models)
                m.model.draw(g, font, x0 + m.x * scale, y0 + m.y * scale, m.w * scale, m.h * scale, ModelView.YAW, ModelView.PITCH, 1);
            ItemStack hovered = ItemStack.EMPTY;
            float iconScale = scale / 2f;
            for (Icon icon : icons) {
                int ix = x0 + icon.x * scale, iy = y0 + icon.y * scale, size = Math.round(16 * iconScale);
                g.pose().pushPose();
                g.pose().translate(ix, iy, 0);
                g.pose().scale(iconScale, iconScale, 1);
                g.renderItem(icon.stack, 0, 0);
                g.pose().popPose();
                if (mouseX >= ix && mouseX < ix + size && mouseY >= iy && mouseY < iy + size) hovered = icon.stack;
            }
            // Text is sized to the book pixels (a character about 3 wide, a line 5 tall) at every scale, so it lands where
            // the book placed it and never runs into what it was drawn beside.
            float textScale = scale / 2f;
            for (Label label : labels) {
                g.pose().pushPose();
                g.pose().translate(x0 + label.x * scale, y0 + label.y * scale, 200); // above item icons
                g.pose().scale(textScale, textScale, 1);
                int ly = 0;
                for (String part : label.text.split("\n")) {
                    g.drawString(font, part, 0, ly, label.color, false);
                    ly += 10;
                }
                g.pose().popPose();
            }
            y = y0 + height * scale + scale + 2;
            if (!caption.isEmpty()) {
                for (FormattedCharSequence s : font.split(Component.literal(caption), width)) {
                    g.drawString(font, s, left + (width - font.width(s)) / 2, y, PALETTE[1], false);
                    y += font.lineHeight;
                }
            }
            return hovered;
        }
    }

    private DiagramPainter() {
    }

    /** Pixel scale for a page this wide: 2 at the usual size, 1 on narrow screens. */
    static int scale(int width) {
        return Math.max(1, Math.min(3, width / Diagrams.WIDTH));
    }

    /** The drawing described by the book's JSON; a part it can't read is left out rather than failing the page. */
    static Drawing parse(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return new Drawing("", "(a drawing that couldn't be read)", 16);
        }
        int height = Math.max(16, Math.min(Diagrams.MAX_HEIGHT, intOr(o, "height", 60)));
        Drawing d = new Drawing(str(o, "title"), str(o, "caption"), height);
        JsonArray shapes = o.has("shapes") && o.get("shapes").isJsonArray() ? o.getAsJsonArray("shapes") : new JsonArray();
        for (JsonElement e : shapes) {
            try {
                draw(d, e.getAsJsonObject());
            } catch (RuntimeException ignored) {
                // skip the shape
            }
        }
        return d;
    }

    private static void draw(Drawing d, JsonObject s) {
        int color = PALETTE[Math.max(0, Diagrams.COLORS.indexOf(str(s, "color").isEmpty() ? "ink" : str(s, "color")))];
        String fill = str(s, "fill").isEmpty() ? "none" : str(s, "fill");
        int x = intOr(s, "x", 0), y = intOr(s, "y", 0);
        switch (str(s, "kind")) {
            case "line" -> d.line(x, y, intOr(s, "x2", x), intOr(s, "y2", y), color);
            case "arrow" -> d.arrow(x, y, intOr(s, "x2", x), intOr(s, "y2", y), color);
            case "rect" -> d.rect(x, y, Math.min(Diagrams.WIDTH, intOr(s, "w", 2)), Math.min(d.height, intOr(s, "h", 2)), color, fill);
            case "ellipse" -> d.ellipse(x, y, Math.min(Diagrams.WIDTH, intOr(s, "w", 2)), Math.min(d.height, intOr(s, "h", 2)), color, fill);
            case "path" -> {
                JsonArray pts = s.getAsJsonArray("points");
                int[][] p = new int[Math.min(200, pts.size())][];
                for (int i = 0; i < p.length; i++) {
                    JsonArray a = pts.get(i).getAsJsonArray();
                    p[i] = new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
                }
                if (p.length >= 3 && !fill.equals("none")) {
                    d.polygon(p, color, fill);
                } else if (p.length == 1) {
                    d.plot(p[0][0], p[0][1], color);
                } else {
                    for (int i = 0; i + 1 < p.length; i++) d.line(p[i][0], p[i][1], p[i + 1][0], p[i + 1][1], color);
                }
            }
            case "text" -> d.labels.add(new Label(str(s, "text"), x, y, color));
            case "model" -> {
                if (s.has("embedded") && s.get("embedded").isJsonObject())
                    d.models.add(new Embedded(ModelView.parse(s.getAsJsonObject("embedded")), x, y,
                            Math.min(Diagrams.WIDTH - x, intOr(s, "w", 16)), Math.min(d.height - y, intOr(s, "h", 16))));
            }
            case "item" -> {
                ResourceLocation id = ResourceLocation.tryParse(str(s, "item"));
                var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
                d.icons.add(new Icon(new ItemStack(item == Items.AIR ? Items.BARRIER : item), x, y));
            }
            default -> {
            }
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
