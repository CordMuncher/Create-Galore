package dev.creategalore.lagwatch;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Measures real ticks per second from wall-clock tick times and raises the alarm. */
final class Watcher {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int WINDOW = 100;           // ticks: 5 s at full speed
    private static final long RECOVER_NANOS = 10_000_000_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long[] tickEnds = new long[WINDOW];
    private int ticks;                                // total ticks seen
    private long startedAt;                           // server ready, nanoTime

    private long lowSince = -1;                       // TPS under alertTps since (nanoTime), -1 when not
    private long highSince = -1;                      // TPS back over recoverTps since, during a spell
    private boolean inSpell;
    private long spellStart;
    private double spellWorst;

    private boolean profiled;                         // an automatic profile has run since start
    private long lastProfileAt;
    private long profileStopAt = -1;                  // when to stop the running profile
    private long profileDueAt = -1;                   // when to look for the saved file
    private long profileStartedMillis;
    private String profileComment;

    void onServerStarted(ServerStartedEvent event) {
        startedAt = System.nanoTime();
        if (!LagWatch.ENABLED.get()) return;
        LOG.info("Lag Watch on: alert under {} TPS for {} s, profiles {}", LagWatch.ALERT_TPS.get(),
                LagWatch.ALERT_AFTER_SECONDS.get(), canProfile() ? "via spark" : "off (spark missing or disabled)");
    }

    void onTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        tickEnds[ticks % WINDOW] = now;
        ticks++;
        // Everything below runs once a second (by tick count), so the cost is a few array reads per tick.
        if (ticks % 20 != 0 || ticks < WINDOW || !LagWatch.ENABLED.get()) return;
        MinecraftServer server = event.getServer();
        if (profileStopAt > 0 && now >= profileStopAt) stopProfile(server, now);
        if (profileDueAt > 0 && now >= profileDueAt) findSavedProfile(server, now);
        if (now - startedAt < LagWatch.STARTUP_GRACE_SECONDS.get() * 1_000_000_000L) return;

        double tps = tps();
        if (!inSpell) {
            if (tps < LagWatch.ALERT_TPS.get()) {
                if (lowSince < 0) lowSince = now;
                if (now - lowSince >= LagWatch.ALERT_AFTER_SECONDS.get() * 1_000_000_000L) startSpell(server, tps, now, false);
            } else {
                lowSince = -1;
            }
            return;
        }
        spellWorst = Math.min(spellWorst, tps);
        if (tps >= LagWatch.RECOVER_TPS.get()) {
            if (highSince < 0) highSince = now;
            if (now - highSince >= RECOVER_NANOS) {
                inSpell = false;
                lowSince = -1;
                tell(server, Component.literal(String.format(Locale.ROOT, "Back to %.1f TPS after %s. Lowest was %.1f.",
                        tps, duration(now - spellStart), spellWorst)).withStyle(ChatFormatting.GREEN));
                logFile(String.format(Locale.ROOT, "%s  recovered, %.1f TPS, spell lasted %s, lowest %.1f TPS",
                        LocalDateTime.now().format(STAMP), tps, duration(now - spellStart), spellWorst));
            }
        } else {
            highSince = -1;
        }
    }

    /** Ticks completed per wall-clock second over the last WINDOW ticks, capped at 20. */
    private double tps() {
        long newest = tickEnds[(ticks - 1) % WINDOW];
        long oldest = tickEnds[ticks % WINDOW];
        double seconds = (newest - oldest) / 1e9;
        return seconds <= 0 ? 20 : Math.min(20, (WINDOW - 1) / seconds);
    }

    private void startSpell(MinecraftServer server, double tps, long now, boolean forced) {
        if (!forced) {
            inSpell = true;
            spellStart = now;
            spellWorst = tps;
            highSince = -1;
        }
        double mspt = server.getAverageTickTimeNanos() / 1e6;
        String census = census(server);
        String lead = forced
                ? String.format(Locale.ROOT, "Test alert: %.1f TPS (%.0f ms per tick).", tps, mspt)
                : String.format(Locale.ROOT, "Server is lagging: %.1f TPS (%.0f ms per tick) for %d s.",
                        tps, mspt, LagWatch.ALERT_AFTER_SECONDS.get());

        String profileNote;
        if (!canProfile()) {
            profileNote = "No profile (spark is missing or profiles are off in the config).";
        } else if (!forced && profiled && now - lastProfileAt < LagWatch.PROFILE_COOLDOWN_MINUTES.get() * 60_000_000_000L) {
            profileNote = "No new profile, the last one was " + duration(now - lastProfileAt) + " ago.";
        } else if (profileStopAt > 0 || profileDueAt > 0) {
            profileNote = "A profile is already recording.";
        } else if (startProfile(server, tps)) {
            if (!forced) {
                profiled = true;
                lastProfileAt = now;
            }
            profileNote = "Recording a " + LagWatch.PROFILE_SECONDS.get() + " s spark profile.";
        } else {
            profileNote = "Couldn't start a spark profile (is one already running?).";
        }

        tell(server, Component.literal(lead + " " + profileNote).withStyle(ChatFormatting.YELLOW));
        tell(server, Component.literal("Most entities: " + census).withStyle(ChatFormatting.GRAY));
        StringBuilder log = new StringBuilder();
        log.append(LocalDateTime.now().format(STAMP)).append(forced ? "  TEST ALERT" : "  ALERT").append('\n')
                .append(String.format(Locale.ROOT, "  %.1f TPS, %.1f ms per tick (average), %d s under %.1f%n",
                        tps, mspt, LagWatch.ALERT_AFTER_SECONDS.get(), LagWatch.ALERT_TPS.get()))
                .append("  ").append(profileNote).append('\n')
                .append("  Most entities: ").append(census).append('\n');
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getLoadedChunksCount() == 0) continue;
            log.append(String.format(Locale.ROOT, "  %s: %d chunks loaded, %d entities%n", level.dimension().location(),
                    level.getChunkSource().getLoadedChunksCount(), count(level.getAllEntities())));
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BlockPos pos = p.blockPosition();
            log.append(String.format(Locale.ROOT, "  Player %s at %d %d %d in %s%n", p.getGameProfile().getName(),
                    pos.getX(), pos.getY(), pos.getZ(), p.level().dimension().location()));
        }
        logFile(log.toString().stripTrailing());
    }

    /** The three biggest piles of one entity type in one dimension, e.g. "2,310 Item in overworld". */
    private static String census(MinecraftServer server) {
        record Pile(String what, int n) {}
        List<Pile> piles = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<EntityType<?>, Integer> counts = new HashMap<>();
            for (Entity e : level.getAllEntities()) counts.merge(e.getType(), 1, Integer::sum);
            String dim = dimName(level);
            counts.forEach((type, n) -> piles.add(new Pile(type.getDescription().getString() + " in " + dim, n)));
        }
        if (piles.isEmpty()) return "none";
        return piles.stream().sorted(Comparator.comparingInt(Pile::n).reversed()).limit(3)
                .map(p -> String.format(Locale.ROOT, "%,d %s", p.n(), p.what())).collect(Collectors.joining(", "));
    }

    private static String dimName(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) return "the Overworld";
        if (level.dimension() == Level.NETHER) return "the Nether";
        if (level.dimension() == Level.END) return "the End";
        return level.dimension().location().toString();
    }

    private static int count(Iterable<?> it) {
        int n = 0;
        for (Object ignored : it) n++;
        return n;
    }

    private static boolean canProfile() {
        return LagWatch.PROFILE.get() && ModList.get().isLoaded("spark");
    }

    private boolean startProfile(MinecraftServer server, double tps) {
        // No --timeout: a timed-out profile leaves spark's background profiler off, a stop restarts it.
        String cmd = "spark profiler start";
        int over = LagWatch.PROFILE_ONLY_TICKS_OVER_MS.get();
        if (over > 0) cmd += " --only-ticks-over " + over;
        if (!spark(server, cmd)) return false;
        profileComment = String.format(Locale.ROOT, "lagwatch-%.1f-tps", tps);
        profileStopAt = System.nanoTime() + LagWatch.PROFILE_SECONDS.get() * 1_000_000_000L;
        return true;
    }

    private void stopProfile(MinecraftServer server, long now) {
        profileStopAt = -1;
        profileStartedMillis = System.currentTimeMillis();
        if (spark(server, "spark profiler stop --save-to-file --comment " + profileComment)) {
            profileDueAt = now + 2_000_000_000L; // spark writes the file on its own thread
        }
    }

    /** Runs a spark command as the console; spark answers in the server log. */
    private static boolean spark(MinecraftServer server, String cmd) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Lag Watch couldn't run '{}'", cmd, e);
            return false;
        }
    }

    /** Find the file spark just wrote, rename it to lagwatch-<date>, announce it and trim old ones. */
    private void findSavedProfile(MinecraftServer server, long now) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("spark");
        Path saved = newest(dir, "profile-");
        if (saved == null || modified(saved) < profileStartedMillis - 1000) {
            if (now - profileDueAt > 60_000_000_000L) {
                profileDueAt = -1;
                tell(server, Component.literal("The spark profile didn't turn up in config/spark. Check the server log.")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        profileDueAt = -1;
        Path ours = dir.resolve("lagwatch-" + saved.getFileName().toString().substring("profile-".length()));
        try {
            Files.move(saved, ours);
        } catch (IOException e) {
            ours = saved;
        }
        String rel = FMLPaths.GAMEDIR.get().relativize(ours).toString().replace('\\', '/');
        tell(server, Component.literal("Profile saved: " + rel + " (open it at spark.lucko.me, see docs/server-setup.md)")
                .withStyle(ChatFormatting.YELLOW));
        logFile(LocalDateTime.now().format(STAMP) + "  profile saved: " + rel);
        prune(dir);
    }

    /** Keeps the newest Lag Watch profiles; profiles saved by hand are never touched. */
    private static void prune(Path dir) {
        int keep = LagWatch.KEEP_PROFILES.get();
        if (keep <= 0) return;
        List<Path> ours = list(dir, "lagwatch-");
        for (int i = 0; i < ours.size() - keep; i++) {
            try {
                Files.deleteIfExists(ours.get(i));
            } catch (IOException e) {
                LOG.warn("Lag Watch couldn't delete old profile {}", ours.get(i));
            }
        }
    }

    /** .sparkprofile files in dir whose name starts with prefix, oldest first. */
    private static List<Path> list(Path dir, String prefix) {
        try (Stream<Path> files = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            return files.filter(f -> {
                String n = f.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".sparkprofile");
            }).sorted(Comparator.comparingLong(Watcher::modified)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Path newest(Path dir, String prefix) {
        List<Path> all = list(dir, prefix);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void tell(MinecraftServer server, Component body) {
        MutableComponent msg = Component.literal("[Lag Watch] ").withStyle(ChatFormatting.GOLD).append(body);
        LOG.info(msg.getString());
        boolean everyone = LagWatch.TELL_EVERYONE.get();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (everyone || server.getPlayerList().isOp(p.getGameProfile())) p.sendSystemMessage(msg);
        }
    }

    private static void logFile(String text) {
        Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("lagwatch.log");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Lag Watch couldn't write {}", file, e);
        }
    }

    private static String duration(long nanos) {
        long s = nanos / 1_000_000_000L;
        return s < 60 ? s + " s" : s < 3600 ? (s / 60) + " min " + (s % 60) + " s" : (s / 3600) + " h " + (s % 3600 / 60) + " min";
    }

    void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("lagwatch").requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    double tps = ticks >= WINDOW ? tps() : 20;
                    String state = inSpell ? "lag spell in progress, lowest " + String.format(Locale.ROOT, "%.1f", spellWorst) + " TPS"
                            : "no lag spell";
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "%.1f TPS, %.1f ms per tick, %s. Alerts under %.1f TPS for %d s. Details: logs/lagwatch.log",
                            tps, server.getAverageTickTimeNanos() / 1e6, state, LagWatch.ALERT_TPS.get(),
                            LagWatch.ALERT_AFTER_SECONDS.get())), false);
                    return 1;
                })
                .then(Commands.literal("test").executes(ctx -> {
                    // Same alert, census, log entry and profile as a real lag spell, ignoring the cooldown.
                    startSpell(ctx.getSource().getServer(), ticks >= WINDOW ? tps() : 20, System.nanoTime(), true);
                    return 1;
                })));
    }
}
