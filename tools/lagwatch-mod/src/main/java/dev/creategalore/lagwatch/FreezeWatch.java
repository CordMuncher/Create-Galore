package dev.creategalore.lagwatch;

import com.mojang.logging.LogUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Catches single long freezes, which the TPS alarm lets through on purpose: one 8 s stall drags the
 * 5 s TPS window down for only a few seconds. A background thread notices when the server thread has
 * not finished a tick for a while and writes what that thread is doing to logs/lagwatch.log, so the
 * mod that froze the server is named in the file instead of guessed at afterwards.
 */
final class FreezeWatch {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long POLL_MILLIS = 250;
    private static final String[] VANILLA = {"java.", "jdk.", "sun.", "net.minecraft.", "com.mojang.", "net.neoforged.",
            "cpw.mods.", "org.spongepowered.", "it.unimi.", "com.google.", "io.netty.", "org.slf4j.", "org.apache."};

    private volatile MinecraftServer server;
    private volatile long lastTickEnd;                // nanoTime of the last finished tick, 0 before the first
    private volatile boolean stopping;

    // Guarded by lock: set by the watcher thread, read and cleared on the server thread once the freeze is over.
    private final Object lock = new Object();
    private long freezeFrom = -1;                     // lastTickEnd of the freeze being reported, -1 when none
    private int dumps;
    private StackTraceElement[] lastStack;

    void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        stopping = false;
        Thread t = new Thread(this::run, "Lag Watch freeze catcher");
        t.setDaemon(true);
        t.start();
    }

    void onServerStopping(ServerStoppingEvent event) {
        stopping = true;                              // saving the world on the way out is not a freeze
    }

    void onTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        long from;
        synchronized (lock) {                         // uncontended except during a freeze, so it costs nothing
            lastTickEnd = now;
            from = freezeFrom;
            freezeFrom = -1;
        }
        if (from >= 0) {
            double seconds = (now - from) / 1e9;
            String line = String.format(Locale.ROOT, "The server froze for %.1f s. What it was stuck on is in logs/lagwatch.log.", seconds);
            Watcher.tell(event.getServer(), Component.literal(line).withStyle(ChatFormatting.YELLOW));
            Watcher.logFile(String.format(Locale.ROOT, "%s  FREEZE OVER after %.1f s", LocalDateTime.now().format(STAMP), seconds));
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            MinecraftServer s = server;
            if (s == null || !s.isRunning()) return;
            if (stopping || !LagWatch.ENABLED.get() || !LagWatch.FREEZE.get()) continue;
            long every = LagWatch.FREEZE_SECONDS.get() * 1_000_000_000L;
            long stuck;
            int look;
            synchronized (lock) {
                long last = lastTickEnd;
                if (last == 0) continue;
                stuck = System.nanoTime() - last;
                if (freezeFrom != last) {             // no freeze being reported yet
                    if (stuck < every) continue;
                    freezeFrom = last;
                    dumps = 0;
                    lastStack = null;
                }
                // Looks at 1x, 2x, 4x, 8x... the threshold, so a long freeze is still seen near its end.
                if (dumps >= LagWatch.FREEZE_DUMPS.get() || stuck < every << dumps) continue;
                look = ++dumps;
            }
            dump(s, stuck, look);
        }
    }

    private void dump(MinecraftServer s, long stuck, int look) {
        Thread main = s.getRunningThread();
        StackTraceElement[] stack = main.getStackTrace();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%s  FREEZE: no tick finished for %.1f s (look %d)%n",
                LocalDateTime.now().format(STAMP), stuck / 1e9, look));
        if (look == 1) {
            try {                                     // the server thread is stuck, so the player list holds still
                for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                    BlockPos pos = p.blockPosition();
                    out.append(String.format(Locale.ROOT, "  Player %s at %d %d %d in %s%n", p.getGameProfile().getName(),
                            pos.getX(), pos.getY(), pos.getZ(), p.level().dimension().location()));
                }
            } catch (RuntimeException ignored) {
                // Only a nice-to-have; the stack below is what matters.
            }
        }
        if (Arrays.equals(stack, lastStack)) {
            out.append("  Same stack as the look before.\n");
        } else {
            Set<String> mods = new LinkedHashSet<>();
            for (StackTraceElement e : stack) {
                // Below here is the server's own loop, which ModernFix wraps on every server; not a suspect.
                if (e.getClassName().equals("net.minecraft.server.MinecraftServer")
                        && (e.getMethodName().equals("waitUntilNextTick") || e.getMethodName().equals("tickServer"))) break;
                String c = e.getClassName();
                if (Arrays.stream(VANILLA).noneMatch(c::startsWith)) mods.add(c.substring(0, Math.max(c.lastIndexOf('.'), 0)));
                // Mixin handlers live in vanilla classes but carry the mod id: handler$abc000$modid$method.
                String m = e.getMethodName();
                if (m.contains("$") && (m.startsWith("handler$") || m.startsWith("redirect$") || m.startsWith("wrapOperation$")
                        || m.startsWith("modify") || m.startsWith("localvar$"))) {
                    String[] parts = m.split("\\$");
                    if (parts.length > 2) mods.add("mixin from " + parts[2]);
                }
            }
            out.append("  Mod code on the server thread, innermost first: ")
                    .append(mods.isEmpty() ? "none (vanilla or NeoForge only)" : String.join(", ", mods)).append('\n');
            out.append("  Server thread stack (").append(main.getState()).append("):\n");
            for (StackTraceElement e : stack) out.append("    at ").append(e).append('\n');
            lastStack = stack;
        }
        String text = out.toString().stripTrailing();
        LOG.warn("[Lag Watch] {}", text);
        Watcher.logFile(text);
    }
}
