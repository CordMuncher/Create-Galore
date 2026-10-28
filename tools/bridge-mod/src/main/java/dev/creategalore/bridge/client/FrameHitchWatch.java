package dev.creategalore.bridge.client;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * The game's stutter catcher, the short twin of {@link FrameFreezeWatch}. Profilers average over seconds, which hides a
 * single long frame. Here a background thread watches every frame: once one has run for 40 ms it starts looking at
 * what the render thread is doing (a stack every few ms) and how much CPU each thread of the game spends. If the frame
 * ends up taking 100 ms or more, logs/create-galore-stutters.log gets one entry: how long it took, a call tree of what
 * the render thread was in, whether the render thread was working or waiting (its CPU time against the clock), garbage collection during
 * the frame, the busiest other threads, and the machine's memory and CPU load at that moment. Frames outside a world
 * (loading) get one line each, and after 300 full entries so does every frame.
 * <p>
 * Stutter can also be many short hiccups in a row with no single frame near 100 ms. So in a world every frame that
 * runs well past the usual one (a spike) is watched the same way, and when spikes pile up (5 or more within a second,
 * or spikes eating a third of a second) a CHOPPY stretch starts. It ends after two calm seconds and gets one entry:
 * how long, frames per second against the usual, 1% low, the worst frame, and the same call tree, threads and memory
 * as a stutter, gathered over all spikes in the stretch. Once a minute a line of frames per second, 1% low and the
 * worst frame gives the baseline. The last session's file is kept as create-galore-stutters.prev.log.
 * <p>
 * Only the really bad ones also get a line in chat, so players know there is something to send: a single frame of 2 s
 * or more, or a choppy stretch of 5 s or more under 15 frames per second, from a minute after joining a world (loading
 * in stutters by nature), only while the game window is in front, and at most one such line every 10 minutes.
 * -Dcreate_galore_bridge.stutters=false turns it off; -Dcreate_galore_bridge.stutters.ms=N sets the length that counts
 * as a stutter.
 */
public final class FrameHitchWatch {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("logs");
    private static final Path FILE = DIR.resolve("create-galore-stutters.log");
    private static final long HITCH_NANOS = Long.getLong("create_galore_bridge.stutters.ms", 100L) * 1_000_000L;
    private static final long LOOK_NANOS = Math.min(40_000_000L, HITCH_NANOS / 2);
    private static final long SAMPLE_NANOS = 4_000_000L;
    private static final int STACK_DEPTH = 160;
    private static final double TREE_SHARE = 0.1;  // calls seen in fewer looks than this share are left out of the tree
    private static final int TREE_LINES = 80;
    private static final int MAX_FULL_ENTRIES = 300;
    private static final long SPIKE_MIN_NANOS = 25_000_000L;  // a spike runs this long and 2.5x the usual frame
    private static final double SPIKE_FACTOR = 2.5;
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int CHOPPY_SPIKES = 5;               // spikes within a second that make it choppy
    private static final double CHOPPY_SHARE = 0.33;          // or this share of the second spent in spikes
    private static final long CALM_NANOS = 2_000_000_000L;    // a choppy stretch ends after this long without
    private static final long SUMMARY_NANOS = 60_000_000_000L;
    private static final long LONGEST_STRETCH_NANOS = 30_000_000_000L;  // longer ones are written in parts
    private static final int NORMAL_FRAMES = 301;             // a normal frame = the median of the last this many in a world
    private static final int NORMAL_MIN = 60;                 // spikes count once a world has drawn this many
    private static final long CHAT_STALL_NANOS = 2_000_000_000L;   // a single frame this long goes to chat
    private static final long CHAT_CHOPPY_NANOS = 5_000_000_000L;  // and so does a choppy stretch this long
    private static final double CHAT_CHOPPY_FPS = 15;               // under this many frames per second
    private static final long CHAT_GRACE_NANOS = 60_000_000_000L;  // nothing in chat this soon after joining a world
    private static final long CHAT_GAP_NANOS = 600_000_000_000L;   // at most one chat line this often

    private static volatile long lastFrame;          // nanoTime at the end of the last drawn frame, 0 before the first
    private static volatile long frames;
    private static volatile Thread renderThread;
    private static volatile String header;           // filled in by the render thread on its first frame
    private static volatile int lastCore = -1;       // CpuProbe.onFastCore() at the end of the last frame

    private FrameHitchWatch() {
    }

    public static void init() {
        if (!Boolean.parseBoolean(System.getProperty("create_galore_bridge.stutters", "true"))) return;
        CpuProbe.init();
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            lastCore = CpuProbe.onFastCore();
            if (renderThread == null) {
                renderThread = Thread.currentThread();
                header = header();
            }
            lastFrame = System.nanoTime();
            frames++;
        });
        Thread t = new Thread(FrameHitchWatch::run, "Create Galore stutter catcher");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private static void run() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        boolean cpuTimes = threads.isThreadCpuTimeSupported();
        if (cpuTimes && !threads.isThreadCpuTimeEnabled()) threads.setThreadCpuTimeEnabled(true);
        long seenFrame = -1;
        long frameStart = 0;                         // end of the frame before the one being watched
        long[] gcAtStart = gcTotals();
        double usual = 0;                            // smoothed length of a normal frame, ns
        Hitch hitch = null;
        int entries = 0;
        boolean headerWritten = false;
        long nextSample = 0;
        ArrayDeque<long[]> recent = new ArrayDeque<>();   // {end, length, spike 0/1} of the frames in the last second
        Stretch stretch = null;
        List<Long> minute = new ArrayList<>();
        long minuteStart = 0;
        int[] minuteCores = new int[2];                  // frames that ended on an efficiency core / on any known core
        long[] minuteMemory = null;
        long[] normalRing = new long[NORMAL_FRAMES];
        int normalCount = 0;
        double normal = 0;                           // median in-world frame, ns; 0 until NORMAL_MIN frames
        long worldSince = 0;                         // nanoTime of the first frame in the current world, 0 outside one
        long lastChat = 0;
        while (true) {
            LockSupport.parkNanos(2_000_000L);
            long end = lastFrame;
            if (end == 0) continue;                  // still starting up
            if (!headerWritten && header != null) {
                startFile(header);
                headerWritten = true;
            }
            long frame = frames;
            if (frame != seenFrame) {
                boolean inWorld = Minecraft.getInstance().level != null;
                if (seenFrame >= 0) {
                    long length = end - frameStart;
                    int core = lastCore;
                    if (inWorld && worldSince == 0) worldSince = end;
                    // A hidden or minimised window can be held back on purpose, so chat only speaks up while the game is in front.
                    boolean chatOk = inWorld && Minecraft.getInstance().isWindowActive() && end - worldSince >= CHAT_GRACE_NANOS && (lastChat == 0 || end - lastChat >= CHAT_GAP_NANOS);
                    if (inWorld) {
                        normalRing[normalCount++ % NORMAL_FRAMES] = length;
                        if (normalCount >= NORMAL_MIN) normal = median(normalRing, Math.min(normalCount, NORMAL_FRAMES));
                    }
                    boolean spike = inWorld && normal > 0 && length >= spike(normal);
                    if (hitch != null && length >= HITCH_NANOS) {
                        // Loading a world stutters by nature: those frames get one line and don't use up the full entries.
                        if (inWorld) entries++;
                        write(hitch.report(length, usual, gcAtStart, threads, inWorld && entries <= MAX_FULL_ENTRIES, core));
                        if (entries == MAX_FULL_ENTRIES) write("  (from here on: one line per stutter)\n\n");
                        if (chatOk && length >= CHAT_STALL_NANOS) {
                            tell(Component.translatable("create_galore_bridge.stutters.stall", String.format(Locale.ROOT, "%.1f", length / 1e9)));
                            lastChat = end;
                            chatOk = false;
                        }
                    }
                    if (!inWorld) {
                        if (stretch != null) write(stretch.report(end, normal, threads, entries < MAX_FULL_ENTRIES));
                        stretch = null;
                        worldSince = 0;
                        normalCount = 0;
                        normal = 0;
                        recent.clear();
                        minute.clear();
                        minuteStart = 0;
                        minuteCores[0] = minuteCores[1] = 0;
                    } else {
                        recent.addLast(new long[] {end, length, spike ? 1 : 0});
                        while (!recent.isEmpty() && end - recent.peekFirst()[0] > WINDOW_NANOS) recent.removeFirst();
                        if (stretch != null) {
                            stretch.frame(end, length, spike, core);
                            if (spike && hitch != null) stretch.calls.merge(hitch.calls);
                            if (end - stretch.lastSpike > CALM_NANOS || end - stretch.start > LONGEST_STRETCH_NANOS) {
                                if (entries < MAX_FULL_ENTRIES) entries++;
                                write(stretch.report(end, normal, threads, entries < MAX_FULL_ENTRIES));
                                double[] bad = stretch.spanAndFps();
                                if (chatOk && bad[0] >= CHAT_CHOPPY_NANOS && bad[1] < CHAT_CHOPPY_FPS) {
                                    tell(Component.translatable("create_galore_bridge.stutters.choppy",
                                            String.format(Locale.ROOT, "%.0f", bad[0] / 1e9), String.format(Locale.ROOT, "%.0f", bad[1])));
                                    lastChat = end;
                                }
                                stretch = null;
                            }
                        } else if (choppy(recent)) {
                            stretch = new Stretch(recent, threads, cpuTimes);
                            if (spike && hitch != null) stretch.calls.merge(hitch.calls);
                        }
                        if (minuteStart == 0) {
                            minuteStart = end;
                            minuteMemory = CpuProbe.memory();
                        }
                        minute.add(length);
                        if (core >= 0) {
                            minuteCores[1]++;
                            if (core == 0) minuteCores[0]++;
                        }
                        if (end - minuteStart >= SUMMARY_NANOS) {
                            write(summary(minute, end - minuteStart, minuteCores, minuteMemory));
                            minuteMemory = CpuProbe.memory();
                            minuteCores[0] = minuteCores[1] = 0;
                            minute.clear();
                            minuteStart = end;
                        }
                    }
                    if (hitch == null && stretch == null && frame == seenFrame + 1 && length < HITCH_NANOS && !spike) {
                        usual = usual == 0 ? length : usual * 0.95 + length * 0.05;
                    }
                }
                seenFrame = frame;
                frameStart = end;
                gcAtStart = gcTotals();
                hitch = null;
                continue;
            }
            long now = System.nanoTime();
            // Spikes are watched from 60% of their length, long frames from LOOK_NANOS, whichever comes first.
            long look = normal > 0 ? Math.min(LOOK_NANOS, (long) (spike(normal) * 0.6)) : LOOK_NANOS;
            if (now - frameStart < look) continue;
            Thread render = renderThread;
            if (render == null) continue;
            if (hitch == null) {
                hitch = new Hitch(render.threadId(), now, threads, cpuTimes);
                nextSample = now;
            }
            if (now >= nextSample) {
                hitch.calls.sample(threads, hitch.renderId);
                nextSample = now + SAMPLE_NANOS;
            }
        }
    }

    /** A grey line in chat, only on this player's screen. */
    private static void tell(Component text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.gui.getChat().addMessage(text.copy().withStyle(ChatFormatting.GRAY));
        });
    }

    private static long spike(double usual) {
        return Math.max(SPIKE_MIN_NANOS, (long) (usual * SPIKE_FACTOR));
    }

    private static double median(long[] ring, int n) {
        long[] copy = java.util.Arrays.copyOf(ring, n);
        java.util.Arrays.sort(copy);
        return copy[n / 2];
    }

    private static boolean choppy(ArrayDeque<long[]> recent) {
        int spikes = 0;
        long spikeTime = 0;
        for (long[] f : recent) {
            if (f[2] == 1) {
                spikes++;
                spikeTime += f[1];
            }
        }
        return spikes >= CHOPPY_SPIKES || (spikes >= 2 && spikeTime >= WINDOW_NANOS * CHOPPY_SHARE);
    }

    /** Frames per second, 1% low and worst frame over a stretch of frame lengths. */
    private static String rates(List<Long> lengths, long span) {
        if (lengths.isEmpty() || span <= 0) return "no frames";
        List<Long> sorted = new ArrayList<>(lengths);
        Collections.sort(sorted);
        // 1% low = frames per second over the slowest 1% of frames
        int n = Math.max(1, sorted.size() / 100);
        long slow = 0;
        for (int i = sorted.size() - n; i < sorted.size(); i++) slow += sorted.get(i);
        return String.format(Locale.ROOT, "%.0f fps, 1%% low %.0f fps, worst frame %.0f ms",
                lengths.size() / (span / 1e9), n / (slow / 1e9), sorted.get(sorted.size() - 1) / 1e6);
    }

    private static String summary(List<Long> lengths, long span, int[] cores, long[] memoryAtStart) {
        StringBuilder out = new StringBuilder(now()).append("  [last ").append(Math.round(span / 1e9)).append(" s] ").append(rates(lengths, span));
        if (cores[1] > 0) out.append(String.format(Locale.ROOT, ", render thread on efficiency cores %.0f%% of frames", 100.0 * cores[0] / cores[1]));
        String clocks = CpuProbe.clocks();
        if (!clocks.isEmpty()) out.append("; CPU ").append(clocks);
        long[] memory = CpuProbe.memory();
        if (memoryAtStart != null && memory != null) {
            out.append(String.format(Locale.ROOT, "; %.0f page faults per second, game working set %d MB",
                    (memory[0] - memoryAtStart[0]) / (span / 1e9), memory[1] >> 20));
        }
        return out.append('\n').toString();
    }

    /** What one long frame looked like from the outside, from the moment it ran past the look threshold. */
    private static final class Hitch {
        final long renderId;
        private final long watchedFrom;
        private final boolean cpuTimes;
        private final Map<Long, Long> cpuAtStart;
        private final long[] memoryAtStart = CpuProbe.memory();
        final Calls calls = new Calls();

        Hitch(long renderId, long now, ThreadMXBean threads, boolean cpuTimes) {
            this.renderId = renderId;
            this.watchedFrom = now;
            this.cpuTimes = cpuTimes;
            this.cpuAtStart = cpuTimes ? cpuTimes(threads) : Map.of();
        }

        String report(long length, double usual, long[] gcAtStart, ThreadMXBean threads, boolean full, int core) {
            long watched = System.nanoTime() - watchedFrom;
            StringBuilder out = new StringBuilder();
            out.append(now()).append(String.format(Locale.ROOT, "  STUTTER %d ms (usual frame %.1f ms)", length / 1_000_000L, usual / 1e6));
            appendWhere(out);
            out.append('\n');
            appendGc(out, gcAtStart, "the frame");
            if (cpuTimes) appendRenderCpu(out, cpuDelta(threads, cpuAtStart, renderId), watched, "the last " + watched / 1_000_000L + " ms of the frame");
            appendCpu(out, core < 0 ? "" : "render thread ended the frame on " + (core == 1 ? "a fast core" : "an EFFICIENCY core"));
            appendFaults(out, memoryAtStart, watched, "the last " + watched / 1_000_000L + " ms of the frame");
            appendMachine(out);
            if (!full) return out.append('\n').toString();
            if (cpuTimes) appendBusiest(out, threads, cpuAtStart, renderId, watched);
            calls.print(out);
            return out.append('\n').toString();
        }
    }

    /**
     * A run of choppy frames: many spikes close together, none of which has to be a stutter on its own. Collects the
     * render thread's calls during every spike in it, and thread CPU, garbage collection and frame lengths from the
     * moment it was noticed.
     */
    private static final class Stretch {
        final long start;
        private final long renderId;
        private final boolean cpuTimes;
        private final long noticed = System.nanoTime();
        private final Map<Long, Long> cpuAtStart;
        private final long[] gcAtStart = gcTotals();
        private final long[] memoryAtStart = CpuProbe.memory();
        private final List<Long> lengths = new ArrayList<>();
        private int spikes;
        private int slowFrames, knownFrames, slowSpikes, knownSpikes;
        long lastSpike;
        final Calls calls = new Calls();

        Stretch(ArrayDeque<long[]> recent, ThreadMXBean threads, boolean cpuTimes) {
            Thread render = renderThread;
            this.renderId = render == null ? -1 : render.threadId();
            this.cpuTimes = cpuTimes && render != null;
            this.cpuAtStart = this.cpuTimes ? cpuTimes(threads) : Map.of();
            long first = recent.peekFirst()[0] - recent.peekFirst()[1];
            this.start = first;
            for (long[] f : recent) frame(f[0], f[1], f[2] == 1, -1);
        }

        void frame(long end, long length, boolean spike, int core) {
            lengths.add(length);
            if (core >= 0) {
                knownFrames++;
                if (core == 0) slowFrames++;
                if (spike) {
                    knownSpikes++;
                    if (core == 0) slowSpikes++;
                }
            }
            if (spike) {
                spikes++;
                lastSpike = end;
            }
        }

        /** Frames up to the last spike: the calm tail after it isn't part of the stretch. */
        private List<Long> inside() {
            List<Long> inside = new ArrayList<>();
            long t = start;
            for (long l : lengths) {
                t += l;
                if (t > lastSpike + 1_000_000L) break;
                inside.add(l);
            }
            return inside;
        }

        /** How long the stretch lasted (ns) and its frames per second. */
        double[] spanAndFps() {
            long span = lastSpike - start;
            return new double[] {span, span > 0 ? inside().size() / (span / 1e9) : 0};
        }

        String report(long end, double usual, ThreadMXBean threads, boolean full) {
            long span = lastSpike - start;
            List<Long> inside = inside();
            long watched = System.nanoTime() - noticed;
            StringBuilder out = new StringBuilder();
            out.append(now()).append(String.format(Locale.ROOT, "  CHOPPY for %.1f s: %d spikes over %.0f ms in %d frames (normal frame %.1f ms = %.0f fps)",
                    span / 1e9, spikes, spike(usual) / 1e6, inside.size(), usual / 1e6, usual > 0 ? 1e9 / usual : 0));
            appendWhere(out);
            out.append('\n');
            out.append("  During it: ").append(rates(inside, span)).append('\n');
            appendGc(out, gcAtStart, "it (since noticed)");
            if (cpuTimes) appendRenderCpu(out, cpuDelta(threads, cpuAtStart, renderId), watched, "the " + watched / 1_000_000L + " ms since it was noticed");
            appendCpu(out, knownFrames == 0 ? "" : String.format(Locale.ROOT,
                    "render thread ended %d of %d frames on an efficiency core (%d of %d spikes)", slowFrames, knownFrames, slowSpikes, knownSpikes));
            appendFaults(out, memoryAtStart, watched, "the " + watched / 1_000_000L + " ms since it was noticed");
            appendMachine(out);
            if (!full) return out.append('\n').toString();
            if (cpuTimes) appendBusiest(out, threads, cpuAtStart, renderId, watched);
            if (calls.samples > 0) {
                out.append("  (calls below are from the spikes only)\n");
                calls.print(out);
            }
            return out.append('\n').toString();
        }
    }

    /** Stacks of the render thread taken every few ms, as a call tree, plus the thread states seen. */
    private static final class Calls {
        final Node root = new Node(null);
        final Map<String, Integer> states = new LinkedHashMap<>();
        int samples;

        void sample(ThreadMXBean threads, long renderId) {
            ThreadInfo info = threads.getThreadInfo(renderId, STACK_DEPTH);
            if (info == null) return;
            samples++;
            StackTraceElement[] stack = info.getStackTrace();
            Node node = root;
            root.count++;
            for (int i = stack.length - 1; i >= 0; i--) {
                node = node.children.computeIfAbsent(stack[i], Node::new);
                node.count++;
            }
            String state = info.getThreadState().toString() + (info.isInNative() ? " (in native code)" : "")
                    + (info.getLockName() != null ? " waiting for " + info.getLockName() : "");
            states.merge(state, 1, Integer::sum);
        }

        void merge(Calls other) {
            samples += other.samples;
            other.states.forEach((s, n) -> states.merge(s, n, Integer::sum));
            mergeNode(root, other.root);
        }

        private static void mergeNode(Node into, Node from) {
            into.count += from.count;
            for (Node child : from.children.values()) {
                mergeNode(into.children.computeIfAbsent(child.frame, Node::new), child);
            }
        }

        void print(StringBuilder out) {
            out.append("  Render thread states:");
            states.forEach((s, n) -> out.append(' ').append(n).append("x ").append(s).append(';'));
            out.append('\n');
            out.append("  Render thread calls (").append(samples).append(" looks; share of looks, callers above callees):\n");
            // Calls every look went through (the game loop) are skipped down to the last three before the first split.
            Node start = root;
            List<Node> trunk = new ArrayList<>();
            while (start.children.size() == 1) {
                start = start.children.values().iterator().next();
                trunk.add(start);
            }
            int[] lines = {0};
            for (int i = Math.max(0, trunk.size() - 3); i < trunk.size(); i++) {
                line(out, trunk.get(i), 0, lines);
            }
            List<Node> kids = new ArrayList<>(start.children.values());
            kids.sort((a, b) -> b.count - a.count);
            for (Node k : kids) tree(out, k, 1, lines);
        }

        private void tree(StringBuilder out, Node node, int depth, int[] lines) {
            if (node.count < Math.max(2, samples * TREE_SHARE) || lines[0] >= TREE_LINES) return;
            line(out, node, depth, lines);
            // A chain of calls with the same count is one path: printed without more indent.
            Node n = node;
            while (n.children.size() == 1 && n.children.values().iterator().next().count == n.count) {
                n = n.children.values().iterator().next();
                if (lines[0] >= TREE_LINES) return;
                line(out, n, depth, lines);
            }
            List<Node> kids = new ArrayList<>(n.children.values());
            kids.sort((a, b) -> b.count - a.count);
            for (Node k : kids) tree(out, k, depth + 1, lines);
        }

        private void line(StringBuilder out, Node node, int depth, int[] lines) {
            lines[0]++;
            out.append(String.format(Locale.ROOT, "    %3.0f%% ", 100.0 * node.count / samples)).append("  ".repeat(depth))
               .append(node.frame).append('\n');
        }
    }

    private static Map<Long, Long> cpuTimes(ThreadMXBean threads) {
        Map<Long, Long> out = new HashMap<>();
        for (long id : threads.getAllThreadIds()) {
            long cpu = threads.getThreadCpuTime(id);
            if (cpu >= 0) out.put(id, cpu);
        }
        return out;
    }

    private static long cpuDelta(ThreadMXBean threads, Map<Long, Long> cpuAtStart, long id) {
        Long before = cpuAtStart.get(id);
        long after = threads.getThreadCpuTime(id);
        return before == null || after < 0 ? 0 : after - before;
    }

    private static void appendRenderCpu(StringBuilder out, long renderCpu, long watched, String when) {
        out.append(String.format(Locale.ROOT, "  Render thread used %d ms of CPU in %s (%s)%n", renderCpu / 1_000_000L, when,
                renderCpu > watched * 0.7 ? "working" : renderCpu < watched * 0.3 ? "mostly waiting" : "partly waiting"));
    }

    private static void appendBusiest(StringBuilder out, ThreadMXBean threads, Map<Long, Long> cpuAtStart, long renderId, long watched) {
        List<long[]> busy = new ArrayList<>();
        for (long id : threads.getAllThreadIds()) {
            if (id == renderId) continue;
            long d = cpuDelta(threads, cpuAtStart, id);
            if (d > 0) busy.add(new long[] {id, d});
        }
        busy.sort((a, b) -> Long.compare(b[1], a[1]));
        out.append("  Busiest other threads (CPU ms in the same ").append(watched / 1_000_000L).append(" ms):");
        for (int i = 0; i < busy.size() && i < 8; i++) {
            ThreadInfo info = threads.getThreadInfo(busy.get(i)[0]);
            String name = info == null ? "#" + busy.get(i)[0] : info.getThreadName();
            out.append(" \"").append(name).append("\" ").append(busy.get(i)[1] / 1_000_000L).append(';');
        }
        out.append('\n');
    }

    /** One call in the render thread's call tree, with how many looks went through it. */
    private static final class Node {
        final StackTraceElement frame;
        final Map<StackTraceElement, Node> children = new LinkedHashMap<>();
        int count;

        Node(StackTraceElement frame) {
            this.frame = frame;
        }
    }

    private static void appendWhere(StringBuilder out) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            out.append(", ").append(mc.level == null ? "no world" : mc.level.dimension().location().toString())
               .append(", screen ").append(screen == null ? "none" : screen.getClass().getSimpleName());
        } catch (Throwable ignored) {
            // the entry still counts without it
        }
    }

    private static void appendGc(StringBuilder out, long[] gcAtStart, String during) {
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        StringBuilder gc = new StringBuilder();
        for (int i = 0; i < beans.size(); i++) {
            GarbageCollectorMXBean b = beans.get(i);
            long count = b.getCollectionCount() - gcAtStart[i * 2];
            long ms = b.getCollectionTime() - gcAtStart[i * 2 + 1];
            if (count > 0) gc.append(String.format(Locale.ROOT, " %s %dx %d ms;", b.getName(), count, ms));
        }
        out.append("  Garbage collection during ").append(during).append(':').append(gc.length() == 0 ? " none" : gc).append('\n');
    }

    private static void appendFaults(StringBuilder out, long[] before, long nanos, String when) {
        long[] after = CpuProbe.memory();
        if (before == null || after == null || nanos <= 0) return;
        long faults = after[0] - before[0];
        out.append(String.format(Locale.ROOT, "  Memory: %d page faults in %s (%.0f per second), game working set %d MB%n",
                faults, when, faults / (nanos / 1e9), after[1] >> 20));
    }

    private static void appendCpu(StringBuilder out, String core) {
        String clocks = CpuProbe.clocks();
        if (core.isEmpty() && clocks.isEmpty()) return;
        out.append("  CPU: ").append(core);
        if (!clocks.isEmpty()) out.append(core.isEmpty() ? "" : "; ").append(clocks);
        out.append('\n');
    }

    private static void appendMachine(StringBuilder out) {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                out.append(String.format(Locale.ROOT,
                        "  Machine: game CPU %.0f%%, whole CPU %.0f%%, free RAM %d MB of %d MB, game committed %d MB%n",
                        os.getProcessCpuLoad() * 100, os.getCpuLoad() * 100, os.getFreeMemorySize() >> 20,
                        os.getTotalMemorySize() >> 20, os.getCommittedVirtualMemorySize() >> 20));
            }
        } catch (Throwable ignored) {
            // not every Java has these numbers
        }
    }

    private static long[] gcTotals() {
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        long[] out = new long[beans.size() * 2];
        for (int i = 0; i < beans.size(); i++) {
            out[i * 2] = beans.get(i).getCollectionCount();
            out[i * 2 + 1] = beans.get(i).getCollectionTime();
        }
        return out;
    }

    private static String header() {
        Runtime rt = Runtime.getRuntime();
        StringBuilder out = new StringBuilder();
        out.append(now()).append("  Stutter catcher started: frames of ").append(HITCH_NANOS / 1_000_000L)
           .append(" ms or more are listed, and choppy stretches of shorter spikes\n");
        try {
            out.append("  GPU: ").append(GlUtil.getRenderer()).append(", OpenGL ").append(GlUtil.getOpenGLVersion()).append('\n');
        } catch (Throwable ignored) {
            // the rest of the header still helps
        }
        out.append("  CPU: ").append(CpuProbe.cores());
        String clocks = CpuProbe.clocks();
        if (!clocks.isEmpty()) out.append("; ").append(clocks);
        out.append('\n');
        out.append("  Java ").append(System.getProperty("java.version")).append(", heap up to ").append(rt.maxMemory() >> 20)
           .append(" MB, ").append(rt.availableProcessors()).append(" CPU threads, ").append(System.getProperty("os.name")).append('\n');
        out.append("  JVM flags: ").append(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()
                .stream().filter(a -> a.startsWith("-X")).toList())).append('\n');
        Path display = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-display.properties");
        try {
            if (Files.exists(display)) {
                out.append("  Display settings:");
                for (String line : Files.readAllLines(display, StandardCharsets.UTF_8)) {
                    String l = line.trim();
                    if (!l.isEmpty() && !l.startsWith("#")) out.append(' ').append(l).append(';');
                }
                out.append('\n');
            }
        } catch (IOException ignored) {
            // the rest of the header still helps
        }
        return out.append('\n').toString();
    }

    private static void startFile(String header) {
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                Files.move(FILE, DIR.resolve("create-galore-stutters.prev.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(FILE, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Could not start {}: {}", FILE, e.toString());
        }
    }

    private static void write(String text) {
        try {
            Files.writeString(FILE, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", FILE, e.toString());
        }
    }

    private static String now() {
        return LocalDateTime.now().format(STAMP);
    }
}
