package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings screen's "Find on this PC": copies nvngx_dlss.dll from where NVIDIA's own software keeps it, so a player
 * who has it on their PC needn't download it. The NVIDIA app and the driver's updater keep the DLSS versions they
 * downloaded (for DLSS Override, or when they update a game's DLSS) as
 * %ProgramData%\NVIDIA\NGX\models\dlss\versions\NUMBER\files\NAME.bin, each one an nvngx_dlss.dll under another name
 * (dlssd and dlssg beside it are Ray Reconstruction and Frame Generation). It looks only there and only when the button
 * is pressed, takes the newest version and keeps a copy in the DLSS folder, so NVIDIA updating or removing theirs later
 * changes nothing here. A DLL the game has loaded can't be replaced: the copy then waits beside it as
 * nvngx_dlss.dll.new and takes over at the next start ({@link #applyPending()}).
 *
 * The same press copies DLSS Frame Generation's nvngx_dlssg.dll from the dlssg folder beside it the same way. The game
 * never reads NVIDIA's folder by itself: NVIDIA updates those files often, and the copies here change only when the
 * player asks (or puts their own in the DLSS folder).
 */
final class DlssFinder {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    static final String FG_DLL = "nvngx_dlssg.dll";

    private DlssFinder() {}

    /** What pressing the button did, for the screen; {@code changed} if a DLL was put in place now. */
    record Result(String message, boolean changed) {}

    /** NVIDIA's folder of DLSS versions. */
    static Path nvidiaFolder() {
        return nvidiaFolder("dlss");
    }

    /** NVIDIA's folder of versions of one model: dlss, dlssg (frame generation), dlssd. */
    private static Path nvidiaFolder(String model) {
        String programData = System.getenv("ProgramData");
        return Path.of(programData != null && !programData.isBlank() ? programData : "C:\\ProgramData")
                .resolve("NVIDIA").resolve("NGX").resolve("models").resolve(model).resolve("versions");
    }

    /** What one model's copy came to: NVIDIA's newest version (null: none there) and what happened to it. */
    private record Copy(String version, String have, boolean copied, boolean pending, String error) {
        /** For the screen, one sentence: "DLSS 310.9.16: already up to date." and the like. */
        String text(String label) {
            return version == null ? label + ": not in NVIDIA's folder."
                    : error != null ? label + " " + version + ": couldn't be copied (" + error + ")."
                    : pending ? label + " " + version + ": copied from NVIDIA's folder, used after a restart."
                    : copied ? label + " " + version + ": copied from NVIDIA's folder."
                    : compare(have, version) == 0 ? label + " " + version + ": already up to date."
                    : label + ": yours (" + have + ") is newer than NVIDIA's (" + version + ").";
        }
    }

    /**
     * Pressing "Find on this PC": copies the newest nvngx_dlss.dll and nvngx_dlssg.dll from NVIDIA's folders into the
     * DLSS folder, each unless the one there is as new. {@code changed} if nvngx_dlss.dll was put in place now.
     */
    static Result find() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return new Result("DLSS runs only on Windows.", false);
        }
        // An NVIDIA DLL from DLSS 2 on, and Frame Generation from 3 on (NGX checks NVIDIA's signature when it loads them).
        Copy dlss = copyNewest("dlss", Dlss.DLL, 2);
        Copy dlssg = copyNewest("dlssg", FG_DLL, 3);
        if (dlss.version() == null && dlssg.version() == null) {
            return new Result("Nothing found in NVIDIA's folder (" + nvidiaFolder().getParent() + "). The NVIDIA app puts DLSS there"
                    + " once it has updated DLSS for a game. Get it from NVIDIA instead.", false);
        }
        return new Result(dlss.text("DLSS") + " " + dlssg.text("Frame generation"), dlss.copied() && !dlss.pending());
    }

    /** The newest {@code name} of NVIDIA's {@code model} folder, from version {@code major} on, copied into the DLSS folder. */
    private static Copy copyNewest(String model, String name, int major) {
        Path versions = nvidiaFolder(model);
        Path best = null;
        String bestVersion = null;
        for (Path file : candidates(versions)) {
            String version = DllVersion.read(file);
            if (version != null && major(version) >= major && DllVersion.hasString(file, "NVIDIA")
                    && (bestVersion == null || compare(version, bestVersion) > 0)) {
                best = file;
                bestVersion = version;
            }
        }
        Path folder = Dlss.dllFolder();
        Path dll = folder.resolve(name);
        String have = Files.isRegularFile(dll) ? DllVersion.read(dll) : null;
        if (best == null || have != null && compare(have, bestVersion) >= 0) {
            return new Copy(bestVersion, have, false, false, null);
        }
        try {
            Files.createDirectories(folder);
            Path pending = folder.resolve(name + ".new");
            Files.copy(best, pending, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(pending, dll, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (have == null) {
                    throw e;
                }
                // The game has the old one loaded.
                LOG.info("DLAA: copied {} {} from {}; it replaces {} at the next start", name, bestVersion, best, have);
                return new Copy(bestVersion, have, true, true, null);
            }
        } catch (IOException e) {
            LOG.warn("DLAA: could not copy {} into {}: {}", best, folder, e.toString());
            return new Copy(bestVersion, have, false, false, e.getMessage());
        }
        LOG.info("DLAA: copied {} {} from {} into {}", name, bestVersion, best, folder);
        return new Copy(bestVersion, have, true, false, null);
    }

    /** At mod construction, before DLSS loads: a copy that waited for the old DLL to be let go takes its place. */
    static void applyPending() {
        Path folder = Dlss.dllFolder();
        for (String name : new String[] {Dlss.DLL, FG_DLL}) {
            Path pending = folder.resolve(name + ".new");
            if (Files.isRegularFile(pending)) {
                try {
                    Files.move(pending, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    LOG.info("DLAA: {} {} from NVIDIA's folder is in place", name, DllVersion.read(folder.resolve(name)));
                } catch (IOException e) {
                    LOG.warn("DLAA: could not put {} in place: {}", pending, e.toString());
                }
            }
        }
    }

    /** Every file in each version's files folder. */
    private static List<Path> candidates(Path versions) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(versions)) {
            return files;
        }
        try (DirectoryStream<Path> numbers = Files.newDirectoryStream(versions)) {
            for (Path number : numbers) {
                Path dir = number.resolve("files");
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (DirectoryStream<Path> inside = Files.newDirectoryStream(dir, "*.{bin,dll}")) {
                    for (Path file : inside) {
                        if (Files.isRegularFile(file)) {
                            files.add(file);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA: could not look through {}: {}", versions, e.toString());
        }
        return files;
    }

    private static int major(String version) {
        return parts(version)[0];
    }

    /** Compares versions like 310.9.16 part by part. */
    static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        String[] split = version.split("\\.");
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i].trim());
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }
}
