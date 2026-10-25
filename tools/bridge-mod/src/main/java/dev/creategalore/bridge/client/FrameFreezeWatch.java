package dev.creategalore.bridge.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * The game's own freeze catcher, the client twin of Lag Watch's. When the game window stops drawing for 10 s (a
 * freeze that ends with the game killed from the task manager leaves nothing in latest.log), a background thread
 * writes what every thread of the game is doing to logs/create-galore-client-freeze.log, again at 20, 40 and 80 s,
 * and notes when the game draws again. It names the mod that hung the game, and any two threads waiting on each other.
 */
public final class FrameFreezeWatch {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("logs").resolve("create-galore-client-freeze.log");
    private static final long[] LOOKS_SECONDS = {10, 20, 40, 80};
    private static final int MAX_FRAMES = 120;

    private static volatile long lastFrame;          // nanoTime of the last drawn frame, 0 before the first
    private static volatile Thread renderThread;

    private FrameFreezeWatch() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> {
            if (renderThread == null) renderThread = Thread.currentThread();
            lastFrame = System.nanoTime();
        });
        Thread t = new Thread(FrameFreezeWatch::run, "Create Galore freeze catcher");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        long reported = 0;                           // lastFrame of the freeze being reported, 0 when none
        int looks = 0;
        while (true) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
            long frame = lastFrame;
            if (frame == 0) continue;                // still starting up: the loading screen has its own pace
            if (reported != 0 && frame != reported) {
                write(String.format(Locale.ROOT, "%s  FREEZE OVER after %.1f s%n%n", now(), (frame - reported) / 1e9));
                reported = 0;
            }
            long stuck = (System.nanoTime() - frame) / 1_000_000_000L;
            if (reported == 0) looks = 0;
            if (looks < LOOKS_SECONDS.length && stuck >= LOOKS_SECONDS[looks]) {
                reported = frame;
                looks++;
                write(dump(stuck, looks));
                if (looks == 1) LOG.warn("The game stopped drawing for {} s; what it is stuck on is in {}", stuck, FILE);
            }
        }
    }

    private static String dump(long stuck, int look) {
        StringBuilder out = new StringBuilder();
        out.append(now()).append(String.format(Locale.ROOT, "  FREEZE: the game has not drawn a frame for %d s (look %d)%n", stuck, look));
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            LocalPlayer player = mc.player;
            Screen screen = mc.screen;
            out.append("  World: ").append(level == null ? "none" : level.dimension().location())
               .append(", player at ").append(player == null ? "-" : player.blockPosition().toShortString())
               .append(", screen ").append(screen == null ? "none" : screen.getClass().getName())
               .append(", server ").append(mc.getCurrentServer() == null ? (mc.hasSingleplayerServer() ? "single player" : "-") : "multiplayer")
               .append(System.lineSeparator());
        } catch (Throwable e) {
            out.append("  World: unknown (").append(e).append(")").append(System.lineSeparator());
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threads.isSynchronizerUsageSupported() ? threads.findDeadlockedThreads() : threads.findMonitorDeadlockedThreads();
        if (deadlocked != null) {
            out.append("  DEADLOCK between these threads:");
            for (ThreadInfo info : threads.getThreadInfo(deadlocked)) if (info != null) out.append(" \"").append(info.getThreadName()).append('"');
            out.append(System.lineSeparator());
        }
        ThreadInfo[] all = threads.dumpAllThreads(threads.isObjectMonitorUsageSupported(), threads.isSynchronizerUsageSupported());
        Thread render = renderThread;
        // The thread that draws first, then the rest.
        for (ThreadInfo info : all) if (render != null && info.getThreadId() == render.threadId()) append(out, info);
        for (ThreadInfo info : all) if (render == null || info.getThreadId() != render.threadId()) append(out, info);
        return out.append(System.lineSeparator()).toString();
    }

    private static void append(StringBuilder out, ThreadInfo info) {
        out.append("  \"").append(info.getThreadName()).append("\" ").append(info.getThreadState());
        if (info.getLockName() != null) out.append(" waiting for ").append(info.getLockName());
        if (info.getLockOwnerName() != null) out.append(" held by \"").append(info.getLockOwnerName()).append('"');
        out.append(System.lineSeparator());
        StackTraceElement[] stack = info.getStackTrace();
        MonitorInfo[] monitors = info.getLockedMonitors();
        for (int i = 0; i < stack.length && i < MAX_FRAMES; i++) {
            out.append("      at ").append(stack[i]).append(System.lineSeparator());
            for (MonitorInfo m : monitors) {
                if (m.getLockedStackDepth() == i) out.append("        holds ").append(m).append(System.lineSeparator());
            }
        }
        if (stack.length > MAX_FRAMES) out.append("      ... ").append(stack.length - MAX_FRAMES).append(" more").append(System.lineSeparator());
        for (LockInfo l : info.getLockedSynchronizers()) out.append("      holds ").append(l).append(System.lineSeparator());
    }

    private static void write(String text) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", FILE, e.toString());
        }
    }

    private static String now() {
        return LocalDateTime.now().format(STAMP);
    }
}
