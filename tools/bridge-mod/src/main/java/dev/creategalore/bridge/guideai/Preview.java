package dev.creategalore.bridge.guideai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

/**
 * Pictures of a draft model for the model itself to look at before it shows the build: two corner views and the
 * front and side, drawn on the server from each block's real shape (stairs, slabs and fences keep their shape) in its
 * map colour. Plain shading, no textures: enough to judge the shape, proportions and colours of a build.
 */
final class Preview {
    private static final int VIEW = 360;
    private static final Color BACK = new Color(0xF2EAD8), INK = new Color(0x5A4A32);

    private Preview() {
    }

    /** A PNG with four views of the cells, or null if it couldn't be drawn. */
    static byte[] render(Map<BlockPos, BlockState> cells, int sx, int sy, int sz) {
        return render(cells, sx, sy, sz, null);
    }

    /**
     * The four views, and with {@code moved} four more below them of the build with its moving parts at the end of
     * their motion (which may reach outside the model's box, so those views are framed on everything).
     */
    static byte[] render(Map<BlockPos, BlockState> cells, int sx, int sy, int sz, Map<BlockPos, BlockState> moved) {
        try {
            BufferedImage img = new BufferedImage(VIEW * 2, VIEW * (moved == null ? 2 : 4), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACK);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            views(g, boxes(cells), sx, sy, sz, 0, "");
            if (moved != null) {
                int x0 = 0, y0 = 0, z0 = 0, x1 = sx, y1 = sy, z1 = sz;
                for (BlockPos p : moved.keySet()) {
                    x0 = Math.min(x0, p.getX());
                    y0 = Math.min(y0, p.getY());
                    z0 = Math.min(z0, p.getZ());
                    x1 = Math.max(x1, p.getX() + 1);
                    y1 = Math.max(y1, p.getY() + 1);
                    z1 = Math.max(z1, p.getZ() + 1);
                }
                Map<BlockPos, BlockState> shifted = new java.util.HashMap<>();
                for (Map.Entry<BlockPos, BlockState> e : moved.entrySet()) shifted.put(e.getKey().offset(-x0, -y0, -z0), e.getValue());
                views(g, boxes(shifted), x1 - x0, y1 - y0, z1 - z0, VIEW * 2, "moved: ");
                g.setColor(INK);
                g.fillRect(0, VIEW * 2 - 1, VIEW * 2, 3);
            }
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** One part of a block's shape, in model coordinates, with the block's colour. */
    private record Box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, boolean glassy) {
    }

    private static void views(Graphics2D g, List<Box> boxes, int sx, int sy, int sz, int oy, String prefix) {
        iso(g, boxes, sx, sy, sz, false, 0, oy, prefix + "front-left corner");
        iso(g, boxes, sx, sy, sz, true, VIEW, oy, prefix + "back-right corner");
        flat(g, boxes, sx, sy, sz, true, 0, oy + VIEW, prefix + "front (looking towards -z)");
        flat(g, boxes, sx, sy, sz, false, VIEW, oy + VIEW, prefix + "right side (looking towards -x)");
        g.setColor(INK);
        g.drawLine(VIEW, oy, VIEW, oy + VIEW * 2);
        g.drawLine(0, oy + VIEW, VIEW * 2, oy + VIEW);
    }

    private static List<Box> boxes(Map<BlockPos, BlockState> cells) {
        List<Box> out = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockPos p = e.getKey();
            BlockState s = e.getValue();
            int rgb;
            boolean glassy = false;
            try {
                MapColor c = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                rgb = c == null ? 0 : c.col;
            } catch (RuntimeException ex) {
                rgb = 0;
            }
            if (rgb == 0) {
                rgb = 0xCFE3EA;
                glassy = true;
            }
            List<AABB> shape;
            try {
                shape = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
            } catch (RuntimeException ex) {
                shape = List.of(new AABB(0, 0, 0, 1, 1, 1));
            }
            if (shape.isEmpty()) {
                if (s.getFluidState().isEmpty()) continue;
                shape = List.of(new AABB(0, 0, 0, 1, Math.max(0.1, s.getFluidState().getOwnHeight()), 1));
                glassy = true;
            }
            for (AABB b : shape)
                out.add(new Box(p.getX() + b.minX, p.getY() + b.minY, p.getZ() + b.minZ, p.getX() + b.maxX, p.getY() + b.maxY,
                        p.getZ() + b.maxZ, rgb, glassy));
        }
        return out;
    }

    private static Color shade(int rgb, double f, boolean glassy) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f),
                b = (int) Math.min(255, (rgb & 255) * f);
        return new Color(r, g, b, glassy ? 150 : 255);
    }

    /**
     * An isometric view from above a corner: the front-left one (camera at -x, +z) or the back-right one (+x, -z, drawn
     * by turning the model half round). Boxes are drawn far to near, so nearer ones cover what is behind them.
     */
    private static void iso(Graphics2D g, List<Box> boxes, int sx, int sy, int sz, boolean backRight, int ox, int oy,
            String name) {
        List<Box> view = new ArrayList<>();
        for (Box b : boxes) {
            view.add(backRight ? new Box(sx - b.x1, b.y0, sz - b.z1, sx - b.x0, b.y1, sz - b.z0, b.rgb, b.glassy) : b);
        }
        // Screen position of (x, y, z): u = -(x + z) * 0.866 across, v = (z - x) * 0.5 - y down. Fit the model's box.
        double uMin = -(sx + sz) * 0.866, uMax = 0, vMin = -sx * 0.5 - sy, vMax = sz * 0.5;
        double scale = Math.min((VIEW - 24) / (uMax - uMin), (VIEW - 36) / (vMax - vMin));
        double cx = ox + (VIEW - (uMax - uMin) * scale) / 2 - uMin * scale, cy = oy + 26 + (VIEW - 36 - (vMax - vMin) * scale) / 2 - vMin * scale;
        // Far to near: depth towards the camera is (z - x) + y.
        view.sort(Comparator.comparingDouble((Box b) -> (b.z0 + b.z1) / 2 - (b.x0 + b.x1) / 2 + (b.y0 + b.y1) / 2));
        for (Box b : view) {
            double[] top = {b.x0, b.y1, b.z0, b.x1, b.y1, b.z0, b.x1, b.y1, b.z1, b.x0, b.y1, b.z1};
            double[] left = {b.x0, b.y0, b.z0, b.x0, b.y0, b.z1, b.x0, b.y1, b.z1, b.x0, b.y1, b.z0};
            double[] front = {b.x0, b.y0, b.z1, b.x1, b.y0, b.z1, b.x1, b.y1, b.z1, b.x0, b.y1, b.z1};
            face(g, left, cx, cy, scale, shade(b.rgb, 0.62, b.glassy));
            face(g, front, cx, cy, scale, shade(b.rgb, 0.8, b.glassy));
            face(g, top, cx, cy, scale, shade(b.rgb, 1.0, b.glassy));
        }
        g.setColor(INK);
        g.drawString(name, ox + 8, oy + 14);
    }

    private static void face(Graphics2D g, double[] pts, double cx, double cy, double scale, Color c) {
        Polygon poly = new Polygon();
        for (int i = 0; i < pts.length; i += 3) {
            double x = pts[i], y = pts[i + 1], z = pts[i + 2];
            poly.addPoint((int) Math.round(cx - (x + z) * 0.866 * scale), (int) Math.round(cy + ((z - x) * 0.5 - y) * scale));
        }
        g.setColor(c);
        g.fillPolygon(poly);
        g.setColor(new Color(0, 0, 0, 40));
        g.drawPolygon(poly);
    }

    /** A straight-on view: from the front (+z, x across) or the right side (+x, z across); nearer boxes on top. */
    private static void flat(Graphics2D g, List<Box> boxes, int sx, int sy, int sz, boolean front, int ox, int oy,
            String name) {
        int across = front ? sx : sz;
        double scale = Math.min((VIEW - 20) / (double) across, (VIEW - 34) / (double) sy);
        double left = ox + (VIEW - across * scale) / 2, bottom = oy + VIEW - 8;
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(b -> front ? b.z1 : b.x1));
        for (Box b : sorted) {
            double a0 = front ? b.x0 : sz - b.z1, a1 = front ? b.x1 : sz - b.z0;
            double depth = front ? b.z1 / Math.max(1, sz) : b.x1 / Math.max(1, sx);
            int x = (int) Math.round(left + a0 * scale), w = Math.max(1, (int) Math.round((a1 - a0) * scale));
            int y = (int) Math.round(bottom - b.y1 * scale), h = Math.max(1, (int) Math.round((b.y1 - b.y0) * scale));
            g.setColor(shade(b.rgb, 0.55 + 0.45 * depth, b.glassy));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0, 0, 0, 40));
            g.drawRect(x, y, w, h);
        }
        g.setColor(INK);
        g.drawString(name, ox + 8, oy + 14);
    }
}
