package dev.creategalore.bridge.guideai;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Where the Field Guide's voice keeps its settings and the server owner's Anthropic API key: a folder in the user
 * account that runs the server (%APPDATA%\create-galore on Windows, ~/.config/create-galore elsewhere), outside the
 * server folder, so the key is never in a world backup, a pack update, a crash report or a config that syncs to
 * clients. The updater's start prompt (tools/updater) writes both files; this only reads them.
 */
public final class GuideAiSettings {
    private static final Logger LOG = LogUtils.getLogger();
    static final String PROPERTIES = "field-guide-ai.properties";
    static final String KEY_FILE = "anthropic.key";

    final String model;
    final String effort;
    final int maxTokens;
    final int dailyQuestions;
    final int cooldownSeconds;
    final boolean webSearch;
    final String apiUrl;
    /** Never logged, never sent anywhere but the x-api-key header to {@link #apiUrl}. */
    private final String key;

    private GuideAiSettings(Properties p, String key) {
        this.model = p.getProperty("model", "claude-opus-5-5").trim();
        this.effort = p.getProperty("effort", "medium").trim().toLowerCase(Locale.ROOT);
        this.maxTokens = intProperty(p, "max_tokens", 32000, 1024, 64000);
        this.dailyQuestions = intProperty(p, "daily_questions", 100, 1, 10000);
        this.cooldownSeconds = intProperty(p, "cooldown_seconds", 10, 0, 3600);
        this.webSearch = !"false".equalsIgnoreCase(p.getProperty("web_search", "true").trim());
        String url = p.getProperty("api_url", "https://api.anthropic.com").trim();
        this.apiUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.key = key;
    }

    /** The settings a server without a properties file gets, with no key (the casebook's replay of the loop). */
    static GuideAiSettings defaults() {
        return new GuideAiSettings(new Properties(), "");
    }

    String key() {
        return key;
    }

    /** The settings folder; CREATE_GALORE_HOME overrides it (tests). The updater uses the same rule. */
    public static Path folder() {
        String home = System.getenv("CREATE_GALORE_HOME");
        if (home != null && !home.isBlank()) return Path.of(home);
        String appData = System.getenv("APPDATA");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") && appData != null && !appData.isBlank())
            return Path.of(appData, "create-galore");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".config");
        return base.resolve("create-galore");
    }

    /** The settings if the owner turned the voice on and saved a key, else null (the voice stays off). */
    static GuideAiSettings load() {
        Path dir = folder();
        Path props = dir.resolve(PROPERTIES);
        Path keyFile = dir.resolve(KEY_FILE);
        Properties p = new Properties();
        if (Files.isRegularFile(props)) {
            try (Reader r = Files.newBufferedReader(props, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                LOG.warn("Field Guide voice: couldn't read {} ({}), staying off", props, e.toString());
                return null;
            }
        }
        if (!"true".equalsIgnoreCase(p.getProperty("enabled", "false").trim())) return null;
        String key;
        try {
            key = Files.isRegularFile(keyFile) ? Files.readString(keyFile, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            LOG.warn("Field Guide voice: couldn't read the key file ({}), staying off", e.getClass().getSimpleName());
            return null;
        }
        if (key.isEmpty() || key.contains("\n") || key.contains(" ")) {
            LOG.warn("Field Guide voice is turned on but {} is missing or not a single key; staying off", keyFile);
            return null;
        }
        warnIfShared(keyFile);
        return new GuideAiSettings(p, key);
    }

    private static void warnIfShared(Path keyFile) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(keyFile);
            if (perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ))
                LOG.warn("Field Guide voice: {} can be read by other accounts on this computer; run chmod 600 on it", keyFile);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: the updater gives the file an owner-only access list when it saves it.
        }
    }

    private static int intProperty(Properties p, String name, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(name, "").trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "GuideAiSettings[model=" + model + ", effort=" + effort + "]";
    }
}
