package dev.creategalore.fishing.selftest;

import dev.creategalore.fishing.CreateGaloreFishing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
    }

    private static void onServerStarted(ServerStartedEvent event) {
        String wanted = System.getProperty("cgfish.selftest");
        if (wanted == null || wanted.isBlank()) return;
        int failures = run(event.getServer(), wanted);
        if (Boolean.getBoolean("cgfish.exit")) {
            CreateGaloreFishing.LOG.info("Self-test finished with {} failure(s); stopping the server as asked", failures);
            event.getServer().halt(false);
        }
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
