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
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.opengl.GL45C.glTextureSubImage2D;

import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ground's height far round the camera, from Distant Horizons' terrain data, for a shader pack that works out
 * shadows past its own shadow map with it (mountains shading mountains, forests their floors): a pack whose palette
 * image (cg_palette_img, see {@link WorldGrid}) is {@link #ROWS} rows taller than the palette, the holes and the
 * faces gets the heights in those rows; a pack that doesn't, a game without Distant Horizons, or a Bridge without
 * this, leaves them at 0, which means "not known".
 *
 * One height for each 4 x 4 blocks (a cell), 512 x 512 cells round the camera (2048 blocks across). The window is
 * fixed to the world: a cell keeps its slot (its place modulo 512, x and z) while the window, which moves 16 cells (64
 * blocks) at a time, keeps it in; the window's low corner is the camera's cell less 256, rounded down to a multiple of
 * 16 (the pack works it out the same way). Slot (x, z) is word z * 512 + x of the rows, 4096 words a row.
 *
 * Each word:
 * <ul>
 * <li>bits 0..15: the top of the ground in the cell (the highest block that isn't air or a liquid, at the middle of
 * two of its columns, the higher of the two), plus 4096: leaves and roofs count, the sea doesn't;</li>
 * <li>16..23: which place the slot holds (the cell divided by 512, x and z, 4 bits each), so the pack can tell a
 * slot still holding a place the window has left;</li>
 * <li>31: filled.</li>
 * </ul>
 *
 * The heights are read in the background ({@link FarHeightsDh}), nearest first, a block of 16 x 16 cells (one of
 * Distant Horizons' 64-block sections) at a time, and only what changed is sent: the cells a move of the window brings
 * in, and a section again when Distant Horizons says a chunk in it changed (generated, or rebuilt after you were there).
 * Sections Distant Horizons has nothing for yet are tried again every few seconds.
 */
public final class FarHeights {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    public static final int SIZE = 512;
    public static final int CELL = 4;
    /** Cells the window moves by. */
    static final int STEP = 16;
    /** Sections (16 x 16 cells) across the window. */
    static final int SECTIONS = SIZE / STEP;
    /** Rows of the palette image they take (4096 words a row). */
    public static final int ROWS = SIZE * SIZE / BlockPalette.ROW;
    /** The rows before them: the palette's, the holes' and the faces'. */
    static final int FIRST_ROW = BlockPalette.ENTRIES * BlockPalette.WORDS / BlockPalette.ROW + BlockHoles.ROWS
            + BlockFaces.ROWS;
    static final int HEIGHT_OFFSET = 4096;
    static final int FILLED = 1 << 31;

    private static final Object LOCK = new Object();
    /** The words as the pack gets them (slot z * 512 + x); guarded by LOCK. */
    private static final int[] words = new int[SIZE * SIZE];
    /** Which image rows changed since the last upload; guarded by LOCK. */
    private static final boolean[] dirtyRows = new boolean[ROWS];
    private static boolean anyDirty;

    /** The window's low corner in cells, as the render thread last worked it out (read by the reader). */
    static volatile int lowX, lowZ;
    /** Bumped when everything read so far no longer counts (another world, another dimension). */
    static volatile int generation;
    static volatile ClientLevel level;

    private static int paletteTex;
    /** The Iris pipeline whose image that is (a reload makes a new one, whose images start empty even when GL hands
     * out the same texture id again). */
    private static java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
    private static boolean fits;
    private static boolean started;
    private static boolean warned;
    private static IntBuffer upload;
    private static final int[] uploadRows = new int[ROWS];

    private FarHeights() {
    }

    /** The window's low corner for a camera position (cells). */
    static int low(double blocks) {
        int cell = (int) Math.floor(blocks / CELL);
        return Math.floorDiv(cell - SIZE / 2, STEP) * STEP;
    }

    /** The place tag for a cell (which multiple of 512 it is in), x and z. */
    static int tag(int cellX, int cellZ) {
        return ((cellX >> 9) & 15) | (((cellZ >> 9) & 15) << 4);
    }

    static int word(int cellX, int cellZ, int height) {
        int h = Math.max(0, Math.min(0xFFFF, height + HEIGHT_OFFSET));
        return FILLED | (tag(cellX, cellZ) << 16) | h;
    }

    /** The reader's results for some cells: absolute cells (x, z) and their words. */
    static void put(int generationRead, int[] cellX, int[] cellZ, int[] w, int count) {
        synchronized (LOCK) {
            if (generationRead != generation) {
                return;
            }
            for (int i = 0; i < count; i++) {
                int slot = (cellZ[i] & (SIZE - 1)) * SIZE + (cellX[i] & (SIZE - 1));
                words[slot] = w[i];
                dirtyRows[slot / BlockPalette.ROW] = true;
            }
            anyDirty = true;
        }
    }

    /** At the start of each frame (with the block grid, {@link WorldGrid}): pipeline, the Iris pipeline running the
     * pack; images, its custom image (texture) ids by name, 0 for none. */
    public static void beginFrame(Object pipeline, java.util.function.ToIntFunction<String> images) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int pal = images.applyAsInt(WorldGrid.PALETTE_IMAGE);
            if (pal == 0 || mc.level == null) {
                paletteTex = 0;
                return;
            }
            if (pal != paletteTex || owner.get() != pipeline) {
                paletteTex = pal;
                owner = new java.lang.ref.WeakReference<>(pipeline);
                int pw = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_WIDTH);
                int ph = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_HEIGHT);
                fits = pw == BlockPalette.ROW && ph >= FIRST_ROW + ROWS;
                if (fits) {
                    // A new image (a pack (re)load, which starts it empty): everything goes up again.
                    synchronized (LOCK) {
                        java.util.Arrays.fill(dirtyRows, true);
                        anyDirty = true;
                    }
                    LOG.info("Far heights for the shader pack: {} x {} cells of {} blocks{}", SIZE, SIZE, CELL,
                            ModList.get().isLoaded("distanthorizons") ? "" : " (no Distant Horizons: all unknown)");
                }
            }
            if (!fits) {
                return;
            }
            if (mc.level != level) {
                synchronized (LOCK) {
                    java.util.Arrays.fill(words, 0);
                    java.util.Arrays.fill(dirtyRows, true);
                    anyDirty = true;
                    generation++;
                }
                level = mc.level;
            }
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            lowX = low(camera.x);
            lowZ = low(camera.z);
            if (!started && ModList.get().isLoaded("distanthorizons")) {
                started = true;
                FarHeightsDh.start();
            }
            uploadRows();
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                LOG.error("Far heights for the shader pack failed; they stay as they were", e);
            }
        }
    }

    /** Sends the changed rows, a few at a time (64 rows are a whole window, 1 MB). */
    private static void uploadRows() {
        if (upload == null) {
            upload = MemoryUtil.memAllocInt(BlockPalette.ROW);
        }
        int[] rows = uploadRows;
        int n = 0;
        synchronized (LOCK) {
            if (!anyDirty) {
                return;
            }
            anyDirty = false;
            for (int r = 0; r < ROWS; r++) {
                if (dirtyRows[r]) {
                    if (n == 16) {
                        anyDirty = true; // the rest next frame
                        break;
                    }
                    dirtyRows[r] = false;
                    rows[n++] = r;
                }
            }
        }
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] saved = {glGetInteger(GL_UNPACK_ROW_LENGTH), glGetInteger(GL_UNPACK_SKIP_PIXELS),
            glGetInteger(GL_UNPACK_SKIP_ROWS), glGetInteger(GL_UNPACK_ALIGNMENT)};
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            for (int i = 0; i < n; i++) {
                int r = rows[i];
                upload.clear();
                synchronized (LOCK) {
                    upload.put(words, r * BlockPalette.ROW, BlockPalette.ROW);
                }
                upload.flip();
                glTextureSubImage2D(paletteTex, 0, 0, FIRST_ROW + r, BlockPalette.ROW, 1, GL_RED_INTEGER,
                        GL_UNSIGNED_INT, upload);
            }
        } finally {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, saved[0]);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, saved[1]);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, saved[2]);
            glPixelStorei(GL_UNPACK_ALIGNMENT, saved[3]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }
}
