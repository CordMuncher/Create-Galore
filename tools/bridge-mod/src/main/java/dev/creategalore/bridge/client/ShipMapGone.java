package dev.creategalore.bridge.client;

import com.mojang.logging.LogUtils;
import dev.creategalore.bridge.ShipMapForget;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * The map side of {@link ShipMapForget}: Create - Xaero's Map drops its remembered picture of every ship the server
 * says is gone or only passing through (Discovery flyovers), once the ship is out of view. A ship in view is drawn
 * live as always. Pictures written before this fix are cleared once per world, since the server can no longer say
 * which of those ships are gone; a player's own parked ship comes back the next time it is in view.
 */
public final class ShipMapGone {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String RENDERER = "com.sablexaeromaps.SublevelMapRenderer";
    private static final Path CLEARED_FILE = FMLPaths.CONFIGDIR.get().resolve("create_galore_bridge-ship-map-cleared.txt");
    private static final Map<ResourceLocation, Set<UUID>> GONE = new HashMap<>();
    private static Map<?, ?> cache;
    private static Method drop;
    private static Method currentKey;
    private static Method markHeldChanged;
    private static boolean broken;
    private static Set<String> cleared;

    private ShipMapGone() {
    }

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> GONE.clear());
    }

    public static void onForget(ShipMapForget.Forget forget) {
        Set<UUID> set = GONE.computeIfAbsent(forget.dim(), d -> new HashSet<>());
        if (forget.reset()) set.clear();
        set.addAll(forget.ids());
    }

    /** From the map, before it decides what to keep of the ships out of view: drop the ones the server named. */
    public static void sweep(Set<UUID> tracked) {
        var level = Minecraft.getInstance().level;
        if (level == null || broken) return;
        Set<UUID> gone = GONE.get(level.dimension().location());
        if (gone == null || gone.isEmpty() || !bind()) return;
        try {
            for (Iterator<? extends Map.Entry<?, ?>> it = cache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<?, ?> e = it.next();
                if (e.getKey() instanceof UUID id && gone.contains(id) && (tracked == null || !tracked.contains(id)))
                    drop.invoke(null, it, id, e.getValue(), "gone from the server or a passing flyover (Create Galore)");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.warn("Create Galore: couldn't clear gone ships off the map", e);
        }
    }

    /**
     * True once per world: its ships' pictures from before this fix are left out of the restore. The map writes its
     * file again when the player leaves the world, without them.
     */
    public static boolean clearOldOnce() {
        try {
            if (!bind()) return false;
            String key = (String) currentKey.invoke(null);
            if (key == null || key.isEmpty()) return false;
            if (cleared == null) {
                cleared = new HashSet<>();
                if (Files.exists(CLEARED_FILE)) for (String line : Files.readAllLines(CLEARED_FILE, StandardCharsets.UTF_8))
                    if (!line.isBlank()) cleared.add(line.strip());
            }
            if (!cleared.add(key)) return false;
            Files.write(CLEARED_FILE, List.copyOf(cleared), StandardCharsets.UTF_8);
            markHeldChanged.invoke(null); // written again within a minute, not only when the player leaves
            LOG.info("Create Galore: cleared the ship pictures the map kept from before 0.6.67 for this world, once");
            return true;
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            LOG.warn("Create Galore: couldn't clear the map's old ship pictures", e);
            return false;
        }
    }

    private static boolean bind() {
        if (drop != null) return true;
        if (broken) return false;
        try {
            Class<?> renderer = Class.forName(RENDERER);
            Class<?> texture = Class.forName("com.sablexaeromaps.SublevelMapTexture");
            Field f = renderer.getDeclaredField("CACHE");
            f.setAccessible(true);
            Method d = renderer.getDeclaredMethod("drop", Iterator.class, UUID.class, texture, String.class);
            d.setAccessible(true);
            Method k = Class.forName("com.sablexaeromaps.MapPersistence").getDeclaredMethod("currentKey");
            k.setAccessible(true);
            Method m = renderer.getDeclaredMethod("markHeldChanged");
            m.setAccessible(true);
            markHeldChanged = m;
            cache = (Map<?, ?>) f.get(null);
            currentKey = k;
            drop = d;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.warn("Create Galore: Create - Xaero's Map changed; stale ships can't be cleared off the map", e);
            return false;
        }
    }
}
