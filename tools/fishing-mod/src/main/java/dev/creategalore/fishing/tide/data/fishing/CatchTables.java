package dev.creategalore.fishing.tide.data.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingModifier;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Tide's crate and junk/treasure tables ({@code fishing/crates}, {@code fishing/loot}), loaded from data in Tide's
 * format so Tide's own files and other mods' (Hybrid Aquatic ships crates) keep working. The bite roll asks here for
 * a crate and for which junk or treasure table applies at the spot.
 */
public final class CatchTables extends SimplePreparableReloadListener<CatchTables.Raw> {
    private static final FileToIdConverter CRATE_FILES = FileToIdConverter.json("fishing/crates");
    private static final FileToIdConverter LOOT_FILES = FileToIdConverter.json("fishing/loot");

    private static volatile List<CrateData> crates = List.of();
    private static volatile List<FishingLootData> loot = List.of();

    record Raw(Map<ResourceLocation, JsonElement> crates, Map<ResourceLocation, JsonElement> loot) {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new CatchTables()));
    }

    public static List<CrateData> crates() {
        return crates;
    }

    public static List<FishingLootData> loot() {
        return loot;
    }

    /** Weight of an entry at the spot: 0 if any condition fails, else weight + quality x luck through the modifiers. */
    static double weightAt(List<FishingCondition> conditions, List<FishingModifier> modifiers, double weight,
                           double quality, FishingContext context) {
        for (FishingCondition condition : conditions) {
            if (!condition.test(context)) return 0;
        }
        double w = weight + quality * context.luck();
        for (FishingModifier modifier : modifiers) {
            if (w <= 0) return 0;
            w = modifier.apply(w, context);
        }
        return Math.max(0, w);
    }

    /** A crate that fits the spot, picked by weight, or empty if none does. */
    public static Optional<CrateData> pickCrate(FishingContext context, RandomSource rng) {
        return pick(crates, c -> c.weightAt(context), rng);
    }

    /** The junk (or treasure) table that fits the spot best, picked by weight, or empty to use vanilla's. */
    public static Optional<FishingLootData> pickLoot(FishingContext context, RandomSource rng, boolean treasure) {
        return pick(loot.stream().filter(l -> l.isTreasure() == treasure).toList(), l -> l.weightAt(context), rng);
    }

    private static <T> Optional<T> pick(List<T> entries, ToDoubleFunction<T> weight, RandomSource rng) {
        double[] w = new double[entries.size()];
        double total = 0;
        for (int i = 0; i < w.length; i++) total += w[i] = weight.applyAsDouble(entries.get(i));
        if (total <= 0) return Optional.empty();
        double r = rng.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0 && w[i] > 0) return Optional.of(entries.get(i));
        }
        return Optional.of(entries.get(w.length - 1));
    }

    @Override
    protected Raw prepare(ResourceManager resources, ProfilerFiller profiler) {
        return new Raw(read(CRATE_FILES, resources), read(LOOT_FILES, resources));
    }

    private static Map<ResourceLocation, JsonElement> read(FileToIdConverter files, ResourceManager resources) {
        Map<ResourceLocation, JsonElement> out = new java.util.LinkedHashMap<>();
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
        crates = parse(raw.crates(), CrateData.CODEC, CrateData::associatedMods, "crate");
        loot = parse(raw.loot(), FishingLootData.CODEC, FishingLootData::associatedMods, "fishing loot");
    }

    private static <T> List<T> parse(Map<ResourceLocation, JsonElement> files, Codec<T> codec,
                                     Function<T, List<String>> mods, String what) {
        List<T> out = new ArrayList<>();
        files.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> CreateGaloreFishing.LOG.warn("Skipping {} {}: {}", what, id, err))
                .filter(entry -> mods.apply(entry).stream().allMatch(ModList.get()::isLoaded))
                .ifPresent(out::add));
        return List.copyOf(out);
    }
}
