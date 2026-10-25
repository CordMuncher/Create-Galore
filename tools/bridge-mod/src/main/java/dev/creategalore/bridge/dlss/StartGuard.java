package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an engine off after it took the game down while starting. A file in create-galore-dlss/ marks the engine as
 * starting from just before it starts (or restarts at a new size) until it has run a couple of seconds of frames;
 * a mark still there at the next launch means the game died in between, and the engine stays off until the player
 * picks Try again on the settings screen.
 */
final class StartGuard {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    /** Frames an engine runs before its start counts as safe. */
    private static final int SAFE_FRAMES = 120;

    private final String name;
    private final Path starting;
    private final Path crashed;
    private boolean checked;
    private int frames = -1;

    StartGuard(Path folder, String name) {
        this.name = name;
        starting = folder.resolve(name.toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + "-starting.txt");
        crashed = folder.resolve(starting.getFileName().toString().replace("-starting", "-crashed"));
    }

    /** True if the game died while this engine started, this launch or an earlier one. */
    boolean crashed() {
        if (!checked) {
            checked = true;
            if (Files.exists(starting)) {
                try {
                    Files.move(starting, crashed, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOG.warn("DLAA: could not move {}: {}", starting, e.toString());
                }
                LOG.warn("DLAA: the game closed while {} was starting last time, so {} stays off (settings screen: Try again)", name, name);
            }
        }
        return Files.exists(crashed);
    }

    /** The engine is about to start (or restart): marked until it has run {@link #SAFE_FRAMES} frames. */
    void starting() {
        try {
            Files.createDirectories(starting.getParent());
            Files.writeString(starting, name + " started at " + java.time.Instant.now() + " and had not run "
                    + SAFE_FRAMES + " frames yet\n");
            frames = 0;
        } catch (IOException e) {
            LOG.warn("DLAA: could not write {}: {}", starting, e.toString());
        }
    }

    /** A frame ran: after enough of them the start counts as safe. */
    void ran() {
        if (frames >= 0 && ++frames >= SAFE_FRAMES) {
            stopped();
        }
    }

    /** The engine stopped on its own terms (or the game is closing): not a crash. */
    void stopped() {
        frames = -1;
        try {
            Files.deleteIfExists(starting);
        } catch (IOException e) {
            LOG.warn("DLAA: could not delete {}: {}", starting, e.toString());
        }
    }

    /** The player asked to try again. */
    void clear() {
        try {
            Files.deleteIfExists(crashed);
        } catch (IOException e) {
            LOG.warn("DLAA: could not delete {}: {}", crashed, e.toString());
        }
    }
}
