import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/**
 * The Field Guide's voice (the Create Galore Bridge mod asks Claude for players) needs the server owner's
 * Anthropic API key. This asks for it at server start and keeps it where only the server's user account can
 * read it: a folder outside the server (%APPDATA%\create-galore on Windows, ~/.config/create-galore elsewhere),
 * so it is never in a world backup, a pack update, a log, or a config that the game sends to players.
 *
 * First start: "give the book a voice? [N/Y]". After that one status line, and K within 5 seconds to change it.
 * Also runs on its own: java -jar create-galore-updater.jar --api-key
 */
final class FieldGuideKey {
    static final String PROPERTIES = "field-guide-ai.properties";
    static final String KEY_FILE = "anthropic.key";
    static final int FIRST_ANSWER_SECONDS = 120;
    static final int CHANGE_SECONDS = 5;

    private final PrintStream out;
    private final Path dir = folder();

    FieldGuideKey(PrintStream out) { this.out = out; }

    /** Same rule as the Bridge mod's GuideAiSettings.folder(). */
    static Path folder() {
        String home = System.getenv("CREATE_GALORE_HOME");
        if (home != null && !home.isBlank()) return Paths.get(home);
        String appData = System.getenv("APPDATA");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") && appData != null && !appData.isBlank())
            return Paths.get(appData, "create-galore");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Paths.get(xdg) : Paths.get(System.getProperty("user.home"), ".config");
        return base.resolve("create-galore");
    }

    /** Never fails the start: anything that goes wrong here just leaves the voice as it was. */
    static void atServerStart(PrintStream out) {
        try {
            new FieldGuideKey(out).serverStart();
        } catch (Exception e) {
            out.println("Field Guide voice setup skipped (" + e.getClass().getSimpleName() + "). The server starts as usual.");
        }
    }

    static void standalone() {
        PrintStream out = System.out;
        if (System.console() == null) {
            out.println("Run this from a console window: it asks for the key without showing it.");
            System.exit(2);
        }
        try {
            FieldGuideKey k = new FieldGuideKey(out);
            k.status();
            k.change();
        } catch (Exception e) {
            out.println("Couldn't change the Field Guide voice (" + e + ").");
            System.exit(1);
        }
    }

    void serverStart() throws Exception {
        Path props = dir.resolve(PROPERTIES);
        if (System.console() == null) {
            if (Files.isRegularFile(props)) status();
            return;
        }
        if (!Files.isRegularFile(props)) {
            firstTime();
            return;
        }
        status();
        out.print("Type K and press Enter within " + CHANGE_SECONDS + " seconds to change it: ");
        out.flush();
        String a = Prompt.line(CHANGE_SECONDS);
        out.println();
        if (a != null && a.trim().equalsIgnoreCase("K")) change();
    }

    void firstTime() throws Exception {
        String line = "=========================================================================";
        out.println();
        out.println(line);
        out.println("  New: the Field Guide can answer players' questions in its own words (Claude).");
        out.println("  It needs your own Anthropic API key and runs on this server only; players never");
        out.println("  see the key. Say N on a server you rent or share, or if you don't have a key.");
        out.println("  You can change this at any later start.");
        out.println(line);
        while (true) {
            out.print("Give the book a voice? [N/Y] (no answer in " + FIRST_ANSWER_SECONDS / 60 + " minutes = N): ");
            out.flush();
            String a = Prompt.line(FIRST_ANSWER_SECONDS);
            if (a == null || a.equals(Prompt.END)) {
                out.println();
                out.println("No answer: the book stays quiet. You'll be asked again next start.");
                return;
            }
            a = a.trim().toUpperCase(Locale.ROOT);
            if (a.equals("N") || a.equals("NO")) {
                writeProperties(false);
                out.println("The book stays quiet. Type K at a later start to change that.");
                return;
            }
            if (a.equals("Y") || a.equals("YES")) {
                askForKey();
                return;
            }
            out.println("Type N or Y and press Enter.");
        }
    }

    void status() {
        boolean enabled = "true".equalsIgnoreCase(readProperties().getProperty("enabled", "false").trim());
        boolean key = Files.isRegularFile(dir.resolve(KEY_FILE));
        out.println("Field Guide voice: " + (enabled && key ? "on (API key saved in " + dir + ")" : key ? "off (a key is saved)" : "off") + ".");
    }

    void change() throws Exception {
        if (!Prompt.canReadSecret()) {
            out.println("Can't ask for the key right now. Run: java -jar create-galore-updater.jar --api-key");
            return;
        }
        out.println("Paste a new API key and press Enter (it won't show). Type OFF to turn the voice off and delete");
        out.println("the saved key, or just press Enter to keep things as they are.");
        askForKey();
    }

    /** Asks until a key works, up to three times. The key is only ever held in memory and written to the key file. */
    void askForKey() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            out.print("API key: ");
            out.flush();
            String key = Prompt.secret();
            if (key == null) {
                out.println("No console to read the key from. Nothing changed.");
                return;
            }
            key = key.trim();
            if (key.isEmpty()) {
                out.println("Nothing changed.");
                return;
            }
            if (key.equalsIgnoreCase("OFF")) {
                Files.deleteIfExists(dir.resolve(KEY_FILE));
                writeProperties(false);
                out.println("The voice is off and the key is deleted from this computer.");
                return;
            }
            if (key.contains(" ") || key.length() < 20 || key.length() > 400) {
                out.println("That doesn't look like an API key (they start with sk-ant- and have no spaces). Try again.");
                continue;
            }
            out.println("Checking the key with Anthropic...");
            int status = check(key);
            if (status == 200) {
                saveKey(key);
                writeProperties(true);
                out.println("Key works and is saved in " + dir.resolve(KEY_FILE) + " (only your account can read it).");
                out.println("The book has a voice. Set a monthly spend limit for this key's workspace in the Claude Console.");
                return;
            }
            if (status == 401 || status == 403) {
                out.println("Anthropic refused that key (" + status + "). Copy it again from the Claude Console and try again.");
                continue;
            }
            out.println(status == 0 ? "Couldn't reach Anthropic to check the key (no internet?)."
                : "Anthropic answered " + status + " when checking the key.");
            out.print("Save it anyway? [N/Y]: ");
            out.flush();
            String a = Prompt.line(FIRST_ANSWER_SECONDS);
            if (a != null && (a.trim().equalsIgnoreCase("Y") || a.trim().equalsIgnoreCase("YES"))) {
                saveKey(key);
                writeProperties(true);
                out.println("Saved. If the key is wrong, the book will say so when someone asks it something.");
            } else {
                out.println("Not saved. Nothing changed.");
            }
            return;
        }
        out.println("Nothing changed. Type K at the next start to try again.");
    }

    /** A free call (the model list) that only works with a valid key. Returns the HTTP status, 0 if unreachable. */
    int check(String key) {
        try {
            String base = readProperties().getProperty("api_url", "https://api.anthropic.com").trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/v1/models?limit=1").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("x-api-key", key);
            c.setRequestProperty("anthropic-version", "2023-06-01");
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    // ---------- files ----------

    Properties readProperties() {
        Properties p = new Properties();
        try (java.io.Reader r = Files.newBufferedReader(dir.resolve(PROPERTIES), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            // none yet
        }
        return p;
    }

    /** Writes the settings file on first use; later only its enabled line changes, so hand edits stay. */
    void writeProperties(boolean enabled) throws IOException {
        makeFolder();
        Path file = dir.resolve(PROPERTIES);
        if (Files.isRegularFile(file)) {
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("enabled")) {
                    lines.set(i, "enabled=" + enabled);
                    found = true;
                }
            }
            if (!found) lines.add(0, "enabled=" + enabled);
            Files.write(file, lines, StandardCharsets.UTF_8);
            return;
        }
        String text = String.join("\n",
            "# Create Galore Field Guide voice. Read by the Create Galore Bridge mod when the server starts.",
            "# The API key itself is in anthropic.key next to this file; never put it here or in the server folder.",
            "enabled=" + enabled,
            "# Model and thinking effort (low, medium, high). Sonnet (claude-sonnet-5-5) costs about half as much.",
            "model=claude-opus-5-5",
            "effort=medium",
            "max_tokens=16000",
            "# Questions each player can ask per day, and seconds between their questions.",
            "daily_questions=100",
            "cooldown_seconds=10",
            "# Look things up on Modrinth and the mods' own wikis when the guide has no answer.",
            "web_search=true",
            "");
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        ownerOnly(file);
    }

    void saveKey(String key) throws IOException {
        makeFolder();
        Path file = dir.resolve(KEY_FILE);
        Path tmp = dir.resolve(KEY_FILE + ".new");
        Files.deleteIfExists(tmp);
        if (posix()) {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(tmp);
            ownerOnly(tmp);
        }
        Files.write(tmp, key.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        ownerOnly(file);
    }

    void makeFolder() throws IOException {
        if (!Files.isDirectory(dir)) {
            if (posix()) Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            else Files.createDirectories(dir);
        }
        ownerOnly(dir);
    }

    static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /** chmod 600/700 on Linux and macOS; on Windows an access list that names only the owner. */
    static void ownerOnly(Path p) throws IOException {
        if (posix()) {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(Files.isDirectory(p) ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView view = Files.getFileAttributeView(p, AclFileAttributeView.class);
        if (view == null) return;
        UserPrincipal owner = view.getOwner();
        AclEntry.Builder b = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (Files.isDirectory(p)) b.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        view.setAcl(List.of(b.build()));
    }
}
