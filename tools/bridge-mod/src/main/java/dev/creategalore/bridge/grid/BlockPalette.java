package dev.creategalore.bridge.grid;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a shader pack needs to know about each block state, once per state, for the block grid ({@link WorldGrid}):
 * its shape as up to twelve boxes in 16ths of a block, its colour, where it glows and in what colour, its light level,
 * the pack's own number for it (block.properties), and a few facts the pack decides from (water, drawn see-through,
 * mostly clear like glass, nothing to walk into like a flower or a torch). The pack decides itself what blocks light
 * and what gives light.
 *
 * Each state gets an entry of {@link #WORDS} words in cg_palette_img (4096 words a row):
 * <ol start="0">
 * <li>the pack's number (bits 0..15, 0xffff none), the light level (16..19), the flags (20 and up);</li>
 * <li>its average colour as drawn (sRGB, red in the low byte, alpha the share of its texels that show);</li>
 * <li>its glow colour: its texels weighed by their brightness (sRGB);</li>
 * <li>where it glows in its block, x, y and z in 32nds (6 bits each), and its box count (bits 24..27);</li>
 * <li>up to twelve boxes, each its low corner x, y, z and high corner x, y, z in 16ths (5 bits each).</li>
 * </ol>
 * Entry 0 is air (its word 15 carries {@link WorldGrid}'s marker).
 *
 * <p>The shape is the one its model draws (see {@link #drawnBoxes}): a fence's two rails, not the full-height slab its
 * outline makes of each arm; a closed gate's bars, not a solid panel. A block whose model gives none (drawn by a block
 * entity, or only as flat or tilted faces) keeps its outline shape, and so does a full block.
 */
final class BlockPalette {
    static final int WORDS = 16;
    static final int ROW = 4096;
    static final int ENTRIES = 65536;

    static final int FLAG_WATER = 1 << 20;
    static final int FLAG_TRANSLUCENT = 1 << 21;
    static final int FLAG_MOSTLY_CLEAR = 1 << 22;
    static final int FLAG_NO_COLLISION = 1 << 23;
    static final int FLAG_FULL_CUBE = 1 << 24;
    static final int FLAG_HAS_SHAPE = 1 << 25;
    static final int FLAG_LAVA = 1 << 26;
    static final int FLAG_FLUID = 1 << 27;
    /** Its cut-out faces have clear pixels: its holes are in {@link BlockHoles}. */
    static final int FLAG_HOLES = 1 << 28;

    private static final int MAX_BOXES = 12;

    private final Reference2IntOpenHashMap<BlockState> ids = new Reference2IntOpenHashMap<>();
    private final int[] words = new int[ENTRIES * WORDS];
    /** The holes in its cut-out faces (see {@link BlockHoles}). */
    final BlockHoles holes = new BlockHoles();
    /** Where its faces find their texture in the block atlas (see {@link BlockFaces}). */
    final BlockFaces faces = new BlockFaces();
    private int count = 0;
    /** Entries made since the last upload, from this one on. */
    private int uploadedUpTo = 0;
    /**
     * The pack's block numbers the entries carry: Iris's map as it was when they were read. Iris sets a new pack's map
     * at the start of its pipeline's first frame, after the grid has begun that frame, so a palette started then holds
     * the previous pack's numbers (or none) until {@link #refreshIds} catches up.
     */
    private Object2IntMap<BlockState> irisIds;

    BlockPalette() {
        ids.defaultReturnValue(-1);
        irisIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();
    }

    /** Whether Iris's block numbers are another map than the one the entries carry (a pack was loaded or switched). */
    boolean idsStale() {
        return WorldRenderingSettings.INSTANCE.getBlockStateIds() != irisIds;
    }

    /**
     * Gives every entry the pack's number from Iris's current map and marks the whole palette for upload. The entries
     * themselves stay (a block state keeps its entry), so the grid's words stay valid.
     */
    void refreshIds() {
        irisIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        for (Reference2IntMap.Entry<BlockState> e : ids.reference2IntEntrySet()) {
            int at = e.getIntValue() * WORDS;
            int irisId = irisIds == null ? -1 : irisIds.getOrDefault(e.getKey(), -1);
            words[at] = (words[at] & ~0xffff) | (irisId < 0 ? 0xffff : irisId & 0xffff);
        }
        uploadedUpTo = 0;
    }

    int count() {
        return count;
    }

    int uploadedUpTo() {
        return uploadedUpTo;
    }

    void markUploaded() {
        uploadedUpTo = count;
    }

    int[] words() {
        return words;
    }

    /** The entry of a state, made the first time it is asked for; 0 (air) once the table is full. */
    int idOf(BlockState state) {
        int id = ids.getInt(state);
        if (id >= 0) {
            return id;
        }
        if (count >= ENTRIES) {
            return 0;
        }
        id = count++;
        ids.put(state, id);
        try {
            describe(state, id * WORDS);
        } catch (RuntimeException e) {
            // A mod's block that can't be asked about without a level: a plain full block, keeping the pack's number
            // and its light if those can be had.
            int irisId = -1;
            int emission = 0;
            try {
                irisId = irisIds == null ? -1 : irisIds.getOrDefault(state, -1);
                emission = Math.min(state.getLightEmission(), 15);
            } catch (RuntimeException ignored) {
                // Neither: a plain block.
            }
            java.util.Arrays.fill(words, id * WORDS, id * WORDS + WORDS, 0);
            words[id * WORDS] = (irisId < 0 ? 0xffff : irisId & 0xffff) | (emission << 16) | FLAG_FULL_CUBE | FLAG_HAS_SHAPE;
            words[id * WORDS + 1] = 0xff595959;
            words[id * WORDS + 2] = 0xffffffff;
            words[id * WORDS + 3] = 16 | (16 << 6) | (16 << 12) | (1 << 24);
            words[id * WORDS + 4] = 16 << 15 | 16 << 20 | 16 << 25;
        }
        return id;
    }

    private void describe(BlockState state, int at) {
        int irisId = irisIds == null ? -1 : irisIds.getOrDefault(state, -1);
        int flags = 0;
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            flags |= FLAG_FLUID;
            if (fluid.is(FluidTags.WATER)) {
                flags |= FLAG_WATER;
            }
            if (fluid.is(FluidTags.LAVA)) {
                flags |= FLAG_LAVA;
            }
        }
        if (state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            flags |= FLAG_NO_COLLISION;
        }
        Look look = look(state);
        VoxelShape shape = state.isAir() ? Shapes.empty() : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (state.getRenderShape() == RenderShape.INVISIBLE && fluid.isEmpty()) {
            shape = Shapes.empty(); // a barrier, a light block, moving pistons' leftovers
        }
        List<AABB> boxes = new ArrayList<>(shape.isEmpty() ? List.of() : shape.toAabbs());
        if (!boxes.isEmpty() && !look.drawn.isEmpty() && !Block.isShapeFullBlock(shape)) {
            boxes = look.drawn; // a full block stays one box, however its model splits its faces
        }
        if (!fluid.isEmpty() && boxes.isEmpty()) {
            boxes.add(new AABB(0, 0, 0, 1, fluid.getHeight(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), 1));
        }
        if (boxes.size() > MAX_BOXES) {
            boxes.sort(Comparator.comparingDouble((AABB b) -> -b.getXsize() * b.getYsize() * b.getZsize()));
            boxes = boxes.subList(0, MAX_BOXES);
        }
        int boxCount = 0;
        List<int[]> written = new ArrayList<>();
        for (AABB b : boxes) {
            int x0 = sixteenth(b.minX), y0 = sixteenth(b.minY), z0 = sixteenth(b.minZ);
            int x1 = sixteenth(b.maxX), y1 = sixteenth(b.maxY), z1 = sixteenth(b.maxZ);
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                continue;
            }
            words[at + 4 + boxCount++] = x0 | (y0 << 5) | (z0 << 10) | (x1 << 15) | (y1 << 20) | (z1 << 25);
            written.add(new int[] {x0, y0, z0, x1, y1, z1});
        }
        if (look.cutout && !look.translucent && !look.quads.isEmpty() && !written.isEmpty()
                && holes.describe(at / WORDS, written, look.quads)) {
            flags |= FLAG_HOLES;
        }
        if (!look.quads.isEmpty()) {
            faces.describe(at / WORDS, look.quads, look.tints);
        } else if (look.fluidSprite != null) {
            faces.describeFluid(at / WORDS, look.fluidSprite, look.fluidTint);
        }
        if (boxCount > 0) {
            flags |= FLAG_HAS_SHAPE;
        }
        if (boxCount == 1 && words[at + 4] == (16 << 15 | 16 << 20 | 16 << 25)) {
            flags |= FLAG_FULL_CUBE;
        }

        if (look.translucent) {
            flags |= FLAG_TRANSLUCENT;
        }
        if (look.shown < 0.5f) {
            flags |= FLAG_MOSTLY_CLEAR;
        }
        int emission = Math.min(state.getLightEmission(), 15);
        words[at] = (irisId < 0 ? 0xffff : irisId & 0xffff) | (emission << 16) | flags;
        words[at + 1] = rgba(look.color, look.shown);
        words[at + 2] = rgba(look.glow, 1.0f);
        int lx = Math.round(clamp(look.glowAt[0], 0.03f, 0.97f) * 32.0f);
        int ly = Math.round(clamp(look.glowAt[1], 0.03f, 0.97f) * 32.0f);
        int lz = Math.round(clamp(look.glowAt[2], 0.03f, 0.97f) * 32.0f);
        words[at + 3] = lx | (ly << 6) | (lz << 12) | (boxCount << 24);
    }

    private static int sixteenth(double v) {
        return (int) Math.max(0, Math.min(16, Math.round(v * 16.0)));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int rgba(float[] c, float a) {
        int r = Math.round(clamp(c[0], 0, 1) * 255.0f);
        int g = Math.round(clamp(c[1], 0, 1) * 255.0f);
        int b = Math.round(clamp(c[2], 0, 1) * 255.0f);
        int al = Math.round(clamp(a, 0, 1) * 255.0f);
        return r | (g << 8) | (b << 16) | (al << 24);
    }

    /** How a state looks: from its model's faces, a few texels of each. */
    private static final class Look {
        float[] color = {0.35f, 0.35f, 0.35f};
        float shown = 1.0f;
        float[] glow = {1.0f, 1.0f, 1.0f};
        float[] glowAt = {0.5f, 0.5f, 0.5f};
        boolean translucent;
        /** Drawn cut out: its texture's clear texels are holes (a trapdoor's, a grate's, a ladder's gaps). */
        boolean cutout;
        List<AABB> drawn = List.of();
        List<BakedQuad> quads = List.of();
        /** Each quad's tint (sRGB, red low), -1 for an untinted one. */
        int[] tints = new int[0];
        /** A fluid's still texture and its tint (-1 for none), for a fluid that draws no model. */
        TextureAtlasSprite fluidSprite;
        int fluidTint = -1;
    }

    private static Look look(BlockState state) {
        Look look = new Look();
        Minecraft mc = Minecraft.getInstance();
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                // Water and lava draw no block model: their still texture.
                TextureAtlasSprite sprite = mc.getModelManager().getBlockModelShaper().getParticleIcon(fluid.createLegacyBlock());
                look.fluidSprite = sprite;
                float[] c = average(sprite, 0.0f, 1.0f, 0.0f, 1.0f, null);
                if (c != null) {
                    look.color = new float[] {c[0], c[1], c[2]};
                    look.glow = new float[] {c[4], c[5], c[6]};
                }
                look.translucent = fluid.is(FluidTags.WATER);
                if (look.translucent) {
                    look.color = new float[] {0.25f, 0.42f, 0.8f}; // its texture is grey; the biome tints it
                    look.fluidTint = 0x3f | (0x76 << 8) | (0xe4 << 16); // the default water colour, 0x3F76E4
                }
            }
            return look;
        }
        look.translucent = net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(state)
                == net.minecraft.client.renderer.RenderType.translucent();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction d : Direction.values()) {
            random.setSeed(42L);
            quads.addAll(model.getQuads(state, d, random));
        }
        random.setSeed(42L);
        quads.addAll(model.getQuads(state, null, random));
        if (quads.isEmpty()) {
            return look;
        }
        look.quads = quads;
        look.tints = new int[quads.size()];
        try {
            random.setSeed(42L);
            for (net.minecraft.client.renderer.RenderType type
                    : model.getRenderTypes(state, random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY)) {
                if (type == net.minecraft.client.renderer.RenderType.cutout()
                        || type == net.minecraft.client.renderer.RenderType.cutoutMipped()) {
                    look.cutout = true;
                }
            }
        } catch (RuntimeException e) {
            // A model that can't say without a level: no holes.
        }
        look.drawn = drawnBoxes(quads);
        double[] sum = new double[3];
        double shownSum = 0, all = 0;
        double[] glowSum = new double[3];
        double[] at = new double[3];
        double glowWeight = 0;
        for (int q = 0; q < quads.size(); q++) {
            BakedQuad quad = quads.get(q);
            int[] v = quad.getVertices();
            int stride = v.length / 4;
            float[] tint = {1, 1, 1};
            look.tints[q] = -1;
            if (quad.isTinted()) {
                try {
                    int c = mc.getBlockColors().getColor(state, null, null, quad.getTintIndex());
                    if (c != -1) {
                        tint = new float[] {((c >> 16) & 255) / 255.0f, ((c >> 8) & 255) / 255.0f, (c & 255) / 255.0f};
                        look.tints[q] = ((c >> 16) & 255) | (((c >> 8) & 255) << 8) | ((c & 255) << 16);
                    }
                } catch (RuntimeException ignored) {
                    // A colour provider that needs a level: untinted.
                }
            }
            TextureAtlasSprite sprite = quad.getSprite();
            // Four looks a little in from each corner and one in the middle, as the pack's own light finder did.
            float[][] corners = new float[4][5];
            for (int i = 0; i < 4; i++) {
                corners[i][0] = Float.intBitsToFloat(v[i * stride]);
                corners[i][1] = Float.intBitsToFloat(v[i * stride + 1]);
                corners[i][2] = Float.intBitsToFloat(v[i * stride + 2]);
                corners[i][3] = Float.intBitsToFloat(v[i * stride + 4]);
                corners[i][4] = Float.intBitsToFloat(v[i * stride + 5]);
            }
            float[] mid = new float[5];
            for (float[] c : corners) {
                for (int k = 0; k < 5; k++) {
                    mid[k] += c[k] * 0.25f;
                }
            }
            for (int i = 0; i < 5; i++) {
                float[] p = new float[5];
                for (int k = 0; k < 5; k++) {
                    p[k] = i < 4 ? mid[k] + (corners[i][k] - mid[k]) * 0.8f : mid[k];
                }
                int texel = sample(sprite, p[3], p[4]);
                if (texel == 0x7fffffff) {
                    continue;
                }
                float a = ((texel >>> 24) & 255) / 255.0f;
                float r = (texel & 255) / 255.0f * tint[0];
                float g = ((texel >> 8) & 255) / 255.0f * tint[1];
                float b = ((texel >> 16) & 255) / 255.0f * tint[2];
                all += 1;
                shownSum += a > 0.1f ? 1 : 0;
                if (a > 0.1f) {
                    sum[0] += r;
                    sum[1] += g;
                    sum[2] += b;
                }
                float lr = linear(r), lg = linear(g), lb = linear(b);
                float lum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;
                double w = a * (lum * lum + 0.002);
                glowSum[0] += lr * w;
                glowSum[1] += lg * w;
                glowSum[2] += lb * w;
                at[0] += p[0] * w;
                at[1] += p[1] * w;
                at[2] += p[2] * w;
                glowWeight += w;
            }
        }
        double shownCount = Math.max(shownSum, 1);
        look.color = new float[] {(float) (sum[0] / shownCount), (float) (sum[1] / shownCount), (float) (sum[2] / shownCount)};
        look.shown = all > 0 ? (float) (shownSum / all) : 1.0f;
        if (glowWeight > 0) {
            look.glow = new float[] {srgb((float) (glowSum[0] / glowWeight)), srgb((float) (glowSum[1] / glowWeight)),
                    srgb((float) (glowSum[2] / glowWeight))};
            look.glowAt = new float[] {(float) (at[0] / glowWeight), (float) (at[1] / glowWeight), (float) (at[2] / glowWeight)};
        }
        return look;
    }

    /**
     * The boxes a model draws (its elements), from its faces: each face square to the block, paired with the nearest
     * face across from it that faces the other way with the same outline (in 16ths). An element's two sides make its
     * box; two elements side by side whose shared sides are left out make one box across both. Tilted faces (a
     * torch on a wall, a flower's cross) and faces with nothing across from them (a vine, a ladder) give none.
     */
    private static List<AABB> drawnBoxes(List<BakedQuad> quads) {
        List<List<int[]>> low = new ArrayList<>();  // per axis: faces facing down it (an element's low side)
        List<List<int[]>> high = new ArrayList<>(); // and up it
        for (int axis = 0; axis < 3; axis++) {
            low.add(new ArrayList<>());
            high.add(new ArrayList<>());
        }
        for (BakedQuad quad : quads) {
            Direction d = quad.getDirection();
            int axis = d.getAxis().ordinal();
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
                continue; // tilted
            }
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            int[] face = {sixteenth(lo[axis]), sixteenth(lo[a]), sixteenth(hi[a]), sixteenth(lo[b]), sixteenth(hi[b])};
            if (face[2] <= face[1] || face[4] <= face[3]) {
                continue;
            }
            (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? high : low).get(axis).add(face);
        }
        List<AABB> boxes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int axis = 0; axis < 3; axis++) {
            for (int[] from : low.get(axis)) {
                int[] to = null;
                for (int[] f : high.get(axis)) {
                    if (f[0] > from[0] && f[1] == from[1] && f[2] == from[2] && f[3] == from[3] && f[4] == from[4]
                            && (to == null || f[0] < to[0])) {
                        to = f;
                    }
                }
                if (to == null) {
                    continue;
                }
                int[] min = new int[3];
                int[] max = new int[3];
                min[axis] = from[0];
                max[axis] = to[0];
                min[(axis + 1) % 3] = from[1];
                max[(axis + 1) % 3] = from[2];
                min[(axis + 2) % 3] = from[3];
                max[(axis + 2) % 3] = from[4];
                long key = 0;
                for (int k = 0; k < 3; k++) {
                    key = key * 32 + min[k];
                    key = key * 32 + max[k];
                }
                if (seen.add(key)) {
                    boxes.add(new AABB(min[0] / 16.0, min[1] / 16.0, min[2] / 16.0, max[0] / 16.0, max[1] / 16.0,
                            max[2] / 16.0));
                }
            }
        }
        return boxes;
    }

    /** A sprite's texel at a point of the atlas (u, v), ABGR as NativeImage keeps it; 0x7fffffff when unreadable. */
    static int sample(TextureAtlasSprite sprite, float u, float v) {
        try {
            NativeImage image = sprite.contents().getOriginalImage();
            if (image == null) {
                return 0x7fffffff;
            }
            float su = (u - sprite.getU0()) / Math.max(sprite.getU1() - sprite.getU0(), 1e-6f);
            float sv = (v - sprite.getV0()) / Math.max(sprite.getV1() - sprite.getV0(), 1e-6f);
            int w = sprite.contents().width();
            int h = sprite.contents().height();
            int x = Math.max(0, Math.min(w - 1, (int) (su * w)));
            int y = Math.max(0, Math.min(h - 1, (int) (sv * h)));
            return image.getPixelRGBA(x, y);
        } catch (RuntimeException e) {
            return 0x7fffffff;
        }
    }

    /** A sprite's average over part of it: rgb (sRGB) and alpha, then the glow colour (sRGB); null when unreadable. */
    private static float[] average(TextureAtlasSprite sprite, float u0, float u1, float v0, float v1, float[] tint) {
        double[] s = new double[7];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (u0 + (u1 - u0) * (i + 0.5f) / 4.0f);
                float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (v0 + (v1 - v0) * (j + 0.5f) / 4.0f);
                int texel = sample(sprite, u, v);
                if (texel == 0x7fffffff) {
                    return null;
                }
                float r = (texel & 255) / 255.0f, g = ((texel >> 8) & 255) / 255.0f, b = ((texel >> 16) & 255) / 255.0f;
                s[0] += r;
                s[1] += g;
                s[2] += b;
                s[3] += ((texel >>> 24) & 255) / 255.0f;
                n++;
            }
        }
        float[] out = new float[7];
        for (int k = 0; k < 4; k++) {
            out[k] = (float) (s[k] / n);
        }
        out[4] = out[0];
        out[5] = out[1];
        out[6] = out[2];
        return out;
    }

    private static float linear(float c) {
        return (float) Math.pow(c, 2.2);
    }

    private static float srgb(float c) {
        return (float) Math.pow(Math.max(c, 0.0f), 1.0 / 2.2);
    }
}
