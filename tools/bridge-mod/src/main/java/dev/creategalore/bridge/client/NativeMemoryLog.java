package dev.creategalore.bridge.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Where the game's memory outside the Java heap goes, once a minute, in logs/create-galore-native-memory.log: the
 * game's committed memory against the heap and Java's own non-heap memory, so growth shows up over a session. Mods
 * allocate native memory through LWJGL; with -Dorg.lwjgl.util.DebugAllocator=true LWJGL keeps track of every such
 * allocation, and each entry also lists the methods holding the most of it. That switch costs speed, so it is only for
 * hunting. Off with -Dcreate_galore_bridge.stutters=false, like {@link FrameHitchWatch}.
 */
public final class NativeMemoryLog {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("logs").resolve("create-galore-native-memory.log");
    private static final long PERIOD_MS = 60_000L;
    private static final int TOP = 25;

    private NativeMemoryLog() {
    }

    public static void init() {
        if (!Boolean.parseBoolean(System.getProperty("create_galore_bridge.stutters", "true"))) return;
        Thread t = new Thread(NativeMemoryLog::run, "Create Galore native memory log");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, String.format(Locale.ROOT, "%s  Native memory log started; LWJGL allocation tracking %s%n%n", now(),
                    tracking() ? "ON" : "off (add -Dorg.lwjgl.util.DebugAllocator=true to list the holders)"),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.warn("Could not start {}: {}", FILE, e.toString());
            return;
        }
        while (true) {
            try {
                Thread.sleep(PERIOD_MS);
            } catch (InterruptedException e) {
                return;
            }
            try {
                write(entry());
            } catch (Throwable e) {
                write(now() + "  could not measure: " + e + "\n\n");
            }
        }
    }

    private static boolean tracking() {
        return Boolean.TRUE.equals(Configuration.DEBUG_MEMORY_ALLOCATOR.get());
    }

    private static String entry() {
        StringBuilder out = new StringBuilder(now());
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heap = memory.getHeapMemoryUsage().getCommitted();
        long nonHeap = memory.getNonHeapMemoryUsage().getCommitted();
        long committed = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            committed = os.getCommittedVirtualMemorySize();
        }
        out.append(String.format(Locale.ROOT, "  game committed %d MB = heap %d MB + Java non-heap %d MB + other %d MB%n",
                committed >> 20, heap >> 20, nonHeap >> 20, (committed - heap - nonHeap) >> 20));
        if (tracking()) {
            Map<String, long[]> byMethod = new HashMap<>();
            MemoryUtil.memReport((address, size, threadId, threadName, stack) -> {
                String where = stack.length == 0 ? "(unknown)" : stack[0].toString();
                long[] sum = byMethod.computeIfAbsent(where, k -> new long[2]);
                sum[0] += size;
                sum[1]++;
            }, MemoryUtil.MemoryAllocationReport.Aggregate.GROUP_BY_METHOD, false);
            List<Map.Entry<String, long[]>> sorted = new ArrayList<>(byMethod.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            long total = 0;
            for (Map.Entry<String, long[]> e : sorted) total += e.getValue()[0];
            out.append(String.format(Locale.ROOT, "  LWJGL allocations held: %d MB in %d places; the largest:%n", total >> 20, sorted.size()));
            for (int i = 0; i < sorted.size() && i < TOP; i++) {
                out.append(String.format(Locale.ROOT, "    %8.1f MB  %s%n", sorted.get(i).getValue()[0] / 1048576.0, sorted.get(i).getKey()));
            }
        }
        return out.append('\n').toString();
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
