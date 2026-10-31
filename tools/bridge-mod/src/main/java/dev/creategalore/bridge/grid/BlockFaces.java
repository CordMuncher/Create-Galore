package dev.creategalore.bridge.grid;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Where each block state's faces find their texture in the block atlas, for a pack that draws the grid's blocks itself
 * (reflections, say): a pack that declares the block atlas as a custom texture (minecraft:textures/atlas/blocks.png)
 * and its palette image {@link #ROWS} rows taller than the palette and the holes ({@link BlockHoles}) gets them in
 * those rows; a pack that doesn't, or a Bridge without it, reads 0 there, which means "no textures".
 *
 * <p>Each palette entry has {@link #WORDS} words, at its entry number times {@link #WORDS}:
 * <ol start="0">
 * <li>for each of the six sides in Minecraft's order (down, up, north, south, west, east), five words:
 * <ul>
 * <li>its sprite's place in the atlas: its low corner (u in bits 0..15, v in 16..31) and its high corner (the same),
 * each in 65536ths of the atlas;</li>
 * <li>how a point on that side finds its texel in the sprite, s = s0 + s1 a + s2 b and t = t0 + t1 a + t2 b, with s and
 * t across the sprite (0..1, s along u) and a and b the point's place in the block (0..1) along the two axes of the side
 * (a the one after the side's own axis, y for x, z for y, x for z; b the one after that): (s0, s1), (s2, t0) and
 * (t1, t2) as pairs of half floats;</li>
 * </ul></li>
 * <li>word 30: which sides have a texture (bit 0 down .. bit 5 east), and which of them are tinted (bits 8..13);</li>
 * <li>word 31: the tint, sRGB with red in the low byte, as the block's colour handler gives it without a place (the
 * default grass, leaf and water colours). Air's (entry 0) holds {@link #MARK} instead, so a pack can tell the rows are
 * written.</li>
 * </ol>
 * A side's texture is its model's biggest face on that side, square to it (the first of equal ones: a grass block's
 * side, not its tinted overlay). Faces drawn tilted (a torch on a wall, a flower's cross) give none, so a pack keeps
 * the entry's average colour there. Water and lava take their still texture on every side.
 */
final class BlockFaces {
    static final int WORDS = 32;
    static final int ROWS = BlockPalette.ENTRIES * WORDS / BlockPalette.ROW;
    /** In air's word 31: "UVS1", the rows hold this layout. */
    static final int MARK = 0x55565331;
    private static final int SIDE_WORDS = 5;

    private final int[] words = new int[BlockPalette.ENTRIES * WORDS];
    private int dirtyFrom = 0;
    private int dirtyTo = 0;

    BlockFaces() {
        words[WORDS - 1] = MARK;
        markAllDirty();
    }

    int[] words() {
        return words;
    }

    /** The rows changed since the last upload, low inclusive and high exclusive; empty when none. */
    int dirtyRowFrom() {
        return dirtyFrom * WORDS / BlockPalette.ROW;
    }

    int dirtyRowTo() {
        return dirtyTo <= dirtyFrom ? dirtyRowFrom() : ((dirtyTo * WORDS) + BlockPalette.ROW - 1) / BlockPalette.ROW;
    }

    void markUploaded() {
        dirtyFrom = 0;
        dirtyTo = 0;
    }

    /** Marks every entry to upload again (a new image). */
    void markAllDirty() {
        dirtyFrom = 0;
        dirtyTo = BlockPalette.ENTRIES;
    }

    private void touch(int entry) {
        if (dirtyTo <= dirtyFrom) {
            dirtyFrom = entry;
            dirtyTo = entry + 1;
        } else {
            dirtyFrom = Math.min(dirtyFrom, entry);
            dirtyTo = Math.max(dirtyTo, entry + 1);
        }
    }

    /**
     * Records a state's faces from its model's quads. tints[i] is quads[i]'s tint (sRGB, red low), or -1 when the quad
     * is untinted.
     */
    void describe(int entry, List<BakedQuad> quads, int[] tints) {
        int at = entry * WORDS;
        java.util.Arrays.fill(words, at, at + WORDS, 0);
        int flags = 0;
        int tint = 0;
        for (Direction d : Direction.values()) {
            int side = d.get3DDataValue();
            int axis = d.getAxis().ordinal();
            int best = -1;
            float bestArea = 0.0f;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                if (quad.getDirection() != d) {
                    continue;
                }
                float area = squareArea(quad, axis);
                if (area > bestArea + 1e-6f) {
                    best = i;
                    bestArea = area;
                }
            }
            if (best < 0 || !write(at + side * SIDE_WORDS, quads.get(best), axis)) {
                continue;
            }
            flags |= 1 << side;
            if (tints[best] != -1) {
                flags |= 1 << (8 + side);
                tint = tints[best];
            }
        }
        words[at + 30] = flags;
        words[at + 31] = entry == 0 ? MARK : tint;
        touch(entry);
    }

    /** Water's and lava's still texture on every side, tinted by tint (sRGB, red low) or untinted when -1. */
    void describeFluid(int entry, TextureAtlasSprite sprite, int tint) {
        int at = entry * WORDS;
        java.util.Arrays.fill(words, at, at + WORDS, 0);
        int flags = 0;
        for (int side = 0; side < 6; side++) {
            int w = at + side * SIDE_WORDS;
            words[w] = corner(sprite.getU0(), sprite.getV0());
            words[w + 1] = corner(sprite.getU1(), sprite.getV1());
            // s along a, t along b: the texture laid flat on each side.
            words[w + 2] = halves(0.0f, 1.0f);
            words[w + 3] = halves(0.0f, 0.0f);
            words[w + 4] = halves(0.0f, 1.0f);
            flags |= 1 << side;
            if (tint != -1) {
                flags |= 1 << (8 + side);
            }
        }
        words[at + 30] = flags;
        words[at + 31] = tint == -1 ? 0 : tint;
        touch(entry);
    }

    /** A quad's area across its own axis (0 when tilted). */
    private static float squareArea(BakedQuad quad, int axis) {
        int[] v = quad.getVertices();
        int stride = v.length / 4;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) {
                float c = Float.intBitsToFloat(v[i * stride + k]);
                lo[k] = Math.min(lo[k], c);
                hi[k] = Math.max(hi[k], c);
            }
        }
        if (hi[axis] - lo[axis] > 1e-3f) {
            return 0.0f;
        }
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        return (hi[a] - lo[a]) * (hi[b] - lo[b]);
    }

    /**
     * Writes one side's five words from its quad: the sprite's corners, and the plane that takes the quad's corners (a, b)
     * to their place in the sprite (s, t), fitted to all four by least squares (exact for any quad Minecraft bakes, which
     * maps its corners straight or turned).
     */
    private boolean write(int w, BakedQuad quad, int axis) {
        TextureAtlasSprite sprite = quad.getSprite();
        float du = sprite.getU1() - sprite.getU0();
        float dv = sprite.getV1() - sprite.getV0();
        if (Math.abs(du) < 1e-9f || Math.abs(dv) < 1e-9f) {
            return false;
        }
        int[] v = quad.getVertices();
        int stride = v.length / 4;
        int ia = (axis + 1) % 3;
        int ib = (axis + 2) % 3;
        // Normal equations for s = c0 + c1 a + c2 b (and t), over the four corners.
        double[][] m = new double[3][3];
        double[] rs = new double[3];
        double[] rt = new double[3];
        for (int i = 0; i < 4; i++) {
            double a = Float.intBitsToFloat(v[i * stride + ia]);
            double b = Float.intBitsToFloat(v[i * stride + ib]);
            double s = (Float.intBitsToFloat(v[i * stride + 4]) - sprite.getU0()) / du;
            double t = (Float.intBitsToFloat(v[i * stride + 5]) - sprite.getV0()) / dv;
            double[] row = {1.0, a, b};
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    m[r][c] += row[r] * row[c];
                }
                rs[r] += row[r] * s;
                rt[r] += row[r] * t;
            }
        }
        double[] cs = solve(m, rs);
        double[] ct = solve(m, rt);
        if (cs == null || ct == null) {
            return false;
        }
        words[w] = corner(sprite.getU0(), sprite.getV0());
        words[w + 1] = corner(sprite.getU1(), sprite.getV1());
        words[w + 2] = halves((float) cs[0], (float) cs[1]);
        words[w + 3] = halves((float) cs[2], (float) ct[0]);
        words[w + 4] = halves((float) ct[1], (float) ct[2]);
        return true;
    }

    /** Solves a 3 x 3 system by Cramer's rule; null when it has no single answer (a quad of zero area). */
    private static double[] solve(double[][] m, double[] r) {
        double det = det3(m[0][0], m[0][1], m[0][2], m[1][0], m[1][1], m[1][2], m[2][0], m[2][1], m[2][2]);
        if (Math.abs(det) < 1e-12) {
            return null;
        }
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) {
            double[][] c = {m[0].clone(), m[1].clone(), m[2].clone()};
            for (int i = 0; i < 3; i++) {
                c[i][k] = r[i];
            }
            out[k] = det3(c[0][0], c[0][1], c[0][2], c[1][0], c[1][1], c[1][2], c[2][0], c[2][1], c[2][2]) / det;
        }
        return out;
    }

    private static double det3(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    }

    /** An atlas point in 65536ths, u low and v high. */
    private static int corner(float u, float v) {
        int iu = Math.max(0, Math.min(65535, Math.round(u * 65536.0f)));
        int iv = Math.max(0, Math.min(65535, Math.round(v * 65536.0f)));
        return iu | (iv << 16);
    }

    /** Two half floats, x low (GLSL's packHalf2x16). */
    private static int halves(float x, float y) {
        return (Float.floatToFloat16(x) & 0xffff) | ((Float.floatToFloat16(y) & 0xffff) << 16);
    }
}
