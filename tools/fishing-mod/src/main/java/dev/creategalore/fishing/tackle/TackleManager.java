package dev.creategalore.fishing.tackle;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.net.FishingNet;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ratings of every tackle item, from {@code data/<ns>/fishing/tackle/**.json}. Loaded on the server with the
 * data packs and sent to each client on join and on {@code /reload}, so tooltips and the Tackle Bench show the
 * same numbers the fight uses.
 */
public final class TackleManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final FileToIdConverter FILES = FileToIdConverter.json("fishing/tackle");

    private static volatile Map<Item, TacklePart> parts = Map.of();
    /** The JSON each entry came from, kept to send to clients. */
    private static volatile List<String> sources = List.of();
    private static volatile List<String> problems = List.of();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new TackleManager()));
        NeoForge.EVENT_BUS.addListener(TackleManager::onSync);
    }

    public static Optional<TacklePart> of(Item item) {
        return Optional.ofNullable(parts.get(item));
    }

    public static Optional<TacklePart> of(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : of(stack.getItem());
    }

    /** The part of the given type, if the item is one. */
    public static <T extends TacklePart> Optional<T> of(ItemStack stack, Class<T> type) {
        return of(stack).filter(type::isInstance).map(type::cast);
    }

    public static boolean is(ItemStack stack, TacklePart.Kind kind) {
        return of(stack).map(p -> p.kind() == kind).orElse(false);
    }

    public static Map<Item, TacklePart> all() {
        return parts;
    }

    public static List<String> problems() {
        return problems;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : FILES.listMatchingResources(resources).entrySet()) {
            try (Reader reader = e.getValue().openAsReader()) {
                out.put(FILES.fileToId(e.getKey()), JsonParser.parseReader(reader));
            } catch (Exception ex) {
                CreateGaloreFishing.LOG.warn("Couldn't read tackle data {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<String> json = new ArrayList<>();
        files.forEach((file, element) -> json.add(element.toString()));
        load(json, true);
    }

    /**
     * Parses the given JSON entries and makes them the current table. The server calls this after a reload; the
     * client with what the server sent.
     */
    static void load(List<String> entries, boolean server) {
        Map<Item, TacklePart> loaded = new IdentityHashMap<>();
        List<String> kept = new ArrayList<>();
        List<String> trouble = new ArrayList<>();
        for (String text : entries) {
            JsonElement json = JsonParser.parseString(text);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("item")) {
                trouble.add("no \"item\" in " + text);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(json.getAsJsonObject().get("item").getAsString());
            Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) {
                // Tackle for a mod that isn't installed (Hybrid Aquatic's hooks without Hybrid Aquatic).
                if (id != null && !ModList.get().isLoaded(id.getNamespace())) continue;
                trouble.add("no item " + id);
                continue;
            }
            var parsed = TacklePart.CODEC.parse(JsonOps.INSTANCE, json);
            if (parsed.result().isEmpty()) {
                trouble.add(id + ": " + parsed.error().map(e -> e.message()).orElse("?"));
                continue;
            }
            loaded.put(item.get(), parsed.result().get());
            kept.add(text);
        }
        parts = Collections.unmodifiableMap(loaded);
        sources = List.copyOf(kept);
        problems = List.copyOf(trouble);
        trouble.forEach(p -> CreateGaloreFishing.LOG.warn("Tackle data problem: {}", p));
        if (server) CreateGaloreFishing.LOG.info("Loaded {} tackle items", loaded.size());
    }

    private static void onSync(OnDatapackSyncEvent event) {
        Sync payload = new Sync(sources);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    /** Server to client: the whole tackle table as JSON entries. */
    public record Sync(List<String> entries) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(CreateGaloreFishing.id("tackle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::entries, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }

        /** On the client (integrated servers already have the table). */
        public static void handle(Sync payload) {
            if (FishingNet.isIntegratedServerHost()) return;
            load(payload.entries(), false);
        }
    }
}
