package dev.creategalore.bridge.client;

import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33C.glQueryCounter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.DisplaySettings;
import net.irisshaders.iris.Iris;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * The GPU cost meter (its key, F10 by default, turns it on and off): what each part of a frame costs the GPU, with any
 * shader pack. GL timestamp queries mark every point where the frame's work changes hands: each Iris composite pass by
 * its program name (begin, prepare, deferred and composite stages, compute-only passes by their first compute
 * program), each shadow composite pass (shadowcomp, shadowcomp1 ...), the shadow map draw ("shadow"), the gbuffer
 * phases Iris switches between ("gbuffers:terrain_solid", "gbuffers:entities" ...), the final pass ("final"), the
 * Bridge's own DLSS or FSR 3 call ("dlss", "fsr") and everything else in the frame ("other": vanilla drawing, the GUI,
 * Distant Horizons outside a phase, and any time the GPU waited for the CPU in between). The time between two marks
 * belongs to the innermost part running then, so nested parts (the shadowcomp passes inside the shadow draw, DLSS
 * inside its TAA pass) are not counted twice and the parts add up to the frame.
 *
 * Results are read a few frames later from a ring of {@value #RING} frames, only once the GPU says they are there
 * (GL_QUERY_RESULT_AVAILABLE), so the meter never makes the CPU wait for the GPU; when the GPU is a whole ring behind,
 * frames go untimed instead. While the meter is off it issues no queries at all.
 *
 * A shader pack may group its programs into effects with shaders/cost_meter.properties: {@code group.<Name> = <program>
 * <program> ...} (spaces or commas between them; a name ending in * takes every program starting with the rest, as
 * {@code composite*}). Programs no group names show under their own name. The overlay (top left, twice a second)
 * shows the average frame rate, the 1% low of the last 10 seconds, the CPU and GPU time of a frame, how busy the GPU
 * was and the groups' ms per frame, largest first. logs/create-galore-cost.log gets the same every second, and a
 * hitch line for each frame in a world over 2.5 times the median frame time or over 50 ms: its CPU and GPU time and
 * its three biggest passes.
 *
 * CPU time: from the start of the game's frame (Minecraft.runTick) to handing it to the window, so waits inside the
 * frame count but the swap does not. GPU time: from the GPU reaching the frame's first mark to its last. GPU busy: the
 * GPU time over the time between the starts of two frames on the GPU.
 */
public final class CostMeter {
    private static final Logger LOG = LogUtils.getLogger();

    static final KeyMapping KEY = new KeyMapping("key.create_galore_bridge.cost_meter", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories.misc");

    public static final String OTHER = "other";
    public static final String SHADOW = "shadow";
    public static final String FINAL = "final";
    public static final String DLSS = "dlss";
    public static final String FSR = "fsr";
    static final String FILE = "shaders/cost_meter.properties";
    static final Path LOG_FILE = Path.of("logs", "create-galore-cost.log");

    private static final int RING = 6;
    /** Marks a frame can hold; past this the rest of the frame counts to the last part marked. */
    private static final int MAX_MARKS = 1024;
    private static final long SECOND = 1_000_000_000L;
    private static final long UPDATE = SECOND / 2;
    private static final long HITCH_NANOS = 50_000_000L;
    private static final int WALL_RING = 1 << 14; // 10 seconds at up to 1600 fps
    private static final long LOWS_SPAN = 10 * SECOND;
    private static final int MAX_LINES = 18;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT);

    /** Each gbuffer phase's part name, by ordinal, made the first time Iris switches to it. */
    private static final String[] PHASES = new String[64];

    /** One frame's marks: query ids, and the part that runs from each mark to the next (null at the frame's end). */
    private static final class Frame {
        int[] ids = new int[0];
        String[] labels = new String[0];
        long[] stamps = new long[0];
        int marks;
        long seq;
        long cpuStart;
        long cpuNanos;
        long wallNanos;
        boolean pending;
    }

    /** Sums over a stretch of time (the overlay's half second, the log's second). */
    private static final class Span {
        long start;
        int frames;
        long wall;
        int timed;
        long cpu;
        long gpu;
        long busySpan;
        long busyInterval;
        final Map<String, long[]> parts = new HashMap<>();

        void clear(long now) {
            start = now;
            frames = 0;
            wall = 0;
            timed = 0;
            cpu = 0;
            gpu = 0;
            busySpan = 0;
            busyInterval = 0;
            parts.values().forEach(v -> v[0] = 0);
        }
    }

    private static final Frame[] frames = new Frame[RING];

    static {
        for (int i = 0; i < RING; i++) {
            frames[i] = new Frame();
        }
    }

    /** -Dcreate_galore_bridge.costMeter=true starts the game with the meter on (for test runs). */
    private static volatile boolean wanted = Boolean.getBoolean("create_galore_bridge.costMeter");
    private static boolean on;
    private static boolean recording;
    private static Frame current;
    private static long seq;
    private static long oldestPending = -1;
    private static String label;
    private static final String[] stack = new String[64];
    private static int depth;
    private static String phase;
    private static int shadowDepth;
    private static long untimed;

    private static long frameStartNanos;
    private static Frame lastStarted;
    private static long lastGpuStart;
    private static long lastGpuSeq = -1;

    private static final long[] wallEnds = new long[WALL_RING];
    private static final long[] wallTimes = new long[WALL_RING];
    private static int wallCount;
    private static long[] scratch = new long[1024];
    private static long median;

    private static final Span shown = new Span();
    private static final Span logged = new Span();
    private static final Map<String, long[]> framePart = new HashMap<>();
    private static List<String> lines = List.of();
    private static BufferedWriter log;

    /** Program name (or prefix, ending in *) to group, from the shader pack's cost_meter.properties. */
    private static Map<String, String> groups = Map.of();
    private static List<Map.Entry<String, String>> prefixes = List.of();
    private static final Map<String, String> groupOf = new HashMap<>();
    private static String packName = "none";

    private CostMeter() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "cost_meter"), CostMeter::render));
        NeoForge.EVENT_BUS.addListener(CostMeter::tick);
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent e) -> closeLog());
    }

    private static void tick(ClientTickEvent.Post event) {
        boolean pressed = false;
        while (KEY.consumeClick()) {
            pressed = !pressed;
        }
        if (pressed) {
            wanted = !wanted;
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(wanted
                    ? "create_galore_bridge.cost_meter.on" : "create_galore_bridge.cost_meter.off"), false);
        }
    }

    // ---- Marks (render thread; all of them return at once while the meter is off) ----

    /** A part starts inside whatever runs now (a composite pass, the final pass, DLSS). */
    public static void push(String name) {
        if (!recording) {
            return;
        }
        if (depth < stack.length) {
            stack[depth] = name;
        }
        depth++;
        mark();
    }

    /** The innermost part ends. */
    public static void pop() {
        if (!recording || depth == 0) {
            return;
        }
        depth--;
        mark();
    }

    public static void shadowBegin() {
        if (!recording) {
            return;
        }
        shadowDepth++;
        push(SHADOW);
    }

    public static void shadowEnd() {
        if (!recording || shadowDepth == 0) {
            return;
        }
        shadowDepth--;
        pop();
    }

    /**
     * Iris switches gbuffer phase ({@code name} null: none, else WorldRenderingPhase's name). Phases the shadow draw
     * switches through count to the shadow draw.
     */
    public static void phase(int ordinal, String name) {
        if (!recording || shadowDepth > 0) {
            return;
        }
        if (name == null) {
            phase = null;
        } else if (ordinal < PHASES.length) {
            if (PHASES[ordinal] == null) {
                PHASES[ordinal] = "gbuffers:" + name.toLowerCase(Locale.ROOT);
            }
            phase = PHASES[ordinal];
        } else {
            phase = "gbuffers:" + name.toLowerCase(Locale.ROOT);
        }
        mark();
    }

    private static void mark() {
        String now = depth > 0 ? stack[Math.min(depth, stack.length) - 1] : phase != null ? phase : OTHER;
        if (now.equals(label) || current.marks >= MAX_MARKS - 1) {
            return;
        }
        stamp(now);
    }

    private static void stamp(String part) {
        Frame f = current;
        if (f.marks == f.ids.length) {
            int grow = Math.max(32, f.ids.length);
            int[] ids = Arrays.copyOf(f.ids, f.ids.length + grow);
            int[] fresh = new int[grow];
            glGenQueries(fresh);
            System.arraycopy(fresh, 0, ids, f.ids.length, grow);
            f.ids = ids;
            f.labels = Arrays.copyOf(f.labels, ids.length);
            f.stamps = new long[ids.length];
        }
        glQueryCounter(f.ids[f.marks], GL_TIMESTAMP);
        f.labels[f.marks++] = part;
        label = part;
    }

    // ---- Frames ----

    /** First thing in each frame (Minecraft.runTick). */
    public static void frameStart() {
        long now = System.nanoTime();
        if (recording) {
            frameEnd(); // the frame before never reached the window
        }
        if (on && frameStartNanos != 0L) {
            long wall = now - frameStartNanos;
            int slot = wallCount & (WALL_RING - 1);
            wallEnds[slot] = now;
            wallTimes[slot] = wall;
            wallCount++;
            if (lastStarted != null) {
                lastStarted.wallNanos = wall;
            }
            shown.frames++;
            shown.wall += wall;
            logged.frames++;
            logged.wall += wall;
        }
        frameStartNanos = now;
        lastStarted = null;
        if (wanted != on) {
            if (wanted) {
                start(now);
            } else {
                stop();
            }
        }
        if (!on) {
            return;
        }
        collect();
        if (now - shown.start >= UPDATE) {
            updateLines(now);
            shown.clear(now);
        }
        if (now - logged.start >= SECOND) {
            logSecond();
            logged.clear(now);
        }
        Frame f = frames[(int) (seq % RING)];
        if (f.pending) {
            untimed++;
            return;
        }
        f.seq = seq++;
        f.marks = 0;
        f.cpuStart = now;
        f.wallNanos = 0L;
        current = f;
        lastStarted = f;
        recording = true;
        depth = 0;
        phase = null;
        shadowDepth = 0;
        label = null;
        stamp(OTHER);
    }

    /** The frame is handed to the window. */
    public static void frameEnd() {
        if (!recording) {
            return;
        }
        Frame f = current;
        stamp(null);
        f.cpuNanos = System.nanoTime() - f.cpuStart;
        f.pending = true;
        if (oldestPending < 0) {
            oldestPending = f.seq;
        }
        recording = false;
    }

    /** Reads the frames the GPU has finished, oldest first, without waiting for any. */
    private static void collect() {
        while (oldestPending >= 0 && oldestPending < seq) {
            Frame f = frames[(int) (oldestPending % RING)];
            if (!f.pending || f.seq != oldestPending) {
                oldestPending++;
                continue;
            }
            for (int i = f.marks - 1; i >= 0; i--) {
                if (glGetQueryObjecti(f.ids[i], GL_QUERY_RESULT_AVAILABLE) == 0) {
                    return;
                }
            }
            for (int i = 0; i < f.marks; i++) {
                f.stamps[i] = glGetQueryObjecti64(f.ids[i], GL_QUERY_RESULT);
            }
            f.pending = false;
            oldestPending++;
            done(f);
        }
        if (oldestPending >= seq) {
            oldestPending = -1;
        }
    }

    private static void done(Frame f) {
        if (f.marks < 2) {
            return;
        }
        long gpu = f.stamps[f.marks - 1] - f.stamps[0];
        framePart.values().forEach(v -> v[0] = 0);
        for (int i = 0; i < f.marks - 1; i++) {
            long time = Math.max(0L, f.stamps[i + 1] - f.stamps[i]);
            framePart.computeIfAbsent(f.labels[i], k -> new long[1])[0] += time;
        }
        long interval = lastGpuSeq == f.seq - 1 ? f.stamps[0] - lastGpuStart : 0L;
        lastGpuSeq = f.seq;
        lastGpuStart = f.stamps[0];
        for (Span w : new Span[] {shown, logged}) {
            w.timed++;
            w.cpu += f.cpuNanos;
            w.gpu += gpu;
            if (interval > 0L) {
                w.busySpan += gpu;
                w.busyInterval += interval;
            }
            for (Map.Entry<String, long[]> part : framePart.entrySet()) {
                if (part.getValue()[0] != 0L) {
                    w.parts.computeIfAbsent(part.getKey(), k -> new long[1])[0] += part.getValue()[0];
                }
            }
        }
        // Loading screens stutter by nature: hitches count only in a world.
        long wall = f.wallNanos;
        if (Minecraft.getInstance().level != null && (wall > HITCH_NANOS || (median > 0L && wall * 2 > median * 5))) {
            hitch(f, wall, gpu);
        }
    }

    // ---- On and off ----

    private static void start(long now) {
        on = true;
        seq = 0;
        oldestPending = -1;
        lastGpuSeq = -1;
        untimed = 0;
        wallCount = 0;
        median = 0;
        frameStartNanos = now;
        shown.clear(now);
        logged.clear(now);
        lines = List.of();
        readGroups();
        openLog();
    }

    private static void stop() {
        on = false;
        recording = false;
        for (Frame f : frames) {
            if (f.ids.length > 0) {
                glDeleteQueries(f.ids);
            }
            f.ids = new int[0];
            f.labels = new String[0];
            f.stamps = new long[0];
            f.marks = 0;
            f.pending = false;
        }
        closeLog();
    }

    /** A shader pack loaded (or shaders turned off): its groups apply from now. */
    public static void onPipelineCreated() {
        if (on) {
            readGroups();
            write("# shader pack " + packName + groupsText());
        }
    }

    // ---- Groups ----

    private static void readGroups() {
        Map<String, String> exact = new LinkedHashMap<>();
        List<Map.Entry<String, String>> starts = new ArrayList<>();
        packName = "none";
        try {
            String name = Iris.getCurrentPackName();
            if (name != null && Iris.getIrisConfig().areShadersEnabled() && Iris.getCurrentPack().isPresent()) {
                packName = name;
                Properties props = load(Iris.getShaderpacksDirectory().resolve(name));
                if (props != null) {
                    for (String key : props.stringPropertyNames()) {
                        if (!key.startsWith("group.") || key.length() == 6) {
                            continue;
                        }
                        String group = key.substring(6);
                        for (String program : props.getProperty(key).trim().split("[\\s,]+")) {
                            if (program.isEmpty()) {
                                continue;
                            }
                            if (program.endsWith("*")) {
                                starts.add(Map.entry(program.substring(0, program.length() - 1), group));
                            } else {
                                exact.put(program, group);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("Cost meter: could not read the shader pack's {}", FILE, t);
        }
        // The longest prefix wins.
        starts.sort((a, b) -> b.getKey().length() - a.getKey().length());
        groups = exact;
        prefixes = starts;
        groupOf.clear();
    }

    private static Properties load(Path pack) throws IOException {
        if (Files.isDirectory(pack)) {
            Path file = pack.resolve(FILE);
            return Files.isRegularFile(file) ? read(file) : null;
        }
        if (Files.isRegularFile(pack)) {
            try (FileSystem zip = FileSystems.newFileSystem(pack)) {
                Path file = zip.getPath(FILE);
                return Files.isRegularFile(file) ? read(file) : null;
            }
        }
        return null;
    }

    private static Properties read(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return props;
    }

    /** The group a part counts to: its pack's group, else its own name. */
    static String group(String part) {
        return groupOf.computeIfAbsent(part, p -> {
            String group = groups.get(p);
            if (group != null) {
                return group;
            }
            for (Map.Entry<String, String> prefix : prefixes) {
                if (p.startsWith(prefix.getKey())) {
                    return prefix.getValue();
                }
            }
            return p;
        });
    }

    private static String groupsText() {
        if (groups.isEmpty() && prefixes.isEmpty()) {
            return ", no " + FILE;
        }
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        groups.forEach((program, group) -> byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(program));
        prefixes.forEach(e -> byGroup.computeIfAbsent(e.getValue(), g -> new ArrayList<>()).add(e.getKey() + "*"));
        StringBuilder text = new StringBuilder(", groups:");
        byGroup.forEach((group, programs) -> text.append(' ').append(group).append('=').append(String.join(" ", programs)).append(';'));
        return text.toString();
    }

    /** ms per timed frame of each group in {@code w}, largest first. */
    private static List<Map.Entry<String, Double>> byGroup(Span w) {
        Map<String, Double> sums = new HashMap<>();
        for (Map.Entry<String, long[]> part : w.parts.entrySet()) {
            if (part.getValue()[0] != 0L) {
                sums.merge(group(part.getKey()), part.getValue()[0] / 1.0e6 / w.timed, Double::sum);
            }
        }
        // Parts under 5 microseconds a frame (a phase that only switched) are left out.
        sums.values().removeIf(ms -> ms < 0.005);
        List<Map.Entry<String, Double>> list = new ArrayList<>(sums.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    // ---- Numbers ----

    /** The median frame time and the 1% low (fps) of the last 10 seconds. */
    private static long lows(long now) {
        int n = Math.min(wallCount, WALL_RING);
        int in = 0;
        if (scratch.length < n) {
            scratch = new long[WALL_RING];
        }
        for (int i = 1; i <= n; i++) {
            int slot = (wallCount - i) & (WALL_RING - 1);
            if (now - wallEnds[slot] > LOWS_SPAN) {
                break;
            }
            scratch[in++] = wallTimes[slot];
        }
        if (in == 0) {
            return 0;
        }
        Arrays.sort(scratch, 0, in);
        median = scratch[in / 2];
        int slowest = Math.max(1, in / 100);
        long sum = 0;
        for (int i = in - slowest; i < in; i++) {
            sum += scratch[i];
        }
        return Math.round(slowest * 1.0e9 / sum);
    }

    private static String summary(Span w, long low) {
        double fps = w.wall > 0 ? w.frames * 1.0e9 / w.wall : 0;
        if (w.timed == 0) {
            return String.format(Locale.ROOT, "fps %.1f  1%% low %d  (no frame timed yet)", fps, low);
        }
        double busy = w.busyInterval > 0 ? Math.min(100.0, 100.0 * w.busySpan / w.busyInterval) : 0;
        return String.format(Locale.ROOT, "fps %.1f  1%% low %d  CPU %.2f ms  GPU %.2f ms  GPU busy %.0f%%", fps, low,
                w.cpu / 1.0e6 / w.timed, w.gpu / 1.0e6 / w.timed, busy);
    }

    private static void updateLines(long now) {
        long low = lows(now);
        List<String> out = new ArrayList<>();
        out.add("Cost meter: " + packName);
        out.add(summary(shown, low));
        if (untimed > 0) {
            out.add(untimed + " frames untimed (GPU over " + RING + " frames behind)");
        }
        List<Map.Entry<String, Double>> list = byGroup(shown);
        int room = MAX_LINES - out.size();
        double rest = 0;
        int more = 0;
        for (int i = 0; i < list.size(); i++) {
            if (i < room - 1 || list.size() == room) {
                out.add(String.format(Locale.ROOT, "%6.2f ms  %s", list.get(i).getValue(), list.get(i).getKey()));
            } else {
                rest += list.get(i).getValue();
                more++;
            }
        }
        if (more > 0) {
            out.add(String.format(Locale.ROOT, "%6.2f ms  %d more", rest, more));
        }
        lines = out;
    }

    // ---- The log ----

    private static void openLog() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            boolean big = Files.isRegularFile(LOG_FILE) && Files.size(LOG_FILE) > 32L << 20;
            log = Files.newBufferedWriter(LOG_FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    big ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND);
            Window window = Minecraft.getInstance().getWindow();
            write("# " + LocalDateTime.now().withNano(0) + " cost meter on, " + window.getWidth() + "x" + window.getHeight() + ", "
                    + GlUtil.getRenderer() + ", shader pack " + packName + groupsText());
        } catch (IOException e) {
            LOG.warn("Cost meter: can't write {}", LOG_FILE, e);
            log = null;
        }
    }

    private static void closeLog() {
        if (log != null) {
            write("# " + LocalDateTime.now().withNano(0) + " cost meter off");
            try {
                log.close();
            } catch (IOException ignored) {
            }
            log = null;
        }
    }

    private static void write(String line) {
        if (log == null) {
            return;
        }
        try {
            log.write(line);
            log.newLine();
        } catch (IOException e) {
            LOG.warn("Cost meter: can't write {}", LOG_FILE, e);
            log = null;
        }
    }

    private static void logSecond() {
        if (log == null || logged.frames == 0) {
            return;
        }
        StringBuilder line = new StringBuilder(LocalTime.now().format(CLOCK)).append(' ')
                .append(summary(logged, lows(System.nanoTime())));
        if (untimed > 0) {
            line.append("  untimed ").append(untimed);
        }
        for (Map.Entry<String, Double> group : byGroup(logged)) {
            line.append(" | ").append(group.getKey()).append(String.format(Locale.ROOT, " %.2f", group.getValue()));
        }
        write(line.toString());
        try {
            log.flush();
        } catch (IOException ignored) {
        }
    }

    private static void hitch(Frame f, long wall, long gpu) {
        if (log == null) {
            return;
        }
        List<Map.Entry<String, long[]>> parts = new ArrayList<>();
        for (Map.Entry<String, long[]> part : framePart.entrySet()) {
            if (part.getValue()[0] != 0L) {
                parts.add(part);
            }
        }
        parts.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        StringBuilder line = new StringBuilder(LocalTime.now().format(CLOCK)).append(String.format(Locale.ROOT,
                " HITCH %.1f ms (median %.1f) CPU %.2f ms GPU %.2f ms", wall / 1.0e6, median / 1.0e6, f.cpuNanos / 1.0e6, gpu / 1.0e6));
        for (int i = 0; i < Math.min(3, parts.size()); i++) {
            line.append(" | ").append(parts.get(i).getKey()).append(String.format(Locale.ROOT, " %.2f", parts.get(i).getValue()[0] / 1.0e6));
        }
        write(line.toString());
    }

    // ---- The overlay ----

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        List<String> text = lines;
        if (!on || text.isEmpty() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Font font = mc.font;
        Window window = mc.getWindow();
        // Whole screen pixels per font pixel, as the frame counter does.
        int pixels = Math.max(1, (int) Math.round(window.getHeight() / 720.0));
        float scale = (float) (pixels / window.getGuiScale());
        int x = 4;
        int y = 4;
        String fps = DisplaySettings.getString(DisplaySettings.FPS_COUNTER, FpsOverlay.OFF).toLowerCase(Locale.ROOT);
        if (!fps.equals(FpsOverlay.OFF)
                && DisplaySettings.getString(DisplaySettings.FPS_COUNTER_CORNER, FpsOverlay.TOP_RIGHT).equalsIgnoreCase(FpsOverlay.TOP_LEFT)) {
            y += fps.equals(FpsOverlay.DETAILED) ? 48 : 14; // under the frame counter
        }
        int width = 0;
        for (String line : text) {
            width = Math.max(width, font.width(line));
        }
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.fill(x - 2, y - 2, x + width + 2, y + text.size() * 10, 0x90000000);
        for (int i = 0; i < text.size(); i++) {
            graphics.drawString(font, text.get(i), x, y + i * 10, i == 0 ? 0xFFFFFF : i == 1 ? 0xFFDD55 : 0xCCCCCC, true);
        }
        graphics.pose().popPose();
    }
}
