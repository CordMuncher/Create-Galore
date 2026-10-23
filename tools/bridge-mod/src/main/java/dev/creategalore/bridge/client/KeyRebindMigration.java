package dev.creategalore.bridge.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the pack's key rebinds to instances that already had an options.txt. The pack ships options.txt as a
 * preserved file (the updater never overwrites it, so players keep their own keys), which means rebinds added in a
 * later pack version never reach an instance that was set up before it. Pack 0.2.1 moved nine keys off clashing
 * defaults (Curios vs Alex's Caves on G, Aether vs TFMG on I, TFMG and Twilight Forest on V, C, B and Z).
 *
 * Runs once per instance while the mods load, before Minecraft reads options.txt. A key still on the mod's own default
 * (or not saved yet) gets the pack's key; a key the player changed is left alone. A marker file in config/ records
 * that it ran (Minecraft drops unknown lines from options.txt), so a player who later puts a key back on its old
 * default keeps it there.
 */
public final class KeyRebindMigration {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final String MARKER = "create_galore_bridge-keys-0.2.1.done";

    /** options.txt key name -> {the mod's default, the pack's key since 0.2.1}. */
    private static final Map<String, String[]> REBINDS = new LinkedHashMap<>();

    static {
        REBINDS.put("key_key.special_ability", new String[] {"key.keyboard.g", "key.keyboard.apostrophe"});
        REBINDS.put("key_key.aether.open_accessories.desc", new String[] {"key.keyboard.i", "key.keyboard.unknown"});
        REBINDS.put("key_tfmg.keyinfo.engine_start", new String[] {"key.keyboard.i", "key.keyboard.minus"});
        REBINDS.put("key_tfmg.keyinfo.transmission_shift_up", new String[] {"key.keyboard.v", "key.keyboard.right"});
        REBINDS.put("key_tfmg.keyinfo.transmission_shift_down", new String[] {"key.keyboard.c", "key.keyboard.left"});
        REBINDS.put("key_tfmg.keyinfo.custom_button", new String[] {"key.keyboard.b", "key.keyboard.f6"});
        REBINDS.put("key_key.twilightforest.zoom", new String[] {"key.keyboard.z", "key.keyboard.f7"});
        REBINDS.put("key_key.twilightforest.swap_hotbar", new String[] {"key.keyboard.v", "key.keyboard.f9"});
        REBINDS.put("key_key.twilightforest.item_display_map_cycle", new String[] {"key.keyboard.c", "key.keyboard.f12"});
    }

    private KeyRebindMigration() {
    }

    /**
     * Pack 0.4.40 replaced the three accessory screens with one wear screen on G (Curios' old key): Curios' and
     * Accessories' keys come off their defaults so G isn't taken twice (bound again, they open the wear screen too).
     */
    private static final String WEAR_MARKER = "create_galore_bridge-keys-0.4.40.done";
    private static final Map<String, String[]> WEAR_REBINDS = new LinkedHashMap<>();

    static {
        WEAR_REBINDS.put("key_key.curios.open.desc", new String[] {"key.keyboard.g", "key.keyboard.unknown"});
        WEAR_REBINDS.put("key_accessories.key.open_accessories_screen", new String[] {"key.keyboard.comma", "key.keyboard.unknown"});
    }

    public static void run() {
        run(MARKER, REBINDS, "the pack 0.2.1 key rebinds");
        run(WEAR_MARKER, WEAR_REBINDS, "the pack 0.4.40 key rebinds (one accessory screen on G)");
    }

    private static void run(String markerName, Map<String, String[]> rebinds, String what) {
        Path file = FMLPaths.GAMEDIR.get().resolve("options.txt");
        Path marker = FMLPaths.CONFIGDIR.get().resolve(markerName);
        try {
            if (Files.exists(marker)) {
                return;
            }
            // No options.txt: a brand-new instance without the pack's file. Minecraft writes the mod defaults, nothing to move.
            if (!Files.exists(file)) {
                Files.writeString(marker, "");
                return;
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            Map<String, String[]> pending = new LinkedHashMap<>(rebinds);
            List<String> moved = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String[] rebind = pending.remove(line.substring(0, colon));
                if (rebind != null && line.substring(colon + 1).equals(rebind[0])) {
                    lines.set(i, line.substring(0, colon + 1) + rebind[1]);
                    moved.add(line.substring(4, colon));
                }
            }
            // Keys never saved are still on the mod default.
            pending.forEach((key, rebind) -> {
                lines.add(key + ":" + rebind[1]);
                moved.add(key.substring(4));
            });
            Files.write(file, lines, StandardCharsets.UTF_8);
            Files.writeString(marker, "Create Galore Bridge moved " + what + " into options.txt once. Delete this file to run it again.\n");
            if (!moved.isEmpty()) {
                LOG.info("Moved {} key(s) off clashing mod defaults to the pack's keys: {}", moved.size(), moved);
            }
        } catch (IOException e) {
            LOG.warn("Couldn't update key rebinds in options.txt", e);
        }
    }
}
