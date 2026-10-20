package dev.creategalore.fishing.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tide.data.fishing.DisplayData;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Which items are drawn as their fish (any mod's): an item whose fish file names a display mob, or that has a water
 * mob of the same id (vanilla, Tide, Aquaculture and Hybrid Aquatic fish all do). Worked out at startup, before any
 * world data, from the fish files this mod ships (Tide's and Tide Extra Compatibility's, which cover every fish mod in
 * the pack) and the mob registry.
 *
 * <p>Those items get {@link FishItemRenderer}: their baked models are wrapped so every view but the sprite's own goes
 * through it, and it falls back to the original sprite whenever it can't draw the fish. Items another mod already
 * draws itself (its own item renderer) are left alone.
 */
final class FishItems {
    private FishItems() {
    }

    /** Fish item to the mob that draws it, from the shipped fish files and same-id mobs. */
    private static final Map<Item, ResourceLocation> MOBS = new IdentityHashMap<>();
    /** The items' own baked models, for the fallback. */
    private static final Map<Item, BakedModel> SPRITES = new IdentityHashMap<>();

    static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.LOWEST, (RegisterClientExtensionsEvent event) -> {
            collect();
            Item[] items = MOBS.keySet().stream().filter(i -> !event.isItemRegistered(i)).toArray(Item[]::new);
            MOBS.keySet().retainAll(java.util.Set.of(items));
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return FishItemRenderer.get();
                }
            }, items);
            CreateGaloreFishing.LOG.info("Drawing {} fish items as their 3D models", items.length);
            if (Boolean.getBoolean("cgfish.debug")) {
                CreateGaloreFishing.LOG.info("[cgfish] fish items: {}", java.util.Arrays.stream(items)
                        .map(i -> BuiltInRegistries.ITEM.getKey(i) + "=" + MOBS.get(i)).toList());
            }
        });
        modBus.addListener(EventPriority.LOWEST, (ModelEvent.ModifyBakingResult event) -> {
            SPRITES.clear();
            for (Item item : MOBS.keySet()) {
                ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
                BakedModel model = event.getModels().get(key);
                if (model == null || model.isCustomRenderer()) continue;
                SPRITES.put(item, model);
                event.getModels().put(key, new FishItemRenderer.Model(model));
            }
        });
        modBus.addListener((RegisterItemDecorationsEvent event) ->
                MOBS.keySet().forEach(item -> event.register(item, FishItemRenderer::decorate)));
    }

    /** True if {@code item} is drawn as a fish (its model is wrapped). */
    static boolean drawn(Item item) {
        return SPRITES.containsKey(item);
    }

    /** The item's own model (its sprite), or null if it isn't one of ours. */
    static BakedModel sprite(Item item) {
        return SPRITES.get(item);
    }

    /**
     * The mob that draws this fish: the one its synced fish data names, else the one found at startup. Empty for an
     * item that isn't a fish.
     */
    static Optional<ResourceLocation> mob(ItemStack stack) {
        Optional<ResourceLocation> named = FishData.get(stack).flatMap(FishData::display)
                .map(DisplayData::entityKey).map(k -> k.location());
        return named.isPresent() ? named : Optional.ofNullable(MOBS.get(stack.getItem()));
    }

    private static void collect() {
        MOBS.clear();
        // 1. Every fish file this mod ships: its fish item and, if named, its display mob.
        Map<ResourceLocation, ResourceLocation> files = new HashMap<>();
        try {
            Path data = ModList.get().getModFileById(CreateGaloreFishing.ID).getFile().findResource("data");
            try (Stream<Path> paths = Files.walk(data)) {
                paths.filter(p -> p.toString().endsWith(".json") && p.toString().replace('\\', '/').contains("/fishing/fish/"))
                        .forEach(p -> read(p, files));
            }
        } catch (IOException | RuntimeException e) {
            CreateGaloreFishing.LOG.warn("Couldn't read the shipped fish files for 3D fish items: {}", e.toString());
        }
        files.forEach((fish, mob) -> BuiltInRegistries.ITEM.getOptional(fish)
                .filter(item -> BuiltInRegistries.ENTITY_TYPE.containsKey(mob))
                .ifPresent(item -> MOBS.put(item, mob)));
        // 2. Items with a water mob of the same id (fish the files don't cover).
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).filter(FishItems::waterMob)
                    .ifPresent(type -> MOBS.putIfAbsent(item, id));
        }
    }

    private static boolean waterMob(EntityType<?> type) {
        MobCategory c = type.getCategory();
        return c == MobCategory.WATER_AMBIENT || c == MobCategory.WATER_CREATURE || c == MobCategory.UNDERGROUND_WATER_CREATURE;
    }

    private static void read(Path file, Map<ResourceLocation, ResourceLocation> out) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) return;
            JsonObject o = json.getAsJsonObject();
            // A fish merged into another is still a fish to draw (and with the aliases its parent may be itself).
            if (!o.has("fish")) return;
            ResourceLocation fish = ResourceLocation.tryParse(o.get("fish").getAsString());
            if (fish == null) return;
            ResourceLocation mob = fish;
            if (o.has("display_data") && o.getAsJsonObject("display_data").has("entity")) {
                ResourceLocation named = ResourceLocation.tryParse(o.getAsJsonObject("display_data").get("entity").getAsString());
                if (named != null) mob = named;
            }
            out.put(fish, mob);
        } catch (IOException | RuntimeException e) {
            // A broken file is the server's problem to report; it just isn't drawn in 3D.
        }
    }
}
