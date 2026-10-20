/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing: the record and its codec are Tide's, so Tide's
 * fish files (data/<namespace>/fishing/fish/**.json) load unchanged; the journal,
 * display and minigame fields are kept for the ported journal and trophy mounts.
 */
package dev.creategalore.fishing.tide.data.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One fish as Tide describes it: which item it is, where and when it bites (conditions), how often (selection
 * weight, scaled by modifiers and luck), its sizes in cm, its journal page and how it sits on a display.
 *
 * <p>A fish with a {@code parent} is merged into that fish: it never bites itself, and its item counts as the
 * parent for the journal. The pack uses this to make one mod's fish the canonical version of a species.
 */
public record FishData(Holder<Item> fish,
                           Optional<Holder<Item>> bucket,
                           List<String> associatedMods,
                           boolean showInJournal,
                           List<FishingCondition> conditions,
                           List<FishingModifier> modifiers,
                           double weight,
                           double quality,
                           float strength,
                           float speed,
                           Behavior behavior,
                           ProfileData profile,
                           Optional<SizeData> size,
                           Optional<DisplayData> display,
                           Optional<Holder<Item>> parent) {

    public static final Codec<FishData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("fish").forGetter(FishData::fish),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("bucket").forGetter(FishData::bucket),
            Codec.STRING.listOf().optionalFieldOf("associated_mods", List.of()).forGetter(FishData::associatedMods),
            Codec.BOOL.optionalFieldOf("show_in_journal", true).forGetter(FishData::showInJournal),
            FishingCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(FishData::conditions),
            FishingModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(FishData::modifiers),
            Codec.DOUBLE.optionalFieldOf("selection_weight", 0.0).forGetter(FishData::weight),
            Codec.DOUBLE.optionalFieldOf("selection_quality", 0.0).forGetter(FishData::quality),
            Codec.FLOAT.optionalFieldOf("strength", 0.3f).forGetter(FishData::strength),
            Codec.FLOAT.optionalFieldOf("speed", 1.0f).forGetter(FishData::speed),
            Behavior.CODEC.optionalFieldOf("behavior", Behavior.SINE).forGetter(FishData::behavior),
            ProfileData.CODEC.optionalFieldOf("journal_profile", new ProfileData()).forGetter(FishData::profile),
            SizeData.CODEC.optionalFieldOf("size").forGetter(FishData::size),
            DisplayData.CODEC.optionalFieldOf("display_data").forGetter(FishData::display),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("parent").forGetter(FishData::parent)
    ).apply(i, FishData::new));

    // ---- Lookups (Tide's API). Published by the species loader on the server and by the data sync on clients. ----

    private static volatile Map<Item, FishData> byItem = Map.of();
    private static volatile Map<Item, FishData> byBucket = Map.of();
    private static volatile List<FishData> originals = List.of();
    private static volatile Map<Item, List<FishData>> mergers = Map.of();

    /**
     * Replaces the known fish: every loaded fish file, merged ones included (their items and buckets then resolve
     * to their parent's data, as in Tide).
     */
    public static void publish(Collection<FishData> all) {
        Map<Item, FishData> items = new HashMap<>();
        Map<Item, FishData> buckets = new HashMap<>();
        Map<Item, List<FishData>> merged = new HashMap<>();
        List<FishData> roots = new ArrayList<>();
        for (FishData data : all) {
            if (data.parent().isPresent()) {
                merged.computeIfAbsent(data.parent().get().value(), k -> new ArrayList<>()).add(data);
                continue;
            }
            roots.add(data);
            items.put(data.fish().value(), data);
            data.bucket().ifPresent(b -> buckets.put(b.value(), data));
        }
        merged.forEach((parent, list) -> {
            FishData parentData = items.get(parent);
            if (parentData == null) return;
            for (FishData data : list) {
                items.putIfAbsent(data.fish().value(), parentData);
                data.bucket().ifPresent(b -> buckets.putIfAbsent(b.value(), parentData));
            }
        });
        byItem = Map.copyOf(items);
        byBucket = Map.copyOf(buckets);
        originals = List.copyOf(roots);
        mergers = Map.copyOf(merged);
    }

    /** Every fish that is its own species (not merged into another), in load order. */
    public static List<FishData> all() {
        return originals;
    }

    public static Optional<FishData> get(ItemStack fish) {
        return get(fish.getItem());
    }

    public static Optional<FishData> get(Entity entity) {
        return get(entity.getType());
    }

    /** A fish mob's data: the fish item with the entity's id. */
    public static Optional<FishData> get(EntityType<?> entityType) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return BuiltInRegistries.ITEM.getOptional(key).flatMap(FishData::get);
    }

    public static Optional<FishData> get(Item fish) {
        return Optional.ofNullable(byItem.get(fish));
    }

    public static Optional<FishData> fromBucket(ItemStack bucket) {
        return Optional.ofNullable(byBucket.get(bucket.getItem()));
    }

    /** The data of this exact item, even when it is merged into another fish. */
    public static Optional<FishData> getExact(ItemStack fish) {
        return getExact(fish.getItem());
    }

    public static Optional<FishData> getExact(Item fish) {
        Optional<FishData> data = get(fish);
        if (data.isEmpty() || data.get().fish().value() == fish) return data;
        return mergers.getOrDefault(data.get().fish().value(), List.of()).stream()
                .filter(d -> d.fish().value() == fish).findFirst();
    }

    public static FishData getOrThrow(ItemStack fish) {
        return get(fish).orElseThrow();
    }

    public static ItemStack randomFish() {
        List<FishData> list = originals;
        return list.isEmpty() ? ItemStack.EMPTY : new ItemStack(list.get(RandomSource.create().nextInt(list.size())).fish());
    }

    public boolean hasJournalEntry() {
        return isOriginal() && showInJournal();
    }

    public boolean isOriginal() {
        return parent().isEmpty();
    }

    /** A random length in cm, as Tide sizes a fish it hands out (0 if the fish has no sizes). */
    public double getRandomLength(RandomSource rng) {
        return size.map(s -> s.sample(rng, 0.0)).orElse(0.0);
    }

    public double getAverageLength() {
        return size.map(s -> (s.typicalHighCm() + s.typicalLowCm()) / 2.0).orElse(0.0);
    }

    /** Whether all of this fish's conditions pass at the spot. */
    public boolean canBiteAt(FishingContext context) {
        for (FishingCondition condition : conditions) {
            if (!condition.test(context)) return false;
        }
        return true;
    }

    /** Selection weight at the spot: base + quality x luck, then each modifier; never below 0. */
    public double weightAt(FishingContext context) {
        double w = weight + quality * context.luck();
        if (w <= 0) return 0;
        for (FishingModifier modifier : modifiers) {
            w = modifier.apply(w, context);
            if (w <= 0) return 0;
        }
        return w;
    }

    /** The fluid this fish lives in ("water" unless a fluid condition says otherwise). */
    public String medium() {
        for (FishingCondition condition : conditions) {
            if (condition instanceof FishingCondition.Medium m) return m.medium();
        }
        return "water";
    }

    /**
     * How the fish's marker moved in Tide's minigame. Not used for fights directly; it seeds the default fight
     * profile for fish that have no fight data of their own.
     */
    public enum Behavior implements StringRepresentable {
        SINE, PLATEAU, JITTER, DARTS, LINEAR, LINEAR_WRAP;

        public static final Codec<Behavior> CODEC = StringRepresentable.fromEnum(Behavior::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
