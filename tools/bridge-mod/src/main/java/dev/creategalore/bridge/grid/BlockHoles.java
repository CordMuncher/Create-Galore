package dev.creategalore.bridge.grid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The holes in block states' cut-out faces, for a pack's light to pass through: a trapdoor's windows, a grate's slots,
 * a ladder's gaps, the clear middle of a glass block. It goes to the rows of cg_palette_img after the palette's own
 * (a pack that wants it declares the image {@link #ROWS} rows taller); a pack that doesn't, or a Bridge without it,
 * leaves those rows at 0, which means "no holes".
 *
 * <p>For each box of a state's shape and each axis, a mask of its two faces across that axis: 16 x 16 cells in 16ths
 * of the block, a cell open where every face of the model that lies across that axis inside the box (the box's own two
 * faces, and any drawn between them) shows a clear texel there (alpha 0.1 or less), and closed where any shows one
 * that isn't, or where none is drawn at all. So holes come only from what a texture says is clear; a face the model
 * leaves undrawn, or draws tilted (a chain's), stops light as before.
 *
 * <p>Layout, in words (4096 a row):
 * <ul>
 * <li>[0, 65536): per palette entry, the word where its table starts; 0 for none (no holes anywhere). Air's word (0)
 * holds {@link #MARK} instead, so a pack can tell the rows are written;</li>
 * <li>[65536, 131072): tables, two words per box in shape order: the masks across x (bits 0..15) and y (16..31),
 * then across z (0..15); mask 0 is a closed face;</li>
 * <li>[131072, 196608): masks, 8 words each. Of the two axes along the faces, a is the one after the faces' own
 * (y for x, z for y, x for z) and b the one after that (z for x, x for y, y for z): a row of 16 cells runs along a
 * (cell i in bit i), the rows go along b, two a word (the even row in bits 0..15, the odd one in 16..31). A set bit is
 * open.</li>
 * </ul>
 * Tables and masks are shared by every state that has the same ones.
 */
final class BlockHoles {
    static final int ROWS = 48;
    /** In word 0 (air, which has no holes): "HOL1", the rows hold this layout. */
    static final int MARK = 0x484f4c31;
    static final int WORDS = BlockPalette.ROW * ROWS;
    private static final int TABLES = 65536;
    private static final int MASKS = 131072;
    private static final int MASK_WORDS = 8;

    private final int[] words = new int[WORDS];
    private final boolean[] dirty = new boolean[ROWS];
    private final Map<Key, Integer> tables = new HashMap<>();
    private final Map<Key, Integer> masks = new HashMap<>();
    private int tableTop = TABLES;
    private int maskCount = 1; // mask 0: closed

    BlockHoles() {
        words[0] = MARK;
        dirty[0] = true;
    }

    int[] words() {
        return words;
    }

    /** Hands over the rows changed since last time (in the order of the image), clearing the marks. */
    boolean takeDirty(int row) {
        boolean d = dirty[row];
        dirty[row] = false;
        return d;
    }

    /** Marks every row to upload again (a new image). */
    void markAllDirty() {
        Arrays.fill(dirty, true);
    }

    /**
     * Works out a state's holes from its boxes (16ths: x0, y0, z0, x1, y1, z1, in shape order) and its model's faces,
     * and records them for its palette entry: true when it has any.
     */
    boolean describe(int entry, List<int[]> boxes, List<BakedQuad> quads) {
        int[] table = new int[boxes.size() * 2];
        boolean any = false;
        for (int i = 0; i < boxes.size(); i++) {
            int[] box = boxes.get(i);
            int mx = maskAcross(box, 0, quads);
            int my = maskAcross(box, 1, quads);
            int mz = maskAcross(box, 2, quads);
            table[i * 2] = mx | (my << 16);
            table[i * 2 + 1] = mz;
            any |= (mx | my | mz) != 0;
        }
        if (!any) {
            return false;
        }
        Key key = new Key(table);
        Integer at = tables.get(key);
        if (at == null) {
            if (tableTop + table.length > MASKS) {
                return false; // full: no holes for this one
            }
            at = tableTop;
            tableTop += table.length;
            System.arraycopy(table, 0, words, at, table.length);
            touch(at, table.length);
            tables.put(key, at);
        }
        words[entry] = at;
        touch(entry, 1);
        return true;
    }

    /** The mask (its number) of a box's two faces across an axis: 0 when it has no open cell. */
    private int maskAcross(int[] box, int axis, List<BakedQuad> quads) {
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        // Per cell: 0 nothing drawn, 1 drawn clear, 2 drawn solid.
        byte[] cells = new byte[256];
        for (BakedQuad quad : quads) {
            if (quad.getDirection().getAxis().ordinal() != axis) {
                continue;
            }
            int[] v = quad.getVertices();
            int stride = v.length / 4;
            float[][] p = new float[4][3];
            float[][] uv = new float[4][2];
            for (int i = 0; i < 4; i++) {
                for (int k = 0; k < 3; k++) {
                    p[i][k] = Float.intBitsToFloat(v[i * stride + k]);
                }
                uv[i][0] = Float.intBitsToFloat(v[i * stride + 4]);
                uv[i][1] = Float.intBitsToFloat(v[i * stride + 5]);
            }
            float plane = p[0][axis];
            boolean flat = true;
            for (int i = 1; i < 4; i++) {
                flat &= Math.abs(p[i][axis] - plane) < 1e-3f;
            }
            float plane16 = plane * 16.0f;
            if (!flat || plane16 < box[axis] - 0.01f || plane16 > box[axis + 3] + 0.01f) {
                continue; // tilted, or outside the box
            }
            // The face's own coordinates (block units) to its texture's, from three corners: (s, t) along its edges.
            float a0 = p[0][a], b0 = p[0][b];
            float e1a = p[1][a] - a0, e1b = p[1][b] - b0;
            float e2a = p[3][a] - a0, e2b = p[3][b] - b0;
            float det = e1a * e2b - e1b * e2a;
            if (Math.abs(det) < 1e-6f) {
                continue;
            }
            float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE, minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
            for (float[] c : p) {
                minA = Math.min(minA, c[a]);
                maxA = Math.max(maxA, c[a]);
                minB = Math.min(minB, c[b]);
                maxB = Math.max(maxB, c[b]);
            }
            TextureAtlasSprite sprite = quad.getSprite();
            for (int j = box[b]; j < box[b + 3]; j++) {
                float cb = (j + 0.5f) / 16.0f;
                if (cb < minB || cb > maxB) {
                    continue;
                }
                for (int i = box[a]; i < box[a + 3]; i++) {
                    float ca = (i + 0.5f) / 16.0f;
                    if (ca < minA || ca > maxA) {
                        continue;
                    }
                    float da = ca - a0, db = cb - b0;
                    float s = (da * e2b - db * e2a) / det;
                    float t = (e1a * db - e1b * da) / det;
                    float u = uv[0][0] + s * (uv[1][0] - uv[0][0]) + t * (uv[3][0] - uv[0][0]);
                    float w = uv[0][1] + s * (uv[1][1] - uv[0][1]) + t * (uv[3][1] - uv[0][1]);
                    int texel = BlockPalette.sample(sprite, u, w);
                    int cell = j * 16 + i;
                    boolean solid = texel == 0x7fffffff || ((texel >>> 24) & 255) > 25;
                    if (solid) {
                        cells[cell] = 2;
                    } else if (cells[cell] == 0) {
                        cells[cell] = 1;
                    }
                }
            }
        }
        int[] mask = new int[MASK_WORDS];
        boolean open = false;
        for (int cell = 0; cell < 256; cell++) {
            if (cells[cell] == 1) {
                int row = cell >> 4;
                mask[row >> 1] |= 1 << ((row & 1) * 16 + (cell & 15));
                open = true;
            }
        }
        if (!open) {
            return 0;
        }
        Key key = new Key(mask);
        Integer id = masks.get(key);
        if (id == null) {
            if (MASKS + (maskCount + 1) * MASK_WORDS > WORDS || maskCount > 0xffff) {
                return 0; // full: closed
            }
            id = maskCount++;
            System.arraycopy(mask, 0, words, MASKS + id * MASK_WORDS, MASK_WORDS);
            touch(MASKS + id * MASK_WORDS, MASK_WORDS);
            masks.put(key, id);
        }
        return id;
    }

    private void touch(int from, int count) {
        for (int row = from / BlockPalette.ROW; row <= (from + count - 1) / BlockPalette.ROW; row++) {
            dirty[row] = true;
        }
    }

    private record Key(int[] w) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Arrays.equals(w, k.w);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(w);
        }
    }
}
