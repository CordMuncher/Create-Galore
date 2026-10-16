import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs from the server folder's start-server script, before the server starts:
 *
 *   java -jar create-galore-updater.jar --server-start
 *
 * Checks whether a newer pack is out and asks in the console: no / yes / back up the world first, then yes.
 * Never updates without an answer: no answer, no console or no internet all mean "start as it is".
 *
 * Servers built by the install-server scripts (packwiz.json) update with packwiz-installer from the pack on
 * GitHub. Servers built by the Server Setup app (.galore-installed.txt) update with the matching app from
 * the latest release, run as "--update", which keeps memory, port, Elytra and edited configs.
 *
 * Exit code 0: start the server. Anything else: don't (the update failed or needs a restart of the script).
 */
final class ServerUpdateCheck {
    static final String REPO = "https://github.com/emresahin0906-code/Create-Galore";
    static final String RELEASE_INFO = REPO + "/releases/latest/download/Create-Galore-pack-info.txt";
    static final String STATE = ".galore-pack.txt";
    static final int ANSWER_SECONDS = 120;
    static final int START_AGAIN = 10;

    final Path here = Paths.get("").toAbsolutePath();
    final PrintStream out = System.out;

    public static void main(String[] args) { System.exit(new ServerUpdateCheck().run(args)); }

    int run(String[] args) {
        try {
            return check(Arrays.asList(args));
        } catch (Exception e) {
            // Only the update itself can fail the start; a broken check must never keep the server down.
            out.println("Create Galore update check failed (" + e + "). Starting the server as it is.");
            return 0;
        }
    }

    int check(List<String> args) throws Exception {
        Map<String, String> state = readKeyValues(here.resolve(STATE));
        boolean scripts = Files.isRegularFile(here.resolve("packwiz.json"));
        boolean app = Files.isRegularFile(here.resolve(".galore-installed.txt"));
        if ("app".equals(state.get("installer"))) scripts = false;
        else if ("script".equals(state.get("installer"))) app = false;
        if (!scripts && !app) {
            out.println("Create Galore update check: this folder wasn't set up by install-server or Server Setup, skipping.");
            return 0;
        }

        // What's installed, and what's out.
        String installedHash, installedVersion = state.getOrDefault("pack", "?");
        String latestHash, latestVersion, latestNeoForge;
        String packUrl = args.size() > 1 ? args.get(1) : CreateGaloreUpdater.PACK_URL;
        out.println("Checking for Create Galore updates...");
        if (scripts) {
            Matcher m = Pattern.compile("\"packFileHash\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([0-9a-fA-F]+)\"")
                .matcher(new String(Files.readAllBytes(here.resolve("packwiz.json")), StandardCharsets.UTF_8));
            installedHash = m.find() ? m.group(1) : "";
            byte[] toml = fetch(packUrl);
            if (toml == null) return offline();
            String text = new String(toml, StandardCharsets.UTF_8);
            latestHash = sha256(toml);
            latestVersion = Objects.toString(tomlString(text, "version"), "?");
            latestNeoForge = tomlString(text, "neoforge");
        } else {
            installedHash = state.getOrDefault("pack_sha256", "");
            byte[] info = fetch(RELEASE_INFO);
            if (info == null) return offline();
            Map<String, String> kv = parseKeyValues(new String(info, StandardCharsets.UTF_8));
            latestHash = kv.getOrDefault("pack_sha256", "");
            latestVersion = kv.getOrDefault("pack", "?");
            latestNeoForge = kv.get("neoforge");
        }
        if (latestHash.isEmpty() || latestHash.equalsIgnoreCase(installedHash)) {
            out.println("Create Galore " + installedVersion + " is up to date.");
            return 0;
        }

        String what = latestVersion.equals(installedVersion) ? "Create Galore " + latestVersion + " has new changes"
            : installedVersion.equals("?") ? "Create Galore update available: " + latestVersion
            : "Create Galore update available: " + installedVersion + " -> " + latestVersion;
        boolean newNeoForge = latestNeoForge != null && !Files.isDirectory(here.resolve(Paths.get("libraries", "net", "neoforged", "neoforge", latestNeoForge)));
        if (scripts && newNeoForge) {
            out.println(what + ". It moves the server to NeoForge " + latestNeoForge + ", which this check can't install.");
            out.println("Stop here and run install-server from the new server kit (" + REPO + "/releases/latest). Starting the server as it is.");
            return 0;
        }
        if (app && File.separatorChar != '\\') {
            out.println(what + ". Run the newest Server Setup app on this folder to update. Starting the server as it is.");
            return 0;
        }
        if (System.console() == null) {
            out.println(what + ". Not updating: start the server from a console window to choose.");
            return 0;
        }

        char choice = ask(what);
        if (choice == 'N') {
            out.println("Starting without updating. You'll be asked again next start.");
            return 0;
        }
        if (choice == 'B') {
            Path zip = backup(installedVersion, latestVersion);
            if (zip == null) return 1;
        }

        out.println();
        out.println("Updating to " + latestVersion + "...");
        int code = scripts ? updateWithPackwiz(packUrl) : updateWithApp(latestVersion);
        if (code != 0) {
            out.println();
            out.println("THE UPDATE DIDN'T FINISH (exit code " + code + "), so the server won't start: a half-updated server can");
            out.println("lose blocks and items from the world. Start again to retry (check the internet connection), or put the");
            out.println("backup in " + here.resolve("backups") + " back if you made one.");
            return 1;
        }
        if (scripts) {
            state.put("pack", latestVersion);
            state.put("installer", "script");
            writeState(state);
        }
        if (!hasTwilightForest())
            out.println("Twilight Forest isn't in mods/ any more: put twilightforest-1.21.1-4.8.3345-universal.jar back before players join.");
        if (app && newNeoForge) {
            out.println("Updated to " + latestVersion + ", including NeoForge " + latestNeoForge + ". Run start-server again to start the server.");
            return START_AGAIN;
        }
        out.println("Updated to " + latestVersion + ". Players need the same version: their pre-launch updater gets it on the next launch.");
        out.println("Starting the server.");
        return 0;
    }

    int offline() {
        out.println("Couldn't reach GitHub to check for updates. Starting the server as it is.");
        return 0;
    }

    // ---------- Asking ----------

    char ask(String what) throws InterruptedException {
        String line = "=========================================================================";
        out.println();
        out.println(line);
        out.println("  " + what + ".");
        out.println("  The server hasn't started yet, so nothing has been changed.");
        out.println(line);
        out.println("  N  No, start the server without updating");
        out.println("  Y  Yes, update, then start the server");
        out.println("  B  Back up the world first (into the backups folder), then update and start");
        out.println(line);
        LinkedBlockingQueue<String> answers = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                for (String s; (s = in.readLine()) != null; ) answers.add(s);
            } catch (IOException ignored) { }
            answers.add("\u0000"); // end of input
        });
        reader.setDaemon(true);
        reader.start();
        while (true) {
            out.print("Update? [N/Y/B] (no answer in " + ANSWER_SECONDS / 60 + " minutes = N): ");
            out.flush();
            String a = answers.poll(ANSWER_SECONDS, TimeUnit.SECONDS);
            if (a == null) { out.println(); out.println("No answer."); return 'N'; }
            a = a.trim().toUpperCase(Locale.ROOT);
            if (a.equals("\u0000")) return 'N';
            if (a.equals("N") || a.equals("NO")) return 'N';
            if (a.equals("Y") || a.equals("YES")) return 'Y';
            if (a.equals("B") || a.equals("BACKUP")) return 'B';
            out.println("Type N, Y or B and press Enter.");
        }
    }

    // ---------- Backup ----------

    /** Everything an update can change, plus the world: unzip it over the server folder to go back. */
    List<Path> backupTargets() throws IOException {
        List<Path> t = new ArrayList<>();
        for (Path w : worlds()) t.add(w);
        for (String n : new String[] { "mods", "config", "defaultconfigs", "server.properties", "user_jvm_args.txt",
                "whitelist.json", "ops.json", "packwiz.json", ".galore-installed.txt", STATE, "start-server.bat", "start-server.sh" })
            if (Files.exists(here.resolve(n))) t.add(here.resolve(n));
        return t;
    }

    /** The world folders in the server folder (normally just "world"): any folder with a level.dat. */
    List<Path> worlds() throws IOException {
        List<Path> w = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(here, Files::isDirectory)) {
            for (Path d : dirs) if (Files.isRegularFile(d.resolve("level.dat"))) w.add(d);
        }
        return w;
    }

    Path backup(String from, String to) throws IOException {
        List<Path> targets = backupTargets();
        if (worlds().isEmpty()) {
            out.println("There's no world yet (first start), so there's nothing to back up.");
            return here;
        }
        long[] size = { 0 };
        for (Path p : targets) walk(p, (f, rel) -> size[0] += Files.size(f));
        Path dir = here.resolve("backups");
        Files.createDirectories(dir);
        long free = Files.getFileStore(dir).getUsableSpace();
        if (free < size[0] + (512L << 20)) {
            out.println("Not enough disk space for the backup (needs about " + mb(size[0]) + ", " + mb(free) + " free).");
            out.println("Nothing was changed and the server won't start. Free some space and start again, or choose N.");
            return null;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        Path zip = dir.resolve("world-" + stamp + "-before-" + safe(to) + ".zip");
        Path part = dir.resolve(zip.getFileName() + ".part");
        out.println("Backing up the world, mods and configs (" + mb(size[0]) + ") to " + zip + " ...");
        long[] done = { 0 }, shown = { 0 };
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(part)))) {
            z.setLevel(Deflater.BEST_SPEED);
            z.putNextEntry(new ZipEntry("RESTORE.txt"));
            z.write(("Create Galore server backup, made " + stamp + " before updating " + from + " -> " + to + ".\n"
                + "To go back: stop the server, delete the world, mods and config folders in the server folder,\n"
                + "then unzip this file into the server folder. The server is then exactly as it was.\n")
                .replace("\n", System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            byte[] buf = new byte[1 << 16];
            for (Path p : targets) walk(p, (f, rel) -> {
                if (f.getFileName().toString().equals("session.lock")) return;
                z.putNextEntry(new ZipEntry(rel));
                try (InputStream in = Files.newInputStream(f)) {
                    for (int n; (n = in.read(buf)) > 0; ) { z.write(buf, 0, n); done[0] += n; }
                }
                z.closeEntry();
                if (size[0] > 0 && done[0] * 10 / size[0] > shown[0]) { shown[0] = done[0] * 10 / size[0]; out.println("  " + shown[0] * 10 + "%"); }
            });
        } catch (IOException e) {
            Files.deleteIfExists(part);
            out.println("The backup failed (" + e.getMessage() + "). Nothing was changed and the server won't start.");
            out.println("Start again and choose N to play without updating.");
            return null;
        }
        Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
        out.println("Backup done: " + zip);
        return zip;
    }

    interface FileAction { void accept(Path file, String rel) throws IOException; }

    void walk(Path root, FileAction action) throws IOException {
        if (Files.isRegularFile(root)) { action.accept(root, rel(root)); return; }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                if (a.isRegularFile()) action.accept(f, rel(f));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    String rel(Path f) { return here.relativize(f).toString().replace('\\', '/'); }

    static String mb(long bytes) { return (bytes >> 20) + " MB"; }

    static String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    // ---------- Updating ----------

    int updateWithPackwiz(String packUrl) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(java(), "-jar", "packwiz-installer-bootstrap.jar", "--bootstrap-no-update", "-g", "-s", "server", packUrl));
        return new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }

    int updateWithApp(String version) throws Exception {
        Path dir = here.resolve(".galore-update");
        Files.createDirectories(dir);
        String name = "Create-Galore-Server-Setup-" + version + ".exe";
        Path exe = dir.resolve(name);
        out.println("Downloading " + name + " from the release...");
        byte[] data = fetch(REPO + "/releases/download/v" + version + "/" + name);
        if (data == null) {
            out.println("Couldn't download it. The release may still be building: wait a few minutes and start again.");
            return 2;
        }
        Files.write(exe, data);
        Path log = dir.resolve("update-log.txt");
        int code = new ProcessBuilder(exe.toString(), "--update", here.toString(), "--log", log.toString())
            .inheritIO().start().waitFor();
        if (Files.isRegularFile(log))
            for (String l : Files.readAllLines(log, StandardCharsets.UTF_8)) if (!l.startsWith("  ")) out.println("  " + l);
        if (code == 0) Files.deleteIfExists(exe);
        return code;
    }

    boolean hasTwilightForest() throws IOException {
        if (!Files.isDirectory(here.resolve("mods"))) return false;
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(here.resolve("mods"), "twilightforest*.jar")) {
            return jars.iterator().hasNext();
        }
    }

    static String java() {
        return Paths.get(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java").toString();
    }

    // ---------- Small helpers ----------

    /** The body of a URL, or null when it can't be read (offline, 404, timeout). */
    byte[] fetch(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://"))
                return Files.isRegularFile(Paths.get(url)) ? Files.readAllBytes(Paths.get(url)) : null;
            for (int hops = 0; hops < 5; hops++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "create-galore-updater");
                c.setInstanceFollowRedirects(true);
                int status = c.getResponseCode();
                if (status / 100 == 3 && c.getHeaderField("Location") != null) { url = c.getHeaderField("Location"); continue; }
                if (status != 200) return null;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[1 << 16];
                    for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                    return b.toByteArray();
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    static String sha256(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String tomlString(String toml, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"").matcher(toml);
        return m.find() ? m.group(1) : null;
    }

    static Map<String, String> readKeyValues(Path p) throws IOException {
        return Files.isRegularFile(p) ? parseKeyValues(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)) : new LinkedHashMap<>();
    }

    static Map<String, String> parseKeyValues(String text) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String l : text.split("\r?\n")) {
            int eq = l.indexOf('=');
            if (eq > 0 && !l.startsWith("#")) kv.put(l.substring(0, eq).trim(), l.substring(eq + 1).trim());
        }
        return kv;
    }

    void writeState(Map<String, String> kv) throws IOException {
        StringBuilder sb = new StringBuilder("# Create Galore pack version this server has, for the update check at start\n");
        for (Map.Entry<String, String> e : kv.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        Files.write(here.resolve(STATE), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
