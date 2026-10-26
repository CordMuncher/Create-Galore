package dev.creategalore.bridge.client;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.blaze3d.platform.Window;
import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.DisplaySettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The frame counter the display settings turn on (fpsCounter): basic, a small frame rate in a corner, or detailed,
 * also the frame time, the 1% and 0.1% lows of the last 10 seconds (the frame rate of the slowest 1% and 0.1% of the
 * frames, averaged) and a graph of the last seconds' frame times. A frame is timed from one start of the game's frame
 * (Minecraft.runTick) to the next, so every wait in between counts: the frame cap, Reflex, the GPU. The numbers change
 * four times a second, so they can be read. The same size on the screen whatever the GUI scale; hidden under F3 (which
 * has its own) and with the HUD (F1). In the top right corner it moves down under the status effect icons.
 *
 * fpsCounterDim, for OLED screens: see-through, no backing, and the counter moves by a pixel every two minutes so no
 * pixel stays lit in one place.
 */
public final class FpsOverlay {
    public static final String OFF = "off";
    public static final String BASIC = "basic";
    public static final String DETAILED = "detailed";
    public static final String TOP_LEFT = "top-left";
    public static final String TOP_RIGHT = "top-right";
    public static final String BOTTOM_LEFT = "bottom-left";
    public static final String BOTTOM_RIGHT = "bottom-right";

    private static final int RING = 1 << 14; // 10 seconds at up to 1600 fps
    private static final long SECOND = 1_000_000_000L;
    private static final long LOWS_SPAN = 10 * SECOND;
    private static final long UPDATE = SECOND / 4;
    /** The graph: this many columns over this many ns (25 ms a column), and this tall (font pixels). */
    private static final int GRAPH_WIDTH = 100;
    private static final long GRAPH_SPAN = 2_500_000_000L;
    private static final int GRAPH_HEIGHT = 20;
    private static final long SHIFT_EVERY = 120 * SECOND;
    private static final int[][] SHIFTS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /** When each frame ended and how long it took (ns), the newest at count - 1 (mod RING). */
    private static final long[] ends = new long[RING];
    private static final long[] times = new long[RING];
    private static int count;
    private static long last;

    private static int generation = -1;
    private static String mode = OFF;
    private static String corner = TOP_RIGHT;
    private static boolean dim;

    private static long updated;
    private static String fpsText = "";
    private static String frameText = "";
    private static String lowsText = "";
    /** The median frame time of the last 10 seconds (ns): the graph's scale. */
    private static long median;
    private static long[] scratch = new long[1024];
    private static final long[] columns = new long[GRAPH_WIDTH];

    private FpsOverlay() {
    }

    /** Client setup: draws above every other HUD layer. */
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "fps_counter"), FpsOverlay::render);
    }

    /** First thing in each frame (Minecraft.runTick). */
    public static void frame() {
        long now = System.nanoTime();
        if (last != 0L) {
            int slot = count & (RING - 1);
            ends[slot] = now;
            times[slot] = now - last;
            count++;
        }
        last = now;
    }

    private static void readSettings() {
        generation = DisplaySettings.generation();
        String m = DisplaySettings.getString(DisplaySettings.FPS_COUNTER, OFF).toLowerCase(Locale.ROOT);
        mode = m.equals(BASIC) || m.equals(DETAILED) ? m : OFF;
        String c = DisplaySettings.getString(DisplaySettings.FPS_COUNTER_CORNER, TOP_RIGHT).toLowerCase(Locale.ROOT);
        corner = switch (c) {
            case TOP_LEFT, BOTTOM_LEFT, BOTTOM_RIGHT -> c;
            default -> TOP_RIGHT;
        };
        dim = DisplaySettings.get(DisplaySettings.FPS_COUNTER_DIM);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (generation != DisplaySettings.generation()) {
            readSettings();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mode.equals(OFF) || mc.getDebugOverlay().showDebugScreen() || count == 0) {
            return;
        }
        long now = System.nanoTime();
        if (now - updated >= UPDATE) {
            update(now);
            updated = now;
        }
        boolean detailed = mode.equals(DETAILED);
        Font font = mc.font;
        Window window = mc.getWindow();
        // Whole screen pixels per font pixel, so the text stays sharp: 1 up to 720 lines, 2 at 1080 and 1440, 3 at 4K.
        int pixels = Math.max(1, (int) Math.round(window.getHeight() / 720.0));
        float scale = (float) (pixels / window.getGuiScale());
        int screenWidth = window.getWidth() / pixels;
        int screenHeight = window.getHeight() / pixels;

        int pad = dim ? 0 : 2;
        int width = font.width(fpsText);
        int height = 9;
        if (detailed) {
            width = Math.max(width + 4 + font.width(frameText), Math.max(font.width(lowsText), GRAPH_WIDTH));
            height = 9 + 10 + 2 + GRAPH_HEIGHT;
        }
        boolean right = corner.equals(TOP_RIGHT) || corner.equals(BOTTOM_RIGHT);
        boolean top = corner.equals(TOP_LEFT) || corner.equals(TOP_RIGHT);
        int margin = 2;
        int x = right ? screenWidth - margin - pad - width : margin + pad;
        int y = top ? margin + pad : screenHeight - margin - pad - height;
        if (corner.equals(TOP_RIGHT)) {
            y += (int) Math.ceil(effectRows(mc) * 26 * window.getGuiScale() / pixels); // under the status effect icons
        }
        if (dim) {
            int[] shift = SHIFTS[(int) ((now / SHIFT_EVERY) % SHIFTS.length)];
            x += right ? -shift[0] : shift[0];
            y += top ? shift[1] : -shift[1];
        }
        int alpha = dim ? 0x70000000 : 0xFF000000;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        if (!dim && detailed) {
            graphics.fill(x - pad, y - pad, x + width + pad, y + height + pad, 0x90000000);
        }
        graphics.drawString(font, fpsText, x, y, alpha | 0xFFFFFF, !dim);
        if (detailed) {
            graphics.drawString(font, frameText, x + width - font.width(frameText), y, alpha | 0xAAAAAA, !dim);
            graphics.drawString(font, lowsText, x, y + 10, alpha | 0xAAAAAA, !dim);
            graph(graphics, x + width - GRAPH_WIDTH, y + 21, now, dim ? 0x60000000 : 0xFF000000);
        }
        graphics.pose().popPose();
    }

    /** How many rows of status effect icons the HUD shows in the top right corner: 0, 1 or 2. */
    private static int effectRows(Minecraft mc) {
        if (mc.player == null) {
            return 0;
        }
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (effect.showIcon()) {
                if (effect.getEffect().value().isBeneficial()) {
                    beneficial = true;
                } else {
                    harmful = true;
                }
            }
        }
        return harmful ? 2 : beneficial ? 1 : 0;
    }

    /** The numbers shown: the frame rate and frame time of the last second, the lows and median of the last 10 seconds. */
    private static void update(long now) {
        int n = Math.min(count, RING);
        long second = 0;
        int inSecond = 0;
        int inSpan = 0;
        if (scratch.length < n) {
            scratch = new long[RING];
        }
        for (int i = 1; i <= n; i++) {
            int slot = (count - i) & (RING - 1);
            long age = now - ends[slot];
            if (age > LOWS_SPAN) {
                break;
            }
            if (age <= SECOND) {
                second += times[slot];
                inSecond++;
            }
            scratch[inSpan++] = times[slot];
        }
        if (inSecond == 0) {
            // Slower than a frame a second: the last frame alone.
            int slot = (count - 1) & (RING - 1);
            second = times[slot];
            inSecond = 1;
        }
        double fps = inSecond * 1.0e9 / second;
        fpsText = Math.round(fps) + " fps";
        frameText = String.format(Locale.ROOT, "%.1f ms", second / 1.0e6 / inSecond);
        if (inSpan == 0) {
            lowsText = "";
            return;
        }
        Arrays.sort(scratch, 0, inSpan);
        median = scratch[inSpan / 2];
        lowsText = "1% low " + low(inSpan, 100) + "  0.1% low " + low(inSpan, 1000);
    }

    /** The frame rate of the slowest 1/{@code part} of the {@code n} sorted frame times in scratch (at least one frame). */
    private static long low(int n, int part) {
        int slowest = Math.max(1, n / part);
        long sum = 0;
        for (int i = n - slowest; i < n; i++) {
            sum += scratch[i];
        }
        return Math.round(slowest * 1.0e9 / sum);
    }

    /**
     * The frame times of the last 2.5 seconds, newest on the right: each column the longest frame within its 25 ms, as
     * tall as that frame against 2.5 times the median (longer ones stop at the top); green, yellow from 1.5 times the
     * median, red from 2.5 times. A faint line marks the median.
     */
    private static void graph(GuiGraphics graphics, int x, int y, long now, int alpha) {
        Arrays.fill(columns, 0L);
        long from = now - GRAPH_SPAN;
        long column = GRAPH_SPAN / GRAPH_WIDTH;
        int n = Math.min(count, RING);
        for (int i = 1; i <= n; i++) {
            int slot = (count - i) & (RING - 1);
            long end = ends[slot];
            if (end <= from) {
                break;
            }
            long time = times[slot];
            int first = (int) Math.max(0, (end - time - from) / column);
            int lastColumn = (int) Math.min(GRAPH_WIDTH - 1, (end - from) / column);
            for (int c = first; c <= lastColumn; c++) {
                columns[c] = Math.max(columns[c], time);
            }
        }
        long full = Math.max(1L, median * 5 / 2);
        if (alpha == 0xFF000000) {
            graphics.fill(x, y, x + GRAPH_WIDTH, y + GRAPH_HEIGHT, 0x40000000);
        }
        for (int c = 0; c < GRAPH_WIDTH; c++) {
            long time = columns[c];
            if (time == 0L) {
                continue;
            }
            int bar = (int) Math.max(1, Math.min(GRAPH_HEIGHT, time * GRAPH_HEIGHT / full));
            int color = time * 2 >= median * 5 ? 0xFF5555 : time * 2 >= median * 3 ? 0xFFDD55 : 0x55DD55;
            graphics.fill(x + c, y + GRAPH_HEIGHT - bar, x + c + 1, y + GRAPH_HEIGHT, alpha | color);
        }
        int line = y + GRAPH_HEIGHT - (int) (median * GRAPH_HEIGHT / full);
        graphics.fill(x, line, x + GRAPH_WIDTH, line + 1, (alpha == 0xFF000000 ? 0x50000000 : 0x20000000) | 0xFFFFFF);
    }
}
