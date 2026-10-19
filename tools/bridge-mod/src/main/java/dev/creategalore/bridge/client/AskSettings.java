package dev.creategalore.bridge.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** The player's own choice for the Ask the Book screen (the quill sound), in config/create_galore_bridge-ask.properties. */
final class AskSettings {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-ask.properties");
    private static Boolean sound;

    private AskSettings() {
    }

    static boolean sound() {
        if (sound == null) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                // not saved yet
            }
            sound = !"false".equalsIgnoreCase(p.getProperty("sound", "true").trim());
        }
        return sound;
    }

    static void setSound(boolean on) {
        sound = on;
        Properties p = new Properties();
        p.setProperty("sound", Boolean.toString(on));
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            p.store(w, "Create Galore Bridge: Ask the Book");
        } catch (IOException e) {
            // stays for this session
        }
    }
}
