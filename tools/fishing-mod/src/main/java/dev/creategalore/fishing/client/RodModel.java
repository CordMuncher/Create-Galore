package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The 3D rod, drawn from boxes like the rest of Minecraft: a handle (grip, reel seat, fore grip), a blank of
 * rectangular sections that taper toward the tip, guides, the reel and the line threaded through the guides.
 *
 * <p>Model space is in pixels (1/16 block): +Y runs from the hand to the tip, -Z is the underside (where a spinning
 * reel hangs and its guides sit; casting and trolling reels sit on top, +Z, with their guides), +X is the angler's
 * right. The origin is where the hand holds the rod, at the reel seat.
 *
 * <p>Fixed-section blanks taper smoothly and show a ferrule at each joint. Telescopic blanks step down a size per
 * section and their sections nest: at {@code extension} 0 only the butt section shows, at 1 they are all out.
 *
 * <p>The bend is a rotation at each of {@link #JOINTS} joints along the blank about one axis, shared out by the
 * blank's action: a fast blank bends near the tip, a slow one along its whole length.
 */
final class RodModel {
    static final ResourceLocation TEXTURE = CreateGaloreFishing.id("textures/entity/tackle.png");
    private static final RenderType SOLID = RenderType.entitySolid(TEXTURE);
    /** Bend joints along the blank. */
    static final int JOINTS = 16;
    private static final int GUIDE_COLOR = 0xFF55565C;
    private static final float ATLAS_W = 64;
    private static final float ATLAS_H = 32;
    private static final int SEAT_COLOR = 0xFF6E6E72;

    /**
     * Everything that changes from frame to frame. {@code bend} is the total angle the tip turns through (radians),
     * {@code depth} 0..1 how far down the blank the bend reaches (heavy loads bend even a fast blank into its middle).
     */
    record Pose(float bend, Vector3f bendAxis, float extension, float crank, float depth) {
        static final Pose REST = new Pose(0, new Vector3f(1, 0, 0), 1, 0, 0);
    }

    /** What the rod is made of, as far as the model cares. */
    record Build(TacklePart.Rod rod, Optional<TacklePart.Reel> reel, Optional<Integer> lineColor) {
    }

    /** Where things ended up, in the space of the pose stack the rod was drawn in. */
    record Result(Vector3f tip, Vector3f tipDirection) {
    }

    /**
     * The surface a box is textured with: a cell of {@code textures/entity/tackle.png} (greyscale, tinted by the
     * box's colour), in the atlas's order.
     */
    enum Mat { PAINT, CORK, FOAM, WOOD, METAL, THREAD, BAMBOO, PLAIN }

    private final MultiBufferSource buffers;
    private final int light;
    private VertexConsumer solid;
    private Mat mat = Mat.PLAIN;

    RodModel(MultiBufferSource buffers, int light) {
        this.buffers = buffers;
        this.light = light;
    }

    /**
     * Draws a rod lying across a screen box centred at (cx, cy), at most {@code width} by {@code height}: the Tackle
     * Bench's preview. Seen three-quarters from below so the reel and guides show; the rod is first drawn into a
     * {@link VertexBounds} at that angle, so it is sized and centred on what it really occupies (reel, crank and all)
     * and never runs out of its box. The crank turns slowly ({@code crank}, radians).
     */
    static void drawInGui(GuiGraphics g, ItemStack stack, TacklePart.Rod rod, int cx, int cy, int width, int height,
                          float crank) {
        RodParts parts = RodItem.parts(stack);
        Build build = new Build(rod, TackleManager.of(parts.reel(), TacklePart.Reel.class),
                TackleManager.of(parts.line(), TacklePart.Line.class).map(l -> color(l.color())));
        Pose state = new Pose(0, new Vector3f(1, 0, 0), 1, crank, 0);

        PoseStack view = new PoseStack();
        previewAngle(view);
        VertexBounds bounds = new VertexBounds();
        new RodModel(bounds, LightTexture.FULL_BRIGHT).draw(view, build, state);
        if (!bounds.any()) return;
        float w = bounds.max[0] - bounds.min[0];
        float h = bounds.max[1] - bounds.min[1];
        float scale = Math.min(width / w, height / h);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 100);
        g.pose().mulPose(new Matrix4f().scaling(scale, scale, -scale));
        g.pose().translate(-(bounds.min[0] + bounds.max[0]) / 2, -(bounds.min[1] + bounds.max[1]) / 2, 0);
        previewAngle(g.pose());
        Lighting.setupForEntityInInventory();
        new RodModel(g.bufferSource(), LightTexture.FULL_BRIGHT).draw(g.pose(), build, state);
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }

    /**
     * The preview's view of the rod, in screen axes before scaling (x right, y down): lying left to right, tilted to
     * show the underside, where a spinning reel and its guides are.
     */
    private static void previewAngle(PoseStack pose) {
        // Screen space is y-down; turn to a y-up frame first.
        pose.mulPose(new Quaternionf().rotateZ(Mth.PI));
        pose.mulPose(Axis.XP.rotationDegrees(20));
        pose.mulPose(Axis.ZP.rotationDegrees(90));
        pose.mulPose(Axis.YP.rotationDegrees(145));
    }

    /** Visual length of the blank in pixels (rods are drawn shorter than life, or they'd fill the screen). */
    static float blankLength(TacklePart.Rod rod) {
        float total = (float) (16 * (0.55 * rod.length() + 0.45));
        return Math.max(8, total - handleLength(rod) - foreGrip(rod));
    }

    private static float handleLength(TacklePart.Rod rod) {
        if (!rod.reel()) return 5;
        float rear = (float) (3.5 + 1.2 * rod.length());
        return rod.maxLoad() >= 40 ? rear + 2.5f : rear;
    }

    private static float foreGrip(TacklePart.Rod rod) {
        return rod.reel() ? 4 : 0;
    }

    /** Half the blank's width at its butt, from how strong the rod is. */
    private static float baseHalfWidth(TacklePart.Rod rod) {
        if (!rod.reel()) return 0.75f;
        return 0.45f + 0.35f * (float) Math.pow(Math.min(rod.maxLoad(), 45) / 45, 0.7);
    }

    /**
     * Draws the rod into {@code pose} (already placed and scaled to pixels) and reports where the tip ended up.
     */
    Result draw(PoseStack pose, Build build, Pose state) {
        solid = buffers.getBuffer(SOLID);
        TacklePart.Rod rod = build.rod();
        int blank = color(rod.color());
        int grip = color(rod.grip());
        Mat gripMat = switch (rod.gripStyle()) {
            case "foam" -> Mat.FOAM;
            case "wood" -> Mat.WOOD;
            case "bamboo" -> Mat.BAMBOO;
            default -> Mat.CORK;
        };
        Mat finish = switch (rod.finish()) {
            case "wood" -> Mat.WOOD;
            case "bamboo" -> Mat.BAMBOO;
            default -> Mat.PAINT;
        };
        boolean top = build.reel().map(r -> r.style() != TacklePart.ReelStyle.SPINNING).orElse(false);
        List<Vector3f> linePath = new ArrayList<>();

        float y0;
        if (rod.reel()) {
            float rear = handleLength(rod);
            mat = Mat.FOAM;
            box(pose, -0.95f, -rear - 0.6f, -0.95f, 0.95f, -rear, 0.95f, shade(grip, 0.65f));
            mat = gripMat;
            box(pose, -0.85f, -rear, -0.85f, 0.85f, -1.2f, 0.85f, grip);
            mat = Mat.METAL;
            box(pose, -0.6f, -1.2f, -0.6f, 0.6f, 1.6f, 0.6f, SEAT_COLOR);
            box(pose, -0.75f, -1.45f, -0.75f, 0.75f, -1.0f, 0.75f, shade(SEAT_COLOR, 0.8f));
            box(pose, -0.75f, 1.4f, -0.75f, 0.75f, 1.85f, 0.75f, shade(SEAT_COLOR, 0.8f));
            mat = gripMat;
            box(pose, -0.75f, 1.85f, -0.75f, 0.75f, 1.85f + foreGrip(rod) - 0.4f, 0.75f, grip);
            y0 = 1.85f + foreGrip(rod) - 0.4f;
            build.reel().ifPresent(reel -> drawReel(pose, reel, build.lineColor(), state.crank(), linePath));
        } else {
            // A pole: the butt is the handle, a little darker where it's held.
            mat = gripMat;
            box(pose, -0.8f, -5, -0.8f, 0.8f, 0, 0.8f, shade(grip, 0.9f));
            y0 = 0;
        }

        // The blank, joint by joint.
        float length = blankLength(rod);
        int sections = Math.max(1, rod.sections());
        boolean tele = rod.telescopic() && sections > 1;
        float sectionLength = tele ? length / sections : length;
        float shown = tele ? sectionLength * (1 + (sections - 1) * Mth.clamp(state.extension(), 0, 1)) : length;
        float base = baseHalfWidth(rod);
        float tipHalf = 0.38f * base + 0.02f;

        float[] weights = bendWeights(rod.action(), state.depth());
        pose.pushPose();
        pose.translate(0, y0, 0);
        Vector3f axis = state.bendAxis();
        float jointGap = shown / JOINTS;
        List<Float> breaks = breakpoints(shown, jointGap, tele, sectionLength, sections, state.extension(), length);
        List<Fitting> fittings = fittings(shown, tele, sectionLength, sections, state.extension(), rod.reel());
        float segStart = 0;
        int joint = 1;
        for (int i = 0; i + 1 < breaks.size(); i++) {
            float a = breaks.get(i);
            float b = breaks.get(i + 1);
            float mid = (a + b) / 2;
            float half = tele ? teleHalf(mid, sectionLength, sections, state.extension(), base, tipHalf)
                    : Mth.lerp(mid / shown, base, tipHalf);
            mat = finish;
            box(pose, -half, a - segStart, -half, half, b - segStart, half, blank);
            // Ferrules, nodes and guides on this piece, before the frame turns at its end.
            for (Fitting f : fittings) {
                if (f.at() < a || f.at() >= b) continue;
                float h = tele ? teleHalf(f.at() - 0.01f, sectionLength, sections, state.extension(), base, tipHalf)
                        : Mth.lerp(f.at() / shown, base, tipHalf);
                float y = f.at() - segStart;
                mat = tele ? Mat.METAL : finish;
                switch (f.kind()) {
                    case FERRULE -> box(pose, -h - 0.14f, y - 0.35f, -h - 0.14f, h + 0.14f, y + 0.35f, h + 0.14f,
                            shade(blank, 0.75f));
                    case NODE -> box(pose, -h - 0.12f, y - 0.25f, -h - 0.12f, h + 0.12f, y + 0.25f, h + 0.12f,
                            shade(blank, 0.78f));
                    case GUIDE -> linePath.add(guide(pose, y, h, f.size(), top));
                }
            }
            while (joint < JOINTS && b >= joint * jointGap - 1e-4f) {
                pose.translate(0, b - segStart, 0);
                segStart = b;
                pose.mulPose(new Quaternionf().fromAxisAngleRad(axis, state.bend() * weights[joint]));
                joint++;
            }
        }
        // Tip top.
        mat = Mat.METAL;
        float tipY = shown - segStart;
        box(pose, -tipHalf - 0.15f, tipY - 0.5f, -tipHalf - 0.15f, tipHalf + 0.15f, tipY, tipHalf + 0.15f, GUIDE_COLOR);
        Vector3f tip = point(pose, 0, tipY, 0);
        Vector3f tipDir = direction(pose, 0, 1, 0);
        pose.popPose();
        linePath.add(tip);
        build.lineColor().ifPresent(c -> lineAlong(pose, linePath, c));
        return new Result(tip, tipDir);
    }

    /** Where the blank is cut into pieces: every bend joint, every section end, and the tip. */
    private static List<Float> breakpoints(float shown, float jointGap, boolean tele, float sectionLength, int sections,
                                           float extension, float length) {
        List<Float> out = new ArrayList<>();
        for (int j = 0; j <= JOINTS; j++) out.add(j * jointGap);
        if (tele) {
            for (int s = 0; s < sections - 1; s++) out.add(sectionEnd(s, sectionLength, extension));
        }
        out.removeIf(v -> v > shown + 1e-4f);
        out.sort(Float::compare);
        List<Float> unique = new ArrayList<>();
        for (float v : out) {
            if (unique.isEmpty() || v - unique.get(unique.size() - 1) > 0.05f) unique.add(v);
        }
        if (unique.get(unique.size() - 1) < shown - 0.05f) unique.add(shown);
        return unique;
    }

    /** Where telescopic section {@code s} stops showing (the next, thinner one takes over). */
    private static float sectionEnd(int s, float sectionLength, float extension) {
        return s * sectionLength * Mth.clamp(extension, 0, 1) + sectionLength;
    }

    /** Half width of a telescopic blank at {@code at}: the outermost section covering that point. */
    private static float teleHalf(float at, float sectionLength, int sections, float extension, float base, float tip) {
        int s = 0;
        while (s < sections - 1 && at >= sectionEnd(s, sectionLength, extension)) s++;
        return Mth.lerp(s / (float) (sections - 1), base, tip);
    }

    private enum FittingKind { FERRULE, NODE, GUIDE }

    private record Fitting(float at, FittingKind kind, float size) {
    }

    /** Ferrules (fixed sections) and guides along the blank. */
    private static List<Fitting> fittings(float shown, boolean tele, float sectionLength, int sections, float extension,
                                          boolean reel) {
        List<Fitting> out = new ArrayList<>();
        if (tele) {
            for (int s = 0; s < sections - 1; s++) {
                float end = sectionEnd(s, sectionLength, extension);
                out.add(new Fitting(end - 0.3f, FittingKind.FERRULE, 0));
                out.add(new Fitting(end - 0.45f, FittingKind.GUIDE, Mth.lerp(s / (float) (sections - 1), 1.0f, 0.45f)));
            }
            return out;
        }
        if (!reel) {
            // A bamboo pole: nodes every few pixels instead of guides (the line is tied to the tip).
            for (float at = 4; at < shown - 1; at += 4.5f) out.add(new Fitting(at, FittingKind.NODE, 0));
            return out;
        }
        for (int s = 1; s < sections; s++) out.add(new Fitting(shown * s / sections, FittingKind.FERRULE, 0));
        int guides = Math.min(8, 4 + sections);
        for (int g = 0; g < guides; g++) {
            float t = g / (float) guides;
            float at = shown * (0.12f + 0.84f * (float) Math.pow(t, 0.8));
            out.add(new Fitting(at, FittingKind.GUIDE, Mth.lerp(t, 1.05f, 0.42f)));
        }
        return out;
    }

    /**
     * Share of the bend at each joint: toward the tip for a fast blank, spread out for a slow one, and reaching
     * further down the blank as {@code depth} (the load) rises, the way a rod "loads up" into its butt.
     */
    private static float[] bendWeights(TacklePart.Action action, float depth) {
        double p = switch (action) {
            case FAST -> 2.4;
            case MODERATE -> 1.3;
            case SLOW -> 0.6;
        } * (1 - 0.55 * Mth.clamp(depth, 0, 1));
        float[] w = new float[JOINTS];
        double sum = 0;
        for (int j = 1; j < JOINTS; j++) sum += Math.pow(j / (double) JOINTS, p);
        for (int j = 1; j < JOINTS; j++) w[j] = (float) (Math.pow(j / (double) JOINTS, p) / sum);
        return w;
    }

    /** A guide ring standing off the blank, square like everything else. Returns its centre (for the line). */
    private Vector3f guide(PoseStack pose, float y, float blankHalf, float size, boolean top) {
        Mat was = mat;
        mat = Mat.METAL;
        float r = size;
        float t = 0.2f;
        float side = top ? 1 : -1;
        float near = blankHalf + 0.35f;
        float centre = side * (near + r);
        float z0 = centre - r;
        float z1 = centre + r;
        // Stem from the blank to the ring.
        float stemFrom = side * blankHalf;
        float stemTo = side * near;
        box(pose, -0.12f, y - 0.25f, Math.min(stemFrom, stemTo), 0.12f, y + 0.25f, Math.max(stemFrom, stemTo), GUIDE_COLOR);
        box(pose, -0.3f, y - 0.6f, top ? blankHalf : -blankHalf - 0.08f, 0.3f, y + 0.6f, top ? blankHalf + 0.08f : -blankHalf,
                GUIDE_COLOR);
        // The ring, in the plane across the blank.
        box(pose, -r, y - 0.1f, z0, -r + t, y + 0.1f, z1, GUIDE_COLOR);
        box(pose, r - t, y - 0.1f, z0, r, y + 0.1f, z1, GUIDE_COLOR);
        box(pose, -r + t, y - 0.1f, z0, r - t, y + 0.1f, z0 + t, GUIDE_COLOR);
        box(pose, -r + t, y - 0.1f, z1 - t, r - t, y + 0.1f, z1, GUIDE_COLOR);
        mat = was;
        return point(pose, 0, y, centre);
    }

    /** The reel on its seat, with the crank turned to {@code crank} radians; adds where the line leaves the spool. */
    private void drawReel(PoseStack pose, TacklePart.Reel reel, Optional<Integer> lineColor, float crank,
                          List<Vector3f> linePath) {
        int body = color(reel.color());
        int dark = shade(body, 0.7f);
        int spool = lineColor.orElse(shade(body, 1.15f));
        Mat was = mat;
        mat = Mat.METAL;
        switch (reel.style()) {
            case SPINNING -> {
                box(pose, -0.45f, -1.0f, -0.75f, 0.45f, 1.4f, -0.6f, dark);
                box(pose, -0.25f, -0.6f, -2.4f, 0.25f, 0.9f, -0.75f, dark);
                box(pose, -1.0f, -1.0f, -4.2f, 1.0f, 1.4f, -2.4f, body);
                box(pose, -1.25f, 1.4f, -4.55f, 1.25f, 2.0f, -2.05f, dark);
                box(pose, -0.95f, 2.0f, -4.25f, 0.95f, 3.6f, -2.35f, shade(body, 1.1f));
                mat = Mat.THREAD;
                box(pose, -1.05f, 2.2f, -4.35f, 1.05f, 3.3f, -2.25f, spool);
                mat = Mat.METAL;
                box(pose, -0.7f, 3.6f, -3.95f, 0.7f, 3.9f, -2.65f, shade(body, 1.2f));
                // Bail arm across the spool's front.
                box(pose, -1.35f, 1.6f, -4.75f, -1.15f, 2.6f, -4.55f, GUIDE_COLOR);
                box(pose, 1.15f, 1.6f, -4.75f, 1.35f, 2.6f, -4.55f, GUIDE_COLOR);
                box(pose, -1.35f, 2.6f, -4.75f, 1.35f, 2.8f, -4.55f, GUIDE_COLOR);
                crank(pose, -1.0f, 0.2f, -3.3f, -1, crank, 1.6f, body, false);
                linePath.add(point(pose, 0, 3.3f, -2.3f));
            }
            case BAITCASTER -> {
                box(pose, -1.6f, -1.2f, 0.5f, -1.35f, 2.0f, 2.6f, dark);
                box(pose, 1.35f, -1.2f, 0.5f, 1.6f, 2.0f, 2.6f, dark);
                box(pose, -1.35f, -1.0f, 0.6f, 1.35f, 1.8f, 2.4f, body);
                mat = Mat.THREAD;
                box(pose, -1.1f, 0.1f, 2.35f, 1.1f, 1.2f, 2.5f, spool);
                mat = Mat.METAL;
                box(pose, -1.2f, 1.8f, 0.8f, 1.2f, 2.2f, 1.6f, dark);
                crank(pose, 1.6f, 0.2f, 1.5f, 1, crank, 1.4f, body, true);
                linePath.add(point(pose, 0, 1.9f, 1.5f));
            }
            case TROLLING -> {
                box(pose, -2.3f, -1.6f, 0.5f, -2.0f, 2.6f, 4.4f, dark);
                box(pose, 2.0f, -1.6f, 0.5f, 2.3f, 2.6f, 4.4f, dark);
                box(pose, -2.0f, -1.4f, 0.6f, 2.0f, 2.4f, 4.2f, body);
                mat = Mat.THREAD;
                box(pose, -1.6f, 0.0f, 4.15f, 1.6f, 2.0f, 4.35f, spool);
                mat = Mat.METAL;
                // Lever drag on the left.
                box(pose, -2.7f, 0.3f, 2.8f, -2.3f, 0.7f, 3.2f, dark);
                box(pose, -2.65f, 0.4f, 3.2f, -2.35f, 0.6f, 5.0f, shade(body, 1.15f));
                crank(pose, 2.3f, 0.4f, 2.4f, 1, crank, 2.0f, body, false);
                linePath.add(point(pose, 0, 2.4f, 2.4f));
            }
        }
        mat = was;
    }

    /**
     * A crank handle on the {@code side} (-1 left, +1 right) at (x, y, z), turned {@code angle} about the reel's
     * axle (X). {@code paddles}: a baitcaster's double handle.
     */
    private void crank(PoseStack pose, float x, float y, float z, int side, float angle, float arm, int color,
                       boolean paddles) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotation(angle));
        float s = side;
        box(pose, Math.min(0, s * 0.5f), -0.2f, -0.2f, Math.max(0, s * 0.5f), 0.2f, 0.2f, shade(color, 0.8f));
        float ax0 = Math.min(s * 0.5f, s * 0.8f);
        float ax1 = Math.max(s * 0.5f, s * 0.8f);
        box(pose, ax0, -0.2f, -0.2f, ax1, arm, 0.2f, color);
        if (paddles) box(pose, ax0, -arm, -0.2f, ax1, 0.2f, 0.2f, color);
        float k0 = Math.min(s * 0.8f, s * 1.5f);
        float k1 = Math.max(s * 0.8f, s * 1.5f);
        Mat was = mat;
        mat = Mat.FOAM;
        box(pose, k0, arm - 0.4f, -0.4f, k1, arm + 0.4f, 0.4f, 0xFF2A2A2A);
        if (paddles) box(pose, k0, -arm - 0.4f, -0.4f, k1, -arm + 0.4f, 0.4f, 0xFF2A2A2A);
        mat = was;
        pose.popPose();
    }

    /** The line through the guides to the tip, in the space {@code pose} was given in. */
    private void lineAlong(PoseStack pose, List<Vector3f> path, int color) {
        if (path.size() < 2) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose root = pose.last();
        Matrix4f inverse = root.pose().invert(new Matrix4f());
        Matrix3f back = root.normal().transpose(new Matrix3f());
        for (int i = 0; i + 1 < path.size(); i++) {
            Vector3f a = path.get(i);
            Vector3f b = path.get(i + 1);
            Vector3f n = new Vector3f(b).sub(a);
            if (n.lengthSquared() < 1e-8f) continue;
            n.normalize();
            // Points are already in the root space; undo the root pose for the vertices.
            Vector3f la = inverse.transformPosition(new Vector3f(a));
            Vector3f lb = inverse.transformPosition(new Vector3f(b));
            Vector3f ln = back.transform(new Vector3f(n)).normalize();
            lines.addVertex(root, la.x, la.y, la.z).setColor(color).setNormal(root, ln.x, ln.y, ln.z);
            lines.addVertex(root, lb.x, lb.y, lb.z).setColor(color).setNormal(root, ln.x, ln.y, ln.z);
        }
        solid = buffers.getBuffer(SOLID);
    }

    private static Vector3f point(PoseStack pose, float x, float y, float z) {
        return pose.last().pose().transformPosition(x, y, z, new Vector3f());
    }

    private static Vector3f direction(PoseStack pose, float x, float y, float z) {
        return pose.last().pose().transformDirection(x, y, z, new Vector3f()).normalize();
    }

    /** Sets the surface for the boxes drawn next. */
    RodModel material(Mat m) {
        mat = m;
        return this;
    }

    /** One box, with a face texture scaled so a texel is about a model pixel. */
    void box(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        if (solid == null) solid = buffers.getBuffer(SOLID);
        PoseStack.Pose p = pose.last();
        // -Y, +Y
        quad(p, argb, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x1 - x0, z1 - z0);
        quad(p, argb, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x1 - x0, z1 - z0);
        // -Z, +Z
        quad(p, argb, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1 - x0, y1 - y0);
        quad(p, argb, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x1 - x0, y1 - y0);
        // -X, +X
        quad(p, argb, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, z1 - z0, y1 - y0);
        quad(p, argb, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, z1 - z0, y1 - y0);
    }

    private void quad(PoseStack.Pose p, int argb, float nx, float ny, float nz,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz, float w, float h) {
        // A window of the material's 16x16 cell, about a texel per model pixel, placed by position so neighbouring
        // boxes don't repeat the same patch.
        int cell = mat.ordinal();
        float sw = Math.min(w, 15.9f);
        float sh = Math.min(h, 15.9f);
        float ou = Mth.positiveModulo(ax * 1.7f + az * 2.3f + ay * 0.37f, 16 - sw);
        float ov = Mth.positiveModulo(ay * 1.3f + ax * 0.7f + az * 0.9f, 16 - sh);
        float u0 = ((cell % 4) * 16 + ou) / ATLAS_W;
        float v0 = ((cell / 4) * 16 + ov) / ATLAS_H;
        float u1 = u0 + sw / ATLAS_W;
        float v1 = v0 + sh / ATLAS_H;
        vertex(p, argb, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(p, argb, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(p, argb, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(p, argb, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private void vertex(PoseStack.Pose p, int argb, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        solid.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, nx, ny, nz);
    }

    /** "#rrggbb" to opaque ARGB; grey if it doesn't parse. */
    static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (NumberFormatException e) {
            return 0xFF808080;
        }
    }

    static int shade(int argb, float f) {
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * f), 0, 255);
        int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * f), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) * f), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
