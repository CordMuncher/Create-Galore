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
 * Frame generation (testing) takes nvngx_dlssg.dll the same way from the dlssg folder beside it, by itself when it
 * starts ({@link #findFrameGeneration()}).
 */
final class DlssFinder {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final String PENDING = Dlss.DLL + ".new";
    static final String FG_DLL = "nvngx_dlssg.dll";
    private static final String FG_PENDING = FG_DLL + ".new";

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

    /**
     * Copies the newest nvngx_dlssg.dll from NVIDIA's dlssg folder into the DLSS folder unless the one there is as new,
     * or waits as nvngx_dlssg.dll.new if the game has that one loaded. What it did, for the log.
     */
    static String findFrameGeneration() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return "not Windows";
        }
        Path versions = nvidiaFolder("dlssg");
        Path best = null;
        String bestVersion = null;
        for (Path file : candidates(versions)) {
            String version = DllVersion.read(file);
            // DLSS Frame Generation started at 3.0; Streamline's NGX checks NVIDIA's signature when it loads it.
            if (version != null && major(version) >= 3 && DllVersion.hasString(file, "NVIDIA")
                    && (bestVersion == null || compare(version, bestVersion) > 0)) {
                best = file;
                bestVersion = version;
            }
        }
        Path folder = Dlss.dllFolder();
        Path dll = folder.resolve(FG_DLL);
        String have = Files.isRegularFile(dll) ? DllVersion.read(dll) : null;
        if (best == null) {
            return have != null ? FG_DLL + " " + have + " (none in NVIDIA's folder " + versions + ")"
                    : "no " + FG_DLL + " in " + folder + " or NVIDIA's folder " + versions;
        }
        if (have != null && compare(have, bestVersion) >= 0) {
            return FG_DLL + " " + have + " (NVIDIA's folder has " + bestVersion + ")";
        }
        try {
            Files.createDirectories(folder);
            Path pending = folder.resolve(FG_PENDING);
            Files.copy(best, pending, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(pending, dll, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (have == null) {
                    throw e;
                }
                return FG_DLL + " " + have + "; copied " + bestVersion + " from " + best + ", in place at the next start";
            }
        } catch (IOException e) {
            return (have != null ? FG_DLL + " " + have : "no " + FG_DLL) + "; could not copy " + bestVersion + " from " + best
                    + ": " + e.getMessage();
        }
        return FG_DLL + " " + bestVersion + ", copied from " + best;
    }

    static Result find() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return new Result("DLSS runs only on Windows.", false);
        }
        Path versions = nvidiaFolder();
        Path best = null;
        String bestVersion = null;
        for (Path file : candidates(versions)) {
            String version = DllVersion.read(file);
            // An NVIDIA DLL from DLSS 2 on (NGX itself checks NVIDIA's signature when it loads it).
            if (version != null && major(version) >= 2 && DllVersion.hasString(file, "NVIDIA")
                    && (bestVersion == null || compare(version, bestVersion) > 0)) {
                best = file;
                bestVersion = version;
            }
        }
        if (best == null) {
            return new Result("No DLSS found in NVIDIA's folder (" + versions.getParent() + "). The NVIDIA app puts it there once it"
                    + " has updated DLSS for a game. Get it from NVIDIA instead.", false);
        }
        Path folder = Dlss.dllFolder();
        Path dll = folder.resolve(Dlss.DLL);
        String have = Files.isRegularFile(dll) ? DllVersion.read(dll) : null;
        if (have != null && compare(have, bestVersion) >= 0) {
            return new Result("Found DLSS " + bestVersion + " in NVIDIA's folder; yours (" + have + ") is as new, so it stays.", false);
        }
        try {
            Files.createDirectories(folder);
            Path pending = folder.resolve(PENDING);
            Files.copy(best, pending, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(pending, dll, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                if (have == null) {
                    throw e;
                }
                // The game has the old one loaded.
                LOG.info("DLAA: copied DLSS {} from {}; it replaces {} at the next start", bestVersion, best, have);
                return new Result("Copied DLSS " + bestVersion + " from NVIDIA's folder. It replaces " + have
                        + " when you restart the game.", false);
            }
        } catch (IOException e) {
            LOG.warn("DLAA: could not copy {} into {}: {}", best, folder, e.toString());
            return new Result("Found DLSS " + bestVersion + " in NVIDIA's folder but could not copy it: " + e.getMessage(), false);
        }
        LOG.info("DLAA: copied DLSS {} from {} into {}", bestVersion, best, folder);
        return new Result("Copied DLSS " + bestVersion + " from NVIDIA's folder into the DLSS folder.", true);
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
