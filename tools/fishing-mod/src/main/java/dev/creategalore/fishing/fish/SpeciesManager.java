package dev.creategalore.fishing.fish;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.bait.BaitKind;
import dev.creategalore.fishing.sim.Curve;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.SizeModel;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.data.FishingModifier;
import dev.creategalore.fishing.tide.data.TideFishData;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Loads every fish species from data packs on each (re)load: Tide-format fish files from
 * {@code data/<ns>/fishing/fish/**.json} (Tide's own, Tide Extra Compatibility's and the pack's overrides keep
 * working) and our extension files from {@code data/<ns>/fishing/species/**.json}.
 *
 * <p>Files for fish of mods that aren't installed are skipped quietly, as Tide does. A fish with a {@code parent}
 * doesn't bite itself; its item is treated as the parent species everywhere (catches, processing, the journal).
 */
public final class SpeciesManager extends SimplePreparableReloadListener<SpeciesManager.Raw> {
    private static final FileToIdConverter FISH_FILES = FileToIdConverter.json("fishing/fish");
    private static final FileToIdConverter EXTENSION_FILES = FileToIdConverter.json("fishing/species");

    record Raw(Map<ResourceLocation, JsonElement> fish, Map<ResourceLocation, JsonElement> extensions) {
    }

    private static volatile Map<Item, Species> byItem = Map.of();
    private static volatile List<Species> species = List.of();
    private static volatile List<String> problems = List.of();

    public static void register() {
        FishingCondition.bootstrap();
        FishingModifier.bootstrap();
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new SpeciesManager()));
    }

    /** Every species that can bite, in load order. */
    public static List<Species> all() {
        return species;
    }

    /** The species of a fish item (its own, or its parent's if it was merged into one). */
    public static Optional<Species> of(Item item) {
        return Optional.ofNullable(byItem.get(item));
    }

    public static Optional<Species> of(ItemStack stack) {
        return of(stack.getItem());
    }

    public static Optional<Species> byId(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).flatMap(SpeciesManager::of);
    }

    /** Files that failed to load on the last reload, for the self-test. */
    public static List<String> problems() {
        return problems;
    }

    @Override
    protected Raw prepare(ResourceManager resources, ProfilerFiller profiler) {
        return new Raw(read(FISH_FILES, resources), read(EXTENSION_FILES, resources));
    }

    private static Map<ResourceLocation, JsonElement> read(FileToIdConverter files, ResourceManager resources) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : files.listMatchingResources(resources).entrySet()) {
            try (Reader reader = e.getValue().openAsReader()) {
                out.put(files.fileToId(e.getKey()), JsonParser.parseReader(reader));
            } catch (Exception ex) {
                CreateGaloreFishing.LOG.warn("Couldn't read fishing data {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    @Override
    protected void apply(Raw raw, ResourceManager resources, ProfilerFiller profiler) {
        List<String> trouble = new ArrayList<>();
        Map<Item, TideFishData> originals = new LinkedHashMap<>();
        Map<Item, Item> mergedInto = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : raw.fish().entrySet()) {
            if (!modsPresent(e.getValue())) continue;
            dropMissingBucket(e.getValue());
            DataResult<TideFishData> parsed = TideFishData.CODEC.parse(JsonOps.INSTANCE, e.getValue());
            Optional<TideFishData> data = parsed.result();
            if (data.isEmpty()) {
                String fish = fishId(e.getValue());
                // Data for a mod that isn't here (and doesn't say so in associated_mods): not a problem.
                if (fish != null && !ModList.get().isLoaded(ResourceLocation.parse(fish).getNamespace())) continue;
                String why = parsed.error().map(err -> err.message()).orElse("?");
                trouble.add(e.getKey() + ": " + why);
                continue;
            }
            TideFishData d = data.get();
            Item item = d.fish().value();
            if (item == Items.AIR) continue;
            if (d.parent().isPresent()) {
                mergedInto.put(item, d.parent().get().value());
            } else {
                originals.put(item, d);
                mergedInto.remove(item);
            }
        }

        Map<Item, SpeciesExtension> extensions = new IdentityHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : raw.extensions().entrySet()) {
            DataResult<SpeciesExtension> parsed = SpeciesExtension.CODEC.parse(JsonOps.INSTANCE, e.getValue());
            if (parsed.result().isEmpty()) {
                trouble.add(e.getKey() + ": " + parsed.error().map(err -> err.message()).orElse("?"));
                continue;
            }
            SpeciesExtension ext = parsed.result().get();
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ext.fish());
            if (item.isEmpty()) {
                if (ModList.get().isLoaded(ext.fish().getNamespace())) trouble.add(e.getKey() + ": no item " + ext.fish());
                continue;
            }
            extensions.put(item.get(), ext);
        }

        List<Species> built = new ArrayList<>();
        Map<Item, Species> lookup = new IdentityHashMap<>();
        for (Map.Entry<Item, TideFishData> e : originals.entrySet()) {
            Species s = build(e.getKey(), e.getValue(), extensions.get(e.getKey()));
            built.add(s);
            lookup.put(e.getKey(), s);
        }
        for (Map.Entry<Item, Item> e : mergedInto.entrySet()) {
            Species parent = lookup.get(e.getValue());
            if (parent != null) lookup.putIfAbsent(e.getKey(), parent);
        }
        species = Collections.unmodifiableList(built);
        byItem = Collections.unmodifiableMap(lookup);
        problems = List.copyOf(trouble);
        trouble.forEach(p -> CreateGaloreFishing.LOG.warn("Fishing data problem: {}", p));
        CreateGaloreFishing.LOG.info("Loaded {} fish species ({} items incl. merged ones, {} with our own data)",
                built.size(), lookup.size(), extensions.size());
    }

    private static boolean modsPresent(JsonElement json) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("associated_mods")) return true;
        for (JsonElement mod : json.getAsJsonObject().getAsJsonArray("associated_mods")) {
            if (!ModList.get().isLoaded(mod.getAsString())) return false;
        }
        return true;
    }

    /** Some compatibility data names a bucket item that doesn't exist; the fish is fine without one. */
    private static void dropMissingBucket(JsonElement json) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("bucket")) return;
        ResourceLocation bucket = ResourceLocation.tryParse(json.getAsJsonObject().get("bucket").getAsString());
        if (bucket == null || !BuiltInRegistries.ITEM.containsKey(bucket)) json.getAsJsonObject().remove("bucket");
    }

    private static String fishId(JsonElement json) {
        if (json.isJsonObject() && json.getAsJsonObject().has("fish")) return json.getAsJsonObject().get("fish").getAsString();
        return null;
    }

    /** Combines Tide data, our extension (may be null) and the defaults into one species. */
    static Species build(Item item, TideFishData tide, SpeciesExtension ext) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        FishFight base = SpeciesDefaults.fight(path, tide);
        FishFight fight = base;
        double a = SpeciesDefaults.lengthWeightA(path);
        double b = 3.0;
        Feeding feeding = SpeciesDefaults.feeding(path, tide);
        double wary = SpeciesDefaults.wary(path);
        double fillet = SpeciesDefaults.filletYield(path);
        boolean oily = SpeciesDefaults.oily(path);
        Map<BaitKind, Double> diet = new EnumMap<>(BaitKind.class);
        if (ext != null) {
            if (ext.fight().isPresent()) fight = merge(base, ext.fight().get(), id);
            if (ext.lengthWeight().isPresent()) {
                a = ext.lengthWeight().get().a();
                b = ext.lengthWeight().get().b();
            }
            feeding = ext.feeding().orElse(feeding);
            wary = ext.wary().orElse(wary);
            fillet = ext.fillet().orElse(fillet);
            oily = ext.oily().orElse(oily);
        }
        diet.putAll(feeding.diet());
        if (ext != null) diet.putAll(ext.diet());
        TideFishData.Size size = (ext != null ? ext.size() : Optional.<TideFishData.Size>empty())
                .or(tide::size).orElse(new TideFishData.Size(35, 60, Optional.empty(), 100));
        SizeModel model = new SizeModel(size.typicalLowCm(), size.typicalHighCm(), size.recordHighCm(), a, b);
        return new Species(id, item, tide, fight, model, feeding, Collections.unmodifiableMap(diet), wary, fillet, oily);
    }

    private static FishFight merge(FishFight base, SpeciesExtension.FightData f, ResourceLocation id) {
        Curve profile = base.profile();
        if (f.profile().isPresent()) {
            Either<String, List<List<Double>>> p = f.profile().get();
            Curve given = p.map(Curve::preset, points -> {
                try {
                    double[] xs = new double[points.size()];
                    double[] ys = new double[points.size()];
                    for (int i = 0; i < points.size(); i++) {
                        xs[i] = points.get(i).get(0);
                        ys[i] = points.get(i).get(1);
                    }
                    return new Curve(xs, ys);
                } catch (RuntimeException e) {
                    CreateGaloreFishing.LOG.warn("Bad fight profile for {}: {}", id, e.toString());
                    return null;
                }
            });
            if (given != null) profile = given;
        }
        return new FishFight(f.perKg().orElse(base.fightPerKg()), f.stamina().orElse(base.stamina()),
                f.speed().orElse(base.speed()), profile, f.runs().orElse(base.runs()), f.jumps().orElse(base.jumps()),
                f.charges().orElse(base.charges()), f.toothy().orElse(base.toothy()));
    }

    /** For tools: every item that maps to a species, merged ones included. */
    public static Collection<Item> items() {
        return byItem.keySet();
    }
}
