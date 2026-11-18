import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pre-launch updater for a Create Galore instance. Runs from the instance's minecraft folder:
 *
 *   "$INST_JAVA" -jar create-galore-updater.jar            soft update: only changed mods and configs
 *   "$INST_JAVA" -jar create-galore-updater.jar --clean    also resets every config to the pack's version
 *   java -jar create-galore-updater.jar --server-start      on a server: ask whether to update (see ServerUpdateCheck)
 *   java -jar create-galore-updater.jar --api-key           on a server: save or change the Field Guide voice's key (FieldGuideKey)
 *
 * The update itself is packwiz-installer (pinned, shipped next to this jar). Nothing is touched unless the
 * pack can be read. --clean moves config/, defaultconfigs/, options.txt and each world's serverconfig/ aside
 * first, so the pack's files and the mods' defaults come back; if the update fails they are put back. Mods, Minecraft, NeoForge and the worlds themselves are never deleted.
 *
 * Without a pack argument the pack is the latest release's, read at the exact commit the release was built from and
 * checked against the checksum the release lists (see Release). A pack argument is a local pack.toml or an https://
 * address; plain http:// is refused, since packwiz trusts every mod address and checksum the pack lists.
 */
public final class CreateGaloreUpdater {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--server-start")) { ServerUpdateCheck.main(args); return; }
        if (args.length > 0 && args[0].equals("--api-key")) { FieldGuideKey.standalone(); return; }
        boolean clean = false;
        String url = null;
        List<String> passOn = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--clean")) clean = true;
            else if (a.startsWith("--")) passOn.add(a);
            else url = a;
        }
        if (url != null && Net.isUrl(url) && !Net.isHttps(url)) {
            System.err.println("Refusing " + url + ": the pack has to come over https:// (or from a local pack.toml),"
                + " because anyone on the network can change a plain http:// download. Nothing was changed.");
            System.exit(2);
        }
        Path here = Paths.get("").toAbsolutePath();
        if (!Files.isDirectory(here.resolve("mods")) || !Files.isRegularFile(here.resolve("packwiz-installer-bootstrap.jar"))) {
            System.err.println("Not a Create Galore instance folder (needs mods/ and packwiz-installer-bootstrap.jar): " + here);
            System.exit(2);
        }
        String expectedSha256 = null;
        if (url == null) {
            Map<String, String> info = Release.info();
            url = Release.packUrl(info);
            if (url != null) {
                expectedSha256 = info.get("pack_sha256");
            } else {
                url = Release.BRANCH_PACK_URL;
                if (info != null) System.out.println("The latest release doesn't name its commit, so this run reads the pack from the branch.");
            }
        }
        String problem = packProblem(url, expectedSha256);
        if (problem != null) {
            System.err.println("Can't read the pack at " + url + " (" + problem + "). Nothing was changed." + (Net.isUrl(url)
                ? " If the GitHub repo is private, GitHub hides its files: use a local pack folder (see the README) or make the repo public."
                : ""));
            System.exit(3);
        }
        Path backup = here.resolve(BACKUP);
        if (Files.exists(backup)) restore(here, backup); // a clean run that didn't finish
        if (clean) clean(here, backup);

        String java = Paths.get(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(java); cmd.add("-jar"); cmd.add("packwiz-installer-bootstrap.jar"); cmd.add("--bootstrap-no-update");
        cmd.addAll(passOn);
        cmd.add(url);
        // packwiz-installer moves NeoForge by editing the launcher's mmc-pack.json, but the launcher has already
        // read that file for this launch, so this run would start the old NeoForge with the new mods.
        Path mmcPack = here.getParent() == null ? null : here.getParent().resolve("mmc-pack.json");
        byte[] componentsBefore = mmcPack != null && Files.isRegularFile(mmcPack) ? Files.readAllBytes(mmcPack) : null;
        int code = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (Files.exists(backup)) {
            if (code == 0) delete(backup);
            else { System.out.println("The update failed, so --clean puts your old configs back"); restore(here, backup); }
        }
        if (code == 0 && componentsBefore != null && Files.isRegularFile(mmcPack)
                && !Arrays.equals(componentsBefore, Files.readAllBytes(mmcPack))) {
            System.out.println("The update moved the instance to a new NeoForge version. Launch the game again to start it.");
            System.exit(NEW_LOADER);
        }
        System.exit(code);
    }

    static final String BACKUP = ".galore-clean-backup";
    /** Exit code that stops this launch because NeoForge changed; the next launch uses the new version. */
    static final int NEW_LOADER = 4;

    /**
     * Null when the pack can be read (and, for a release's pack, has the checksum the release lists), else what's wrong.
     */
    static String packProblem(String url, String expectedSha256) throws Exception {
        if (!Net.isUrl(url)) return Files.isRegularFile(Paths.get(url)) ? null : "no such file";
        byte[] toml = Net.get(url, Net.TEXT_LIMIT);
        if (toml == null) return Net.lastProblem;
        if (expectedSha256 != null && !(Release.validSha256(expectedSha256) && ServerUpdateCheck.sha256(toml).equalsIgnoreCase(expectedSha256)))
            return "it doesn't match the checksum the release lists";
        return null;
    }

    /** The paths --clean resets, relative to the instance folder. */
    static List<Path> cleanTargets(Path here) throws IOException {
        List<Path> t = new ArrayList<>();
        for (String n : new String[] { "config", "defaultconfigs", "options.txt" }) t.add(Paths.get(n));
        Path saves = here.resolve("saves");
        if (Files.isDirectory(saves))
            try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves)) {
                for (Path w : worlds) t.add(Paths.get("saves", w.getFileName().toString(), "serverconfig"));
            }
        return t;
    }

    /**
     * Moves the configs aside into .galore-clean-backup/ (restored if the update fails, deleted when it works),
     * so packwiz-installer puts back the pack's versions and mods write fresh defaults.
     */
    static void clean(Path here, Path backup) throws IOException {
        System.out.println("Create Galore --clean: resetting every config to the pack's version (mods and worlds are kept)");
        for (Path rel : cleanTargets(here)) {
            Path from = here.resolve(rel);
            if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS)) continue;
            System.out.println("  resetting " + rel);
            Path to = backup.resolve(rel);
            Files.createDirectories(to.getParent());
            Files.move(from, to);
        }
        // packwiz-installer skips its file check when the pack hasn't changed. Forget the pack's hash so it
        // checks every file and puts back the moved ones; it still knows what it installed, so it keeps
        // removing mods the pack drops and doesn't download unchanged mods again.
        Path manifest = here.resolve("packwiz.json");
        if (Files.isRegularFile(manifest)) {
            Files.createDirectories(backup);
            Files.copy(manifest, backup.resolve("packwiz.json"), StandardCopyOption.REPLACE_EXISTING);
            String s = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
            s = s.replaceFirst("(\"packFileHash\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\")[0-9a-fA-F]+", "$100");
            Files.write(manifest, s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Puts everything in the backup folder back where it was, replacing what a failed run wrote there. */
    static void restore(Path here, Path backup) throws IOException {
        List<Path> rels = new ArrayList<>(cleanTargets(here));
        rels.add(Paths.get("packwiz.json"));
        try (DirectoryStream<Path> worlds = Files.isDirectory(backup.resolve("saves")) ? Files.newDirectoryStream(backup.resolve("saves")) : null) {
            if (worlds != null) for (Path w : worlds) rels.add(Paths.get("saves", w.getFileName().toString(), "serverconfig"));
        }
        for (Path rel : rels) {
            Path saved = backup.resolve(rel);
            if (!Files.exists(saved, LinkOption.NOFOLLOW_LINKS)) continue;
            Path dest = here.resolve(rel);
            delete(dest);
            Files.createDirectories(dest.getParent());
            Files.move(saved, dest);
        }
        delete(backup);
        System.out.println("  your previous configs are back");
    }

    static void delete(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { Files.delete(p); return; }
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException { if (e != null) throw e; Files.delete(d); return FileVisitResult.CONTINUE; }
        });
    }
}
