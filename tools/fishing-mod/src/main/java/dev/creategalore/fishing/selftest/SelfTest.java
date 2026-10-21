package dev.creategalore.fishing.selftest;

import dev.creategalore.fishing.CreateGaloreFishing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Headless checks a test server can run without a player: {@code /cgfish selftest <suite|all>} from the console, or
 * at start with {@code -Dcgfish.selftest=all} (add {@code -Dcgfish.exit=true} to stop the server afterwards, for
 * scripts). Each suite writes {@code create_galore_fishing/selftest-<suite>.txt} in the server folder with one
 * PASS/FAIL line per check, and the log gets a one-line summary.
 *
 * <p>Feature areas add their own suites with {@link #suite}; a suite reports through the {@link Report} it is given
 * and should never throw (an exception counts as one failure).
 */
public final class SelfTest {
    /** A named group of checks. */
    @FunctionalInterface
    public interface Suite {
        void run(MinecraftServer server, Report report);
    }

    /** Collects check results for one suite. */
    public static final class Report {
        private final List<String> lines = new ArrayList<>();
        private int passed;
        private int failed;

        /** Records one check; returns {@code ok} so callers can chain on it. */
        public boolean check(boolean ok, String what) {
            lines.add((ok ? "PASS " : "FAIL ") + what);
            if (ok) passed++;
            else failed++;
            return ok;
        }

        public void pass(String what) {
            check(true, what);
        }

        public void fail(String what) {
            check(false, what);
        }

        /** An informational line that is neither a pass nor a failure (numbers, tables). */
        public void info(String what) {
            lines.add("INFO " + what);
        }

        public int failed() {
            return failed;
        }

        public int passed() {
            return passed;
        }
    }

    private static final Map<String, Suite> SUITES = new LinkedHashMap<>();
    /**
     * The overworld chunks the suites build in, at the top of the world near 0, 0. Their entities load a little after
     * the chunks do, and until then the items and orbs a test drops there are invisible to it, so a run waits for them.
     */
    private static final List<ChunkPos> AREA = new ArrayList<>();
    /** Waits at most this long for the area's entities (10 s); a run that times out goes ahead and may fail. */
    private static final int WAIT_TICKS = 200;
    private static Waiting waiting;

    private record Waiting(String wanted, IntConsumer done, List<ChunkPos> forced, int deadline) {
    }

    static {
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) AREA.add(new ChunkPos(x, z));
    }

    private SelfTest() {
    }

    /** Registers a suite under {@code name}. Call during mod construction. */
    public static void suite(String name, Suite suite) {
        SUITES.put(name, suite);
    }

    public static Iterable<String> names() {
        return SUITES.keySet();
    }

    public static void register() {
        suite("boot", (server, report) -> report.pass("mod loaded, server started"));
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerTick);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        String wanted = System.getProperty("cgfish.selftest");
        if (wanted == null || wanted.isBlank()) return;
        MinecraftServer server = event.getServer();
        start(server, wanted, failures -> {
            if (Boolean.getBoolean("cgfish.exit")) {
                CreateGaloreFishing.LOG.info("Self-test finished with {} failure(s); stopping the server as asked", failures);
                server.halt(false);
            }
        });
    }

    /**
     * Runs the suites like {@link #run}, once the test area's entities are loaded: right away if they are (returns
     * true), otherwise on a later tick after force-loading it (returns false). {@code done} gets the failure count.
     */
    public static boolean start(MinecraftServer server, String wanted, IntConsumer done) {
        ServerLevel level = server.overworld();
        List<ChunkPos> forced = new ArrayList<>();
        for (ChunkPos chunk : AREA) {
            if (!level.areEntitiesLoaded(chunk.toLong()) && level.setChunkForced(chunk.x, chunk.z, true)) forced.add(chunk);
        }
        if (forced.isEmpty() && AREA.stream().allMatch(chunk -> level.areEntitiesLoaded(chunk.toLong()))) {
            done.accept(run(server, wanted));
            return true;
        }
        if (waiting != null) forced.addAll(waiting.forced());
        waiting = new Waiting(wanted, done, forced, server.getTickCount() + WAIT_TICKS);
        return false;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (waiting == null) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        boolean loaded = AREA.stream().allMatch(chunk -> level.areEntitiesLoaded(chunk.toLong()));
        if (!loaded && server.getTickCount() < waiting.deadline()) return;
        Waiting run = waiting;
        waiting = null;
        if (!loaded) CreateGaloreFishing.LOG.warn("Self-test area still loading after {} ticks; running anyway", WAIT_TICKS);
        int failures = run(server, run.wanted());
        for (ChunkPos chunk : run.forced()) level.setChunkForced(chunk.x, chunk.z, false);
        run.done().accept(failures);
    }

    /**
     * Runs the suites named in {@code wanted} (comma separated, or {@code all}) and returns the number of failed
     * checks.
     */
    public static int run(MinecraftServer server, String wanted) {
        List<String> names = new ArrayList<>();
        for (String name : wanted.split(",")) {
            name = name.trim();
            if (name.equals("all")) names.addAll(SUITES.keySet());
            else if (!name.isEmpty()) names.add(name);
        }
        int failures = 0;
        Path dir = server.getServerDirectory().resolve(CreateGaloreFishing.ID);
        for (String name : names) {
            Suite suite = SUITES.get(name);
            Report report = new Report();
            if (suite == null) {
                report.fail("no suite called '" + name + "' (known: " + String.join(", ", SUITES.keySet()) + ")");
            } else {
                try {
                    suite.run(server, report);
                } catch (RuntimeException | LinkageError e) {
                    report.fail("suite threw " + e);
                    CreateGaloreFishing.LOG.error("Self-test suite {} threw", name, e);
                }
            }
            failures += report.failed();
            CreateGaloreFishing.LOG.info("Self-test {}: {} passed, {} failed", name, report.passed(), report.failed());
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve("selftest-" + name + ".txt"), report.lines);
            } catch (IOException e) {
                CreateGaloreFishing.LOG.error("Couldn't write the self-test report for {}", name, e);
            }
        }
        return failures;
    }
}
