package dev.creategalore.bridge.client;

import com.simibubi.create.CreateClient;
import dev.creategalore.bridge.SharedSchematics;
import dev.creategalore.bridge.mixin.SchematicHandlerAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The builds of the Schematics this player holds, as the server has them ({@link SharedSchematics}), so a Schematic
 * someone else made shows and places the same as for its maker. Asked for the first time Create loads a paper's
 * build; until it arrives the paper shows nothing, then Create sets it up again. Kept until leaving the server.
 */
public final class SharedSchematicCache {
    private static final byte[] NONE = new byte[0];
    /** Builds by key ({@link SharedSchematics#key}); {@link #NONE}: the server has none, Create reads the local file. */
    private static final Map<String, byte[]> builds = new HashMap<>();
    private static final Map<String, byte[]> arriving = new HashMap<>();
    private static final Set<String> asked = new HashSet<>();
    /** The key of the Schematic Create's handler shows, to set it up again when another paper with the same file name comes in hand. */
    private static String handlerKey;

    private SharedSchematicCache() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(SharedSchematicCache::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> Minecraft.getInstance().execute(() -> {
            builds.clear();
            arriving.clear();
            asked.clear();
            handlerKey = null;
        }));
    }

    /** For {@code SchematicItem.loadSchematic} on the client: the server's build, empty while asked for, null for Create's own way. */
    public static StructureTemplate load(Level level, ItemStack stack) {
        String key = SharedSchematics.key(stack);
        if (key == null) return null;
        byte[] bytes = builds.get(key);
        if (bytes == NONE) return null;
        if (bytes != null) return SharedSchematics.parse(level, bytes);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(SharedSchematics.Request.TYPE)) return null;
        if (asked.add(key)) PacketDistributor.sendToServer(new SharedSchematics.Request(key));
        return new StructureTemplate();
    }

    /** True while the build of this paper is on its way, so nothing says it is missing yet. */
    public static boolean waiting(ItemStack stack) {
        String key = SharedSchematics.key(stack);
        return key != null && asked.contains(key) && !builds.containsKey(key);
    }

    public static void onPart(SharedSchematics.Part part) {
        if (part.total() <= 0) {
            done(part.key(), NONE);
            return;
        }
        if (part.total() > SharedSchematics.MAX_BYTES) return;
        byte[] all = part.offset() == 0 ? new byte[part.total()] : arriving.get(part.key());
        if (all == null || all.length != part.total() || part.offset() + part.bytes().length > all.length) return;
        System.arraycopy(part.bytes(), 0, all, part.offset(), part.bytes().length);
        if (part.offset() + part.bytes().length < all.length) {
            arriving.put(part.key(), all);
            return;
        }
        arriving.remove(part.key());
        if (!part.hash().isEmpty() && !part.hash().equals(part.key())) builds.put(part.hash(), all);
        done(part.key(), all);
    }

    private static void done(String key, byte[] bytes) {
        builds.put(key, bytes);
        // Whatever showed this paper empty loads it again.
        if (key.equals(handlerKey)) ((SchematicHandlerAccessor) CreateClient.SCHEMATIC_HANDLER).create_galore_bridge$setDisplayedSchematic(null);
        SchematicTrace.reload(key);
    }

    /**
     * Create sets a held Schematic up again only when its file name changes, so two papers with the same file name
     * (from two players) would show the first one's build for both.
     */
    private static void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        ItemStack active = CreateClient.SCHEMATIC_HANDLER.isActive() ? CreateClient.SCHEMATIC_HANDLER.getActiveSchematicItem() : null;
        String key = active == null ? null : SharedSchematics.key(active);
        if (Objects.equals(key, handlerKey)) return;
        // The same build under another key (a paper from before that the server just marked) needs no reload.
        boolean same = handlerKey != null && key != null && builds.get(handlerKey) != null && builds.get(handlerKey) == builds.get(key);
        if (handlerKey != null && key != null && !same) ((SchematicHandlerAccessor) CreateClient.SCHEMATIC_HANDLER).create_galore_bridge$setDisplayedSchematic(null);
        handlerKey = key;
    }
}
