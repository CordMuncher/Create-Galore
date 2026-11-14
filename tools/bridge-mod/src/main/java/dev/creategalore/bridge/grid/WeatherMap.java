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
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The weather far round the camera as Winds & Weather has it, for a shader pack that draws its own clouds. The mod
 * draws its regions' clouds in place of the game's, and such a pack switches the game's clouds off, so without this
 * the pack's sky knows nothing of where the mod's weather is fair and where it is heavy. A pack whose palette image
 * (cg_palette_img, see {@link WorldGrid}) is one row taller than the palette, the holes, the faces and the far
 * heights ({@link FarHeights}) gets the weather in that row; a pack that isn't, a game without Winds & Weather, or
 * the mod with its weather regions switched off, leaves the row at 0, which means "not known".
 *
 * One value for each point of a grid {@link #CELL} blocks apart (x and z multiples of it), 64 x 64 points round the
 * camera (10240 blocks across): the points from the camera's (its position divided by the spacing, rounded down)
 * less 32 to it plus 31, x and z. Point (x, z) keeps slot (x mod 64, z mod 64), word z * 64 + x of the row, so the
 * pack reads a point by its own number and only has to keep to that window (a point in from its edge, as the pack's
 * camera and this one can be a frame apart).
 *
 * Each word is the mod's own cloud field at the point (what its clouds would look like there, see
 * {@link WeatherMapWinds}):
 * <ul>
 * <li>bits 0..11: how much cloud, 0 the mod's clearest sky .. 4095 its heaviest thunderstorm (the weather a world
 * makes by itself keeps to about 1100 .. 2650, half of it under 1870; a region set to rain or a storm by command
 * goes to 2900 and over);</li>
 * <li>12..18: how dark the cloud is, 0 white .. 127 black;</li>
 * <li>19..25: how heaped it is, in hundredths (20 a flat sheet .. 120 towers);</li>
 * <li>26..30: how thick the mod draws its cloud layer, in pairs of blocks;</li>
 * <li>31: filled.</li>
 * </ul>
 *
 * The points a move of the window brings in are worked out at once; the rest are gone over again and again, a pass
 * every {@link #PASS_SECONDS} seconds (the weather drifts by at 5 to 12 blocks a second, a point's worth in a
 * quarter of a minute; a region changed by command shows within a pass), and sent when something changed, a few
 * times a second.
 */
public final class WeatherMap {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");

    public static final int SIZE = 64;
    public static final int CELL = 160;
    /** The row of the palette image it takes: the one after the far heights'. */
    static final int ROW = FarHeights.FIRST_ROW + FarHeights.ROWS;
    static final int FILLED = 1 << 31;
    private static final double PASS_SECONDS = 1.0;
    private static final long SEND_EVERY_NANOS = 200_000_000L;

    /** The words as the pack gets them (slot z * 64 + x). */
    private static final int[] words = new int[SIZE * SIZE];
    /** The point each slot holds; held says whether it holds one at all. */
    private static final int[] pointX = new int[SIZE * SIZE];
    private static final int[] pointZ = new int[SIZE * SIZE];
    private static final boolean[] held = new boolean[SIZE * SIZE];

    private static int paletteTex;
    /** The Iris pipeline whose image that is (a reload makes a new one, whose images start empty). */
    private static java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
    private static boolean fits;
    private static boolean winds;
    private static boolean warned;
    private static ClientLevel level;
    private static int lowX = Integer.MIN_VALUE, lowZ = Integer.MIN_VALUE;
    private static boolean allHeld;
    private static boolean empty = true;
    private static int next;
    private static long lastFrame, lastSend;
    private static boolean changed;
    private static IntBuffer upload;

    private WeatherMap() {
    }

    /** At the start of each frame (with the block grid, {@link WorldGrid}): pipeline, the Iris pipeline running the
     * pack; images, its custom image (texture) ids by name, 0 for none. */
    public static void beginFrame(Object pipeline, ToIntFunction<String> images) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int pal = images.applyAsInt(WorldGrid.PALETTE_IMAGE);
            if (pal == 0 || mc.level == null) {
                paletteTex = 0;
                level = null;
                return;
            }
            if (pal != paletteTex || owner.get() != pipeline) {
                paletteTex = pal;
                owner = new java.lang.ref.WeakReference<>(pipeline);
                int pw = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_WIDTH);
                int ph = glGetTextureLevelParameteri(pal, 0, GL_TEXTURE_HEIGHT);
                fits = pw == BlockPalette.ROW && ph >= ROW + 1;
                // A new image (a pack (re)load): everything is worked out and sent again, and until then (or for
                // good, with nothing to tell) the row says "not known" whatever the new image held.
                forget();
                if (fits) {
                    send(System.nanoTime());
                    winds = ModList.get().isLoaded("aeronauticswinds");
                    LOG.info("Weather map for the shader pack: {} x {} points {} blocks apart{}", SIZE, SIZE, CELL,
                            winds ? "" : " (no Winds & Weather: all unknown)");
                }
            }
            if (!fits || !winds) {
                return;
            }
            if (mc.level != level) {
                level = mc.level;
                forget();
            }
            long now = System.nanoTime();
            Object regions = WeatherMapWinds.regions(level);
            if (regions == null) {
                // The mod's weather regions are switched off: nothing is known.
                if (!empty) {
                    forget();
                    send(now);
                }
                return;
            }
            double time = level.getGameTime() + (double) mc.getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            int lx = Math.floorDiv((int) Math.floor(camera.x), CELL) - SIZE / 2;
            int lz = Math.floorDiv((int) Math.floor(camera.z), CELL) - SIZE / 2;
            boolean moved = false;
            if (lx != lowX || lz != lowZ || !allHeld) {
                lowX = lx;
                lowZ = lz;
                for (int z = lz; z < lz + SIZE; z++) {
                    for (int x = lx; x < lx + SIZE; x++) {
                        int slot = (z & (SIZE - 1)) * SIZE + (x & (SIZE - 1));
                        if (!held[slot] || pointX[slot] != x || pointZ[slot] != z) {
                            fill(regions, slot, x, z, time);
                            moved = true;
                        }
                    }
                }
                allHeld = true;
            }
            // The pass over what is there already: its share for the time since the last frame.
            double seconds = Math.min(0.25, Math.max(0.0, (now - lastFrame) * 1e-9));
            lastFrame = now;
            int count = Math.min(SIZE * SIZE / 4, (int) Math.ceil(SIZE * SIZE * seconds / PASS_SECONDS));
            for (int i = 0; i < count; i++) {
                int slot = next;
                next = (next + 1) & (SIZE * SIZE - 1);
                int was = words[slot];
                fill(regions, slot, pointX[slot], pointZ[slot], time);
                changed |= was != words[slot];
            }
            if (moved || (changed && now - lastSend >= SEND_EVERY_NANOS)) {
                send(now);
            }
        } catch (RuntimeException | LinkageError e) {
            // Not tried again until the pack is loaded anew (another version of the mod, say, fails every time).
            winds = false;
            if (!warned) {
                warned = true;
                LOG.error("The weather map for the shader pack failed; it stays as it was", e);
            }
        }
    }

    private static void fill(Object regions, int slot, int x, int z, double time) {
        words[slot] = FILLED | WeatherMapWinds.word(regions, x * CELL, z * CELL, time);
        pointX[slot] = x;
        pointZ[slot] = z;
        held[slot] = true;
        empty = false;
    }

    private static void forget() {
        java.util.Arrays.fill(words, 0);
        java.util.Arrays.fill(held, false);
        allHeld = false;
        empty = true;
        changed = false;
    }

    /** Sends the row (16 KB). */
    private static void send(long now) {
        if (upload == null) {
            upload = MemoryUtil.memAllocInt(SIZE * SIZE);
        }
        lastSend = now;
        changed = false;
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] saved = {glGetInteger(GL_UNPACK_ROW_LENGTH), glGetInteger(GL_UNPACK_SKIP_PIXELS),
            glGetInteger(GL_UNPACK_SKIP_ROWS), glGetInteger(GL_UNPACK_ALIGNMENT)};
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            upload.clear();
            upload.put(words);
            upload.flip();
            glTextureSubImage2D(paletteTex, 0, 0, ROW, SIZE * SIZE, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, upload);
        } finally {
            glPixelStorei(GL_UNPACK_ROW_LENGTH, saved[0]);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, saved[1]);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, saved[2]);
            glPixelStorei(GL_UNPACK_ALIGNMENT, saved[3]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }
}
