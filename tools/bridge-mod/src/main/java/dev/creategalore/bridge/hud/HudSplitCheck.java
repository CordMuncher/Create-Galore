package dev.creategalore.bridge.hud;

import static org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_PACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_PACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_PACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL45C.glGetTextureImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * hudSplitCheck: reads one frame and its world copy back (a stall, for testing only) and checks {@link HudSplit}'s
 * split on every pixel. Leak: alpha 0 but the frame differs from the copy by more than 1 of 255 (the HUD drew there
 * without coverage; a pass that only gets the copy and alpha loses it). Under: the HUD's colour
 * frame - (1 - alpha) * copy below 0 by more than 2.5 of 255 (the HUD darkened the world without coverage). Over:
 * above alpha by as much (light added on top; exact with the HUD's colour, lost with alpha alone). Logs the counts
 * and where they are, and saves the frame, the copy, alpha, the HUD over a checkerboard and the marks as pictures
 * when the scene changed (another screen, or 5% more or less HUD) or something is marked.
 */
final class HudSplitCheck {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Pictures saved per game, so a long session can't fill the disk; the log lines go on. */
    private static final int MAX_SAVED = 100;

    private static int count;
    private static int saved;
    /** The last saved check's screen and HUD pixels: an unchanged scene isn't saved again. */
    private static String savedScreen = "";
    private static int savedHud = -1;

    private HudSplitCheck() {}

    static void run(int frameTexture, int hudlessTexture, int width, int height, String takenAt) {
        int bytes = width * height * 4;
        ByteBuffer frame = MemoryUtil.memAlloc(bytes);
        ByteBuffer hudless = MemoryUtil.memAlloc(bytes);
        Pictures pictures = null;
        try {
            read(frameTexture, frame);
            read(hudlessTexture, hudless);
            count++;
            Stats ui = new Stats();
            Stats full = new Stats();
            Stats leak = new Stats();
            Stats under = new Stats();
            Stats over = new Stats();
            int worstLeak = 0;
            float[] u = new float[3];
            pictures = saved < MAX_SAVED ? new Pictures(width, height) : null;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    int a = frame.get(i + 3) & 0xFF;
                    boolean isLeak = false;
                    boolean isUnder = false;
                    boolean isOver = false;
                    for (int c = 0; c < 3; c++) {
                        int f = frame.get(i + c) & 0xFF;
                        int h = hudless.get(i + c) & 0xFF;
                        if (a == 0 && Math.abs(f - h) > 1) {
                            isLeak = true;
                            worstLeak = Math.max(worstLeak, Math.abs(f - h));
                        }
                        u[c] = f - (255 - a) * h / 255.0f;
                        isUnder |= u[c] < -2.5f;
                        isOver |= u[c] > a + 2.5f;
                    }
                    // Pictures are top row first; OpenGL's rows start at the bottom.
                    int row = height - 1 - y;
                    if (a > 0) {
                        ui.add(x, row);
                    }
                    if (a == 255) {
                        full.add(x, row);
                    }
                    if (isLeak) {
                        leak.add(x, row);
                    }
                    if (isUnder) {
                        under.add(x, row);
                    }
                    if (isOver) {
                        over.add(x, row);
                    }
                    if (pictures != null) {
                        pictures.set(x, row, frame, hudless, i, a, u, isLeak, isUnder, isOver);
                    }
                }
            }
            Screen screen = Minecraft.getInstance().screen;
            String screenName = screen == null ? "none" : screen.getClass().getSimpleName();
            String files = "";
            boolean changed = !screenName.equals(savedScreen) || Math.abs(ui.n - savedHud) > Math.max(savedHud, 1000) / 20;
            if (pictures != null && (changed || leak.n + under.n + over.n > 0)) {
                Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("hud-split");
                String name = String.format(Locale.ROOT, "check-%03d", count);
                if (pictures.save(dir, name)) {
                    files = ", pictures screenshots/hud-split/" + name + "-*.png";
                    saved++;
                    savedScreen = screenName;
                    savedHud = ui.n;
                }
            }
            LOG.info(String.format(Locale.ROOT,
                    "HUD split check %d: %dx%d, copy taken %s, screen %s: HUD %s, opaque %s; leaks %s (worst %d/255); under %s; over %s%s",
                    count, width, height, takenAt, screenName,
                    ui.describe(width, height), full.describe(width, height), leak.describe(width, height), worstLeak,
                    under.describe(width, height), over.describe(width, height), files));
        } catch (RuntimeException e) {
            LOG.warn("HUD split check failed: {}", e.toString());
        } finally {
            if (pictures != null) {
                pictures.close();
            }
            MemoryUtil.memFree(frame);
            MemoryUtil.memFree(hudless);
        }
    }

    private static void read(int texture, ByteBuffer into) {
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        try {
            glGetTextureImage(texture, 0, GL_RGBA, GL_UNSIGNED_BYTE, into);
        } finally {
            glPixelStorei(GL_PACK_ALIGNMENT, alignment);
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        }
    }

    /** How many pixels and the box around them. */
    private static final class Stats {
        int n;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        void add(int x, int y) {
            n++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        String describe(int width, int height) {
            if (n == 0) {
                return "0 px";
            }
            return String.format(Locale.ROOT, "%d px (%.2f%%) in x %d-%d y %d-%d", n, 100.0 * n / ((double) width * height),
                    minX, maxX, minY, maxY);
        }
    }

    private static final class Pictures {
        final NativeImage frame;
        final NativeImage hudless;
        final NativeImage alpha;
        final NativeImage ui;
        final NativeImage marks;

        Pictures(int width, int height) {
            frame = new NativeImage(width, height, false);
            hudless = new NativeImage(width, height, false);
            alpha = new NativeImage(width, height, false);
            ui = new NativeImage(width, height, false);
            marks = new NativeImage(width, height, false);
        }

        void set(int x, int y, ByteBuffer f, ByteBuffer h, int i, int a, float[] u, boolean leak, boolean under, boolean over) {
            int fr = f.get(i) & 0xFF;
            int fg = f.get(i + 1) & 0xFF;
            int fb = f.get(i + 2) & 0xFF;
            frame.setPixelRGBA(x, y, abgr(fr, fg, fb));
            hudless.setPixelRGBA(x, y, abgr(h.get(i) & 0xFF, h.get(i + 1) & 0xFF, h.get(i + 2) & 0xFF));
            alpha.setPixelRGBA(x, y, abgr(a, a, a));
            int grey = ((x >> 4) + (y >> 4)) % 2 == 0 ? 77 : 128;
            float rest = (255 - a) / 255.0f;
            ui.setPixelRGBA(x, y, abgr(clamp(u[0] + rest * grey), clamp(u[1] + rest * grey), clamp(u[2] + rest * grey)));
            int mark;
            if (leak) {
                mark = abgr(255, 0, 255);
            } else if (under) {
                mark = abgr(255, 255, 0);
            } else if (over) {
                mark = abgr(0, 255, 255);
            } else {
                mark = abgr(fr * 35 / 100, fg * 35 / 100, fb * 35 / 100);
            }
            marks.setPixelRGBA(x, y, mark);
        }

        boolean save(Path dir, String name) {
            try {
                Files.createDirectories(dir);
                frame.writeToFile(dir.resolve(name + "-frame.png"));
                hudless.writeToFile(dir.resolve(name + "-hudless.png"));
                alpha.writeToFile(dir.resolve(name + "-alpha.png"));
                ui.writeToFile(dir.resolve(name + "-ui.png"));
                marks.writeToFile(dir.resolve(name + "-marks.png"));
                return true;
            } catch (IOException e) {
                LOG.warn("HUD split check: could not save pictures: {}", e.toString());
                return false;
            }
        }

        void close() {
            frame.close();
            hudless.close();
            alpha.close();
            ui.close();
            marks.close();
        }

        private static int clamp(float v) {
            return Math.max(0, Math.min(255, Math.round(v)));
        }

        private static int abgr(int r, int g, int b) {
            return 0xFF000000 | b << 16 | g << 8 | r;
        }
    }
}
