package dev.creategalore.bridge.grid;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_DEPTH;
import static org.lwjgl.opengl.GL12C.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12C.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.opengl.GL45C.glTextureSubImage2D;
import static org.lwjgl.opengl.GL45C.glTextureSubImage3D;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.function.ToIntFunction;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A copy of the blocks around the camera for a shader pack that asks for one: a pack that makes custom images named
 * cg_world_img (3D, r32ui, its size is the copy's) and cg_palette_img (2D, r32ui, 4096 x 256, or taller for the holes in
 * cut-out faces, {@link BlockHoles}, and then the faces' textures, {@link BlockFaces}) gets them filled here,
 * at the start of each frame before any of its passes run. Packs without them never see this.
 *
 * The copy is fixed to the world: a block keeps its slot (its position modulo the copy's size) while it stays in the
 * box around the camera, which moves 8 blocks at a time (its low corner is always on a multiple of 8), and only the
 * blocks a move brings in are read. A chunk that loads, a block that changes, or sky light that moves refills what it touches. So the pack sees
 * the same blocks wherever the camera is, and a ray through them gives the same answer from wherever it is looked at.
 *
 * Each slot holds a word:
 * <ul>
 * <li>bits 0..15: the block state's entry in the palette ({@link BlockPalette});</li>
 * <li>16..19: the sky light in that block (0..15);</li>
 * <li>20..27: which place the slot holds (the block's position divided by the copy's size, x and z in 3 bits, y in 2),
 * so the pack can tell a slot still holding a place the box has left from one already filled for the new one;</li>
 * <li>31: filled.</li>
 * </ul>
 * Empty slots read 0 (not filled).
 */
public final class WorldGrid {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    public static final String WORLD_IMAGE = "cg_world_img";
    public static final String PALETTE_IMAGE = "cg_palette_img";
    /** In air's palette entry, word 15: the grid's layout, version 1 (lib/world.glsl checks it). */
    public static final int GRID_MARK = 0x43474731;

    /** Blocks read per frame at most (a full box takes a few frames; a step brings in one slab, a fraction of this). */
    private static final int BUDGET = 160_000;

    private static int worldTex;
    private static int paletteTex;
    private static int sx, sy, sz;
    private static int[] cells;
    private static boolean[] dirtyTiles;
    private static int tilesX, tilesY, tilesZ;
    private static boolean anyDirty;
    private static BlockPalette palette;
    private static ClientLevel level;
    /** The box's low corner (world blocks); boxValid false before the first frame. */
    private static int ox, oy, oz;
    private static boolean boxValid;
    /** Regions (world blocks, low inclusive, high exclusive) still to read. */
    private static final ArrayDeque<int[]> pending = new ArrayDeque<>();
    /** Sections already waiting in pending, so a section many updates touch is read once. */
    private static final LongOpenHashSet pendingSections = new LongOpenHashSet();
    private static IntBuffer upload;
    private static java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
    private static boolean warned;

    private WorldGrid() {
    }

    /** True while a pack takes the copy. */
    public static boolean active() {
        return worldTex != 0;
    }

    /**
     * The start of a frame, before the pack's first pass. pipeline: the Iris pipeline running the pack (a new one after
     * every reload, whose images start empty even when GL hands out the same texture ids again); images: its custom
     * image (texture) ids by name, 0 for none.
     */
    public static void beginFrame(Object pipeline, ToIntFunction<String> images) {
        try {
            int world = images.applyAsInt(WORLD_IMAGE);
            int pal = images.applyAsInt(PALETTE_IMAGE);
            Minecraft mc = Minecraft.getInstance();
            if (world == 0 || pal == 0 || mc.level == null) {
                if (worldTex != 0) {
                    reset();
                }
                return;
            }
            if (world != worldTex || pal != paletteTex || mc.level != level || owner.get() != pipeline) {
                start(world, pal, mc.level);
                owner = new java.lang.ref.WeakReference<>(pipeline);
                if (worldTex == 0) {
                    return; // sizes it doesn't take (start warned once)
                }
            }
            if (atlasChanged()) {
                // Resources were reloaded (a resource pack switched, F3+T): the block atlas was stitched anew, so the
                // entries' colours and their faces' places in it are stale. Start over; the box refills in a few frames.
                start(worldTex, paletteTex, mc.level);
                if (worldTex == 0) {
                    return;
                }
            }
            Camera camera = mc.gameRenderer.getMainCamera();
            BlockPos at = camera.getBlockPosition();
            // The low corner on a multiple of 8 blocks, as the pack works it out (lib/voxel.glsl, vox_box_world_lo),
            // so the box's 8-block tiles are fixed to the world.
            moveBox(Math.floorDiv(at.getX() - sx / 2, 8) * 8, Math.floorDiv(at.getY() - sy / 2, 8) * 8,
                    Math.floorDiv(at.getZ() - sz / 2, 8) * 8);
            if (palette.idsStale()) {
                // A pack's block numbers arrive during its first frame, after this point (see BlockPalette.irisIds):
                // until then the entries carry the last pack's, and its lights, leaves and plants would be read as
                // something else. The pack reads the palette afresh each frame, so new numbers are all it takes.
                palette.refreshIds();
            }
            fill();
            uploadCells();
            uploadPalette();
            uploadHoles();
            uploadFaces();
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOG.error("Block grid for the shader pack failed; it stays as it was", e);
            }
        }
    }

    public static void reset() {
        worldTex = 0;
        paletteTex = 0;
        cells = null;
        dirtyTiles = null;
        palette = null;
        level = null;
        boxValid = false;
        pending.clear();
        pendingSections.clear();
        if (upload != null) {
            MemoryUtil.memFree(upload);
            upload = null;
        }
    }

    private static void start(int world, int pal, ClientLevel newLevel) {
        reset();
        sx = glGetTextureLevelParameteri(world, 0, GL_TEXTURE_WIDTH);
        sy = glGetTextureLevelParameteri(world, 0, GL_TEXTURE_HEIGHT);
        sz = glGetTextureLevelParameteri(world, 0, GL_TEXTURE_DEPTH);
        int pw = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_WIDTH);
        int ph = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_HEIGHT);
        if (sx < 16 || sy < 16 || sz < 16 || sx % 8 != 0 || sy % 8 != 0 || sz % 8 != 0
                || pw != BlockPalette.ROW || ph * BlockPalette.ROW < BlockPalette.ENTRIES * BlockPalette.WORDS) {
            if (!warned) {
                warned = true;
                LOG.warn("Shader pack's block grid images have sizes the Bridge doesn't take ({}x{}x{}, palette {}x{})", sx, sy, sz, pw, ph);
            }
            return;
        }
        worldTex = world;
        paletteTex = pal;
        level = newLevel;
        cells = new int[sx * sy * sz];
        tilesX = sx / 8;
        tilesY = sy / 8;
        tilesZ = sz / 8;
        dirtyTiles = new boolean[tilesX * tilesY * tilesZ];
        palette = new BlockPalette();
        holesFit = (long) ph * BlockPalette.ROW >= (long) BlockPalette.ENTRIES * BlockPalette.WORDS + BlockHoles.WORDS;
        palette.holes.markAllDirty(); // the image's rows start as whatever GL left there
        facesFit = holesFit && ph >= BlockPalette.ENTRIES * BlockPalette.WORDS / BlockPalette.ROW + BlockHoles.ROWS + BlockFaces.ROWS;
        palette.faces.markAllDirty();
        atlasMark = atlasMark();
        palette.idOf(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        // Air's last word (unused by any entry) tells the pack the grid is the Bridge's: without it, the pack falls back
        // to Minecraft's own light.
        palette.words()[BlockPalette.WORDS - 1] = GRID_MARK;
        upload = MemoryUtil.memAllocInt(Math.max(512, BlockPalette.ROW));
        // Whatever the texture held is another place's: clear it all on the first upload.
        java.util.Arrays.fill(dirtyTiles, true);
        anyDirty = true;
        LOG.info("Block grid for the shader pack: {} x {} x {} blocks{}{}", sx, sy, sz, holesFit ? ", with holes in cut-out faces" : "",
                facesFit ? ", with block textures" : "");
    }

    /** Moves the box to a new low corner; what it brings in is queued to read. */
    private static void moveBox(int nx, int ny, int nz) {
        if (boxValid && nx == ox && ny == oy && nz == oz) {
            return;
        }
        if (!boxValid || Math.abs(nx - ox) >= sx || Math.abs(ny - oy) >= sy || Math.abs(nz - oz) >= sz) {
            pending.clear();
            pendingSections.clear();
            ox = nx;
            oy = ny;
            oz = nz;
            boxValid = true;
            // Nearest first: the box in eight-block layers outwards from the camera, so what is around you is there
            // before what is far.
            queueOutwards();
            return;
        }
        // The slabs the move brings in, one per axis that changed.
        int[] old = {ox, oy, oz};
        ox = nx;
        oy = ny;
        oz = nz;
        if (nx != old[0]) {
            int lo = nx > old[0] ? old[0] + sx : nx;
            int hi = nx > old[0] ? nx + sx : old[0];
            pending.addFirst(new int[] {lo, oy, oz, hi, oy + sy, oz + sz});
        }
        if (ny != old[1]) {
            int lo = ny > old[1] ? old[1] + sy : ny;
            int hi = ny > old[1] ? ny + sy : old[1];
            pending.addFirst(new int[] {ox, lo, oz, ox + sx, hi, oz + sz});
        }
        if (nz != old[2]) {
            int lo = nz > old[2] ? old[2] + sz : nz;
            int hi = nz > old[2] ? nz + sz : old[2];
            pending.addFirst(new int[] {ox, oy, lo, ox + sx, oy + sy, hi});
        }
    }

    private static void queueOutwards() {
        int cx = ox + sx / 2, cy = oy + sy / 2, cz = oz + sz / 2;
        int rings = Math.max(sx, Math.max(sy, sz)) / 16 + 1;
        // Shells of 16 blocks around the middle: the inner box first, then each ring's slabs.
        int[] prev = null;
        for (int r = 1; r <= rings; r++) {
            int[] box = {Math.max(ox, cx - 16 * r), Math.max(oy, cy - 16 * r), Math.max(oz, cz - 16 * r),
                    Math.min(ox + sx, cx + 16 * r), Math.min(oy + sy, cy + 16 * r), Math.min(oz + sz, cz + 16 * r)};
            if (prev == null) {
                pending.addLast(box);
            } else {
                // box minus prev: up to six slabs.
                if (box[0] < prev[0]) pending.addLast(new int[] {box[0], box[1], box[2], prev[0], box[4], box[5]});
                if (prev[3] < box[3]) pending.addLast(new int[] {prev[3], box[1], box[2], box[3], box[4], box[5]});
                if (box[1] < prev[1]) pending.addLast(new int[] {prev[0], box[1], box[2], prev[3], prev[1], box[5]});
                if (prev[4] < box[4]) pending.addLast(new int[] {prev[0], prev[4], box[2], prev[3], box[4], box[5]});
                if (box[2] < prev[2]) pending.addLast(new int[] {prev[0], prev[1], box[2], prev[3], prev[4], prev[2]});
                if (prev[5] < box[5]) pending.addLast(new int[] {prev[0], prev[1], prev[5], prev[3], prev[4], box[5]});
            }
            prev = box;
        }
    }

    /** A chunk section (section coordinates) changed: its blocks or its light. */
    public static void sectionChanged(int x, int y, int z) {
        if (!active() || !boxValid) {
            return;
        }
        // Only sections that reach into the box.
        if ((x + 1) << 4 <= ox || x << 4 >= ox + sx || (y + 1) << 4 <= oy || y << 4 >= oy + sy || (z + 1) << 4 <= oz || z << 4 >= oz + sz) {
            return;
        }
        if (pendingSections.add(SectionPos.asLong(x, y, z))) {
            pending.addLast(new int[] {x << 4, y << 4, z << 4, (x + 1) << 4, (y + 1) << 4, (z + 1) << 4, 1});
        }
    }

    /** One block changed. */
    public static void blockChanged(BlockPos pos) {
        if (!active()) {
            return;
        }
        pending.addFirst(new int[] {pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1});
    }

    /** A chunk loaded or went away (chunk coordinates). */
    public static void chunkChanged(int cx, int cz) {
        if (!active()) {
            return;
        }
        pending.addLast(new int[] {cx << 4, oy, cz << 4, (cx + 1) << 4, oy + sy, (cz + 1) << 4});
    }

    private static void fill() {
        int budget = BUDGET;
        while (budget > 0 && !pending.isEmpty()) {
            int[] r = pending.pollFirst();
            if (r.length > 6) {
                pendingSections.remove(SectionPos.asLong(r[0] >> 4, r[1] >> 4, r[2] >> 4));
            }
            // Clipped to the box as it is now.
            int x0 = Math.max(r[0], ox), y0 = Math.max(r[1], oy), z0 = Math.max(r[2], oz);
            int x1 = Math.min(r[3], ox + sx), y1 = Math.min(r[4], oy + sy), z1 = Math.min(r[5], oz + sz);
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
                continue;
            }
            int perZ = (x1 - x0) * (y1 - y0);
            int layers = Math.max(1, Math.min(z1 - z0, budget / Math.max(perZ, 1)));
            if (layers < z1 - z0) {
                pending.addFirst(new int[] {x0, y0, z0 + layers, x1, y1, z1});
            }
            readRegion(x0, y0, z0, x1, y1, z0 + layers);
            budget -= perZ * layers;
        }
    }

    /** Reads the blocks of a region inside the box into their slots, a chunk section at a time. */
    private static void readRegion(int x0, int y0, int z0, int x1, int y1, int z1) {
        LayerLightEventListener sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
        boolean hasSky = level.dimensionType().hasSkyLight();
        for (int scx = x0 >> 4; scx <= (x1 - 1) >> 4; scx++) {
            for (int scz = z0 >> 4; scz <= (z1 - 1) >> 4; scz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(scx, scz, false);
                int highest = chunk == null ? -1 : chunk.getHighestFilledSectionIndex();
                for (int scy = y0 >> 4; scy <= (y1 - 1) >> 4; scy++) {
                    int index = chunk == null ? -1 : level.getSectionIndexFromSectionY(scy);
                    LevelChunkSection section = index >= 0 && index < level.getSectionsCount() ? chunk.getSection(index) : null;
                    DataLayer skyData = hasSky && chunk != null ? sky.getDataLayerData(SectionPos.of(scx, scy, scz)) : null;
                    int skyDefault = !hasSky ? 0 : chunk == null ? 15 : index > highest ? 15 : 0;
                    int bx0 = Math.max(x0, scx << 4), bx1 = Math.min(x1, (scx + 1) << 4);
                    int by0 = Math.max(y0, scy << 4), by1 = Math.min(y1, (scy + 1) << 4);
                    int bz0 = Math.max(z0, scz << 4), bz1 = Math.min(z1, (scz + 1) << 4);
                    boolean air = section == null || section.hasOnlyAir();
                    for (int z = bz0; z < bz1; z++) {
                        for (int y = by0; y < by1; y++) {
                            for (int x = bx0; x < bx1; x++) {
                                int id = 0;
                                if (!air) {
                                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                    id = state.isAir() ? 0 : palette.idOf(state);
                                }
                                int light = skyData != null ? skyData.get(x & 15, y & 15, z & 15) : skyDefault;
                                store(x, y, z, chunk == null ? 0 : id | (light << 16) | tag(x, y, z) | (1 << 31));
                            }
                        }
                    }
                }
            }
        }
    }

    private static int tag(int x, int y, int z) {
        return ((Math.floorDiv(x, sx) & 7) << 20) | ((Math.floorDiv(z, sz) & 7) << 23) | ((Math.floorDiv(y, sy) & 3) << 26);
    }

    private static void store(int x, int y, int z, int word) {
        int px = Math.floorMod(x, sx), py = Math.floorMod(y, sy), pz = Math.floorMod(z, sz);
        int i = px + sx * (py + sy * pz);
        if (cells[i] != word) {
            cells[i] = word;
            dirtyTiles[(px >> 3) + tilesX * ((py >> 3) + tilesY * (pz >> 3))] = true;
            anyDirty = true;
        }
    }

    private static void uploadCells() {
        if (!anyDirty) {
            return;
        }
        anyDirty = false;
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] saved = saveUnpack();
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            for (int tz = 0; tz < tilesZ; tz++) {
                for (int ty = 0; ty < tilesY; ty++) {
                    for (int tx = 0; tx < tilesX; tx++) {
                        int t = tx + tilesX * (ty + tilesY * tz);
                        if (!dirtyTiles[t]) {
                            continue;
                        }
                        dirtyTiles[t] = false;
                        upload.clear();
                        for (int z = 0; z < 8; z++) {
                            for (int y = 0; y < 8; y++) {
                                int base = tx * 8 + sx * ((ty * 8 + y) + sy * (tz * 8 + z));
                                upload.put(cells, base, 8);
                            }
                        }
                        upload.flip();
                        glTextureSubImage3D(worldTex, 0, tx * 8, ty * 8, tz * 8, 8, 8, 8, GL_RED_INTEGER, GL_UNSIGNED_INT, upload);
                    }
                }
            }
        } finally {
            restoreUnpack(saved);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    /** Whether the pack's palette image has the rows for the holes in cut-out faces (see {@link BlockHoles}). */
    private static boolean holesFit;

    private static void uploadHoles() {
        if (!holesFit) {
            return;
        }
        BlockHoles holes = palette.holes;
        int first = BlockPalette.ENTRIES * BlockPalette.WORDS / BlockPalette.ROW;
        int unpackBuffer = -1;
        int[] saved = null;
        try {
            for (int row = 0; row < BlockHoles.ROWS; row++) {
                if (!holes.takeDirty(row)) {
                    continue;
                }
                if (saved == null) {
                    unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
                    saved = saveUnpack();
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                    glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                    glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                }
                upload.clear();
                upload.put(holes.words(), row * BlockPalette.ROW, BlockPalette.ROW);
                upload.flip();
                glTextureSubImage2D(paletteTex, 0, 0, first + row, BlockPalette.ROW, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, upload);
            }
        } finally {
            if (saved != null) {
                restoreUnpack(saved);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            }
        }
    }

    /**
     * Whether the pack's palette image has the rows for the faces' textures (see {@link BlockFaces}), after the holes'.
     */
    private static boolean facesFit;
    /** The block atlas's missing-texture sprite when the palette was started: a new one after every stitch. */
    private static Object atlasMark;

    private static Object atlasMark() {
        try {
            return Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                    .getSprite(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean atlasChanged() {
        Object now = atlasMark();
        return now != null && now != atlasMark;
    }

    private static void uploadFaces() {
        if (!facesFit) {
            return;
        }
        BlockFaces faces = palette.faces;
        int from = faces.dirtyRowFrom();
        int to = faces.dirtyRowTo();
        if (from >= to) {
            return;
        }
        int first = BlockPalette.ENTRIES * BlockPalette.WORDS / BlockPalette.ROW + BlockHoles.ROWS;
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] saved = saveUnpack();
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            for (int row = from; row < to; row++) {
                upload.clear();
                upload.put(faces.words(), row * BlockPalette.ROW, BlockPalette.ROW);
                upload.flip();
                glTextureSubImage2D(paletteTex, 0, 0, first + row, BlockPalette.ROW, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, upload);
            }
            faces.markUploaded();
        } finally {
            restoreUnpack(saved);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static void uploadPalette() {
        int from = palette.uploadedUpTo();
        int to = palette.count();
        if (from >= to) {
            return;
        }
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] saved = saveUnpack();
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            int perRow = BlockPalette.ROW / BlockPalette.WORDS;
            // Whole rows from the first new entry's to the last's.
            for (int row = from / perRow; row <= (to - 1) / perRow; row++) {
                upload.clear();
                upload.put(palette.words(), row * BlockPalette.ROW, BlockPalette.ROW);
                upload.flip();
                glTextureSubImage2D(paletteTex, 0, 0, row, BlockPalette.ROW, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, upload);
            }
            palette.markUploaded();
        } finally {
            restoreUnpack(saved);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static int[] saveUnpack() {
        return new int[] {glGetInteger(GL_UNPACK_ROW_LENGTH), glGetInteger(GL_UNPACK_SKIP_PIXELS), glGetInteger(GL_UNPACK_SKIP_ROWS),
                glGetInteger(GL_UNPACK_IMAGE_HEIGHT), glGetInteger(GL_UNPACK_SKIP_IMAGES), glGetInteger(GL_UNPACK_ALIGNMENT)};
    }

    private static void restoreUnpack(int[] s) {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, s[0]);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, s[1]);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, s[2]);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, s[3]);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, s[4]);
        glPixelStorei(GL_UNPACK_ALIGNMENT, s[5]);
    }
}
