import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-launch updater for a Create Galore instance. Runs from the instance's minecraft folder:
 *
 *   "$INST_JAVA" -jar create-galore-updater.jar            soft update: only changed mods and configs
 *   "$INST_JAVA" -jar create-galore-updater.jar --clean    also resets every config to the pack's version
 *
 * The update itself is packwiz-installer (pinned, shipped next to this jar). --clean deletes config/,
 * defaultconfigs/, options.txt and each world's serverconfig/ first, so the pack's files and the mods'
 * defaults come back. Mods, Minecraft, NeoForge and the worlds themselves are never deleted.
 */
public final class CreateGaloreUpdater {
    static final String PACK_URL = "https://raw.githubusercontent.com/emresahin0906-code/Create-Galore/main-6fi2ef/pack/pack.toml";

    public static void main(String[] args) throws Exception {
        boolean clean = false;
        String url = PACK_URL;
        List<String> passOn = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--clean")) clean = true;
            else if (a.startsWith("--")) passOn.add(a);
            else url = a;
        }
        Path here = Paths.get("").toAbsolutePath();
        if (!Files.isDirectory(here.resolve("mods")) || !Files.isRegularFile(here.resolve("packwiz-installer-bootstrap.jar"))) {
            System.err.println("Not a Create Galore instance folder (needs mods/ and packwiz-installer-bootstrap.jar): " + here);
            System.exit(2);
        }
        if (clean) clean(here);

        String java = Paths.get(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(java); cmd.add("-jar"); cmd.add("packwiz-installer-bootstrap.jar"); cmd.add("--bootstrap-no-update");
        cmd.addAll(passOn);
        cmd.add(url);
        System.exit(new ProcessBuilder(cmd).inheritIO().start().waitFor());
    }

    static void clean(Path here) throws IOException {
        System.out.println("Create Galore --clean: resetting every config to the pack's version (mods and worlds are kept)");
        delete(here.resolve("config"));
        delete(here.resolve("defaultconfigs"));
        delete(here.resolve("options.txt"));
        Path saves = here.resolve("saves");
        if (Files.isDirectory(saves))
            try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves)) {
                for (Path w : worlds) delete(w.resolve("serverconfig"));
            }
        // packwiz-installer skips its file check when the pack hasn't changed. Forget the pack's hash so it
        // checks every file and puts back the deleted ones; it still knows what it installed, so it keeps
        // removing mods the pack drops and doesn't download unchanged mods again.
        Path manifest = here.resolve("packwiz.json");
        if (Files.isRegularFile(manifest)) {
            String s = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
            s = s.replaceFirst("(\"packFileHash\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\")[0-9a-fA-F]+", "$100");
            Files.write(manifest, s.getBytes(StandardCharsets.UTF_8));
        }
    }

    static void delete(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        System.out.println("  deleting " + p.getFileName() + (p.getParent() != null && p.getParent().getFileName() != null && !p.getParent().equals(Paths.get("").toAbsolutePath()) ? " in " + p.getParent().getFileName() : ""));
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { Files.delete(p); return; }
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException { Files.delete(f); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException { if (e != null) throw e; Files.delete(d); return FileVisitResult.CONTINUE; }
        });
    }
}
