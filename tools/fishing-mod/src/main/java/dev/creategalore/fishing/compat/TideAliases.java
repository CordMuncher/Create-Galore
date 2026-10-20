package dev.creategalore.fishing.compat;

import dev.creategalore.fishing.CreateGaloreFishing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry aliases for the Tide ids this mod doesn't register, so worlds made with Tide load with nothing lost.
 *
 * <p>Ported Tide content keeps its own {@code tide:} ids and needs nothing here. What's left is what the pack hands to
 * other mods (twelve species that Aquaculture and Hybrid Aquatic already have, and Voidseeker, which folds into
 * Darkness Eater) and what our tackle replaces (Tide's tier rods, its lines, the Angling Table, fish bones and fish
 * slices). An alias only applies while the old name is free, and it is followed everywhere a name is looked up:
 * saved items, entities and block entities, codecs (recipes, loot tables, fish data) and tags.
 *
 * <p>Each target is only aliased when its mod is loaded, so a pack without Hybrid Aquatic simply loses those three.
 */
public final class TideAliases {
    private TideAliases() {
    }

    /** One handed-off species: its fish item, its bucket and spawn egg (where the target mod has them) and its mob. */
    private record Species(String tide, String target, String bucket, String egg) {
    }

    // Aquaculture's fish have buckets but no spawn eggs, so a Tide spawn egg becomes the bucket (it places the fish
    // too). Hybrid Aquatic's have spawn eggs but no buckets, so a Tide bucket becomes the fish item.
    private static final List<Species> SPECIES = List.of(
            new Species("arapaima", "aquaculture:arapaima", "aquaculture:arapaima_bucket", "aquaculture:arapaima_bucket"),
            new Species("bluegill", "aquaculture:bluegill", "aquaculture:bluegill_bucket", "aquaculture:bluegill_bucket"),
            new Species("carp", "aquaculture:carp", "aquaculture:carp_bucket", "aquaculture:carp_bucket"),
            new Species("catfish", "aquaculture:catfish", "aquaculture:catfish_bucket", "aquaculture:catfish_bucket"),
            new Species("rainbow_trout", "aquaculture:rainbow_trout", "aquaculture:rainbow_trout_bucket", "aquaculture:rainbow_trout_bucket"),
            new Species("smallmouth_bass", "aquaculture:smallmouth_bass", "aquaculture:smallmouth_bass_bucket", "aquaculture:smallmouth_bass_bucket"),
            new Species("tuna", "aquaculture:tuna", "aquaculture:tuna_bucket", "aquaculture:tuna_bucket"),
            new Species("coelacanth", "hybrid_aquatic:coelacanth", "hybrid_aquatic:coelacanth", "hybrid_aquatic:coelacanth_spawn_egg"),
            new Species("mackerel", "hybrid_aquatic:mackerel", "hybrid_aquatic:mackerel", "hybrid_aquatic:mackerel_spawn_egg"),
            new Species("mahi_mahi", "hybrid_aquatic:mahi", "hybrid_aquatic:mahi", "hybrid_aquatic:mahi_spawn_egg"),
            new Species("anglerfish", "hybrid_aquatic:anglerfish", "hybrid_aquatic:anglerfish", "hybrid_aquatic:anglerfish_spawn_egg"),
            new Species("voidseeker", "tide:darkness_eater", "tide:darkness_eater_bucket", "tide:darkness_eater_spawn_egg"));

    /** Items Tide had that our own items replace. */
    private static final Map<String, String> ITEMS = new LinkedHashMap<>();
    static {
        String ours = CreateGaloreFishing.ID + ":";
        ITEMS.put("stone_fishing_rod", ours + "wooden_rod");
        ITEMS.put("iron_fishing_rod", ours + "iron_rod");
        ITEMS.put("golden_fishing_rod", ours + "iron_rod");
        ITEMS.put("crystal_fishing_rod", ours + "steel_rod");
        ITEMS.put("diamond_fishing_rod", ours + "steel_rod");
        ITEMS.put("netherite_fishing_rod", ours + "netherite_rod");
        // Tide's lines by strength: string, then copper and iron, gold (fine and hard to see), diamond.
        ITEMS.put("fishing_line", ours + "string_line");
        ITEMS.put("copper_line", ours + "mono_line");
        ITEMS.put("iron_line", ours + "mono_line");
        ITEMS.put("golden_line", ours + "clear_line");
        ITEMS.put("diamond_line", ours + "braided_line");
        ITEMS.put("angling_table", ours + "tackle_bench");
        ITEMS.put("fish_bone", "aquaculture:fish_bones");
        ITEMS.put("fish_slice", "aquaculture:fish_fillet_raw");
        ITEMS.put("cooked_fish_slice", "aquaculture:fish_fillet_cooked");
    }

    private static final Map<String, String> BLOCKS = Map.of("angling_table", CreateGaloreFishing.ID + ":tackle_bench");

    /** Every item alias, old Tide id to the id it now loads as (for the self-test and the docs). */
    public static Map<ResourceLocation, ResourceLocation> items() {
        Map<ResourceLocation, ResourceLocation> out = new LinkedHashMap<>();
        for (Species s : SPECIES) {
            put(out, s.tide(), s.target());
            put(out, s.tide() + "_bucket", s.bucket());
            put(out, s.tide() + "_spawn_egg", s.egg());
        }
        ITEMS.forEach((from, to) -> put(out, from, to));
        return out;
    }

    /** Every entity alias, old Tide id to the mob it now loads as. */
    public static Map<ResourceLocation, ResourceLocation> entities() {
        Map<ResourceLocation, ResourceLocation> out = new LinkedHashMap<>();
        for (Species s : SPECIES) put(out, s.tide(), s.target());
        return out;
    }

    public static Map<ResourceLocation, ResourceLocation> blocks() {
        Map<ResourceLocation, ResourceLocation> out = new LinkedHashMap<>();
        BLOCKS.forEach((from, to) -> put(out, from, to));
        return out;
    }

    /** Adds the aliases; called from the mod constructor, before any registry fires. */
    public static void register(IEventBus modBus) {
        alias(modBus, Registries.ITEM, items());
        alias(modBus, Registries.BLOCK, blocks());
        alias(modBus, Registries.ENTITY_TYPE, entities());
    }

    private static <T> void alias(IEventBus modBus, ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
                                  Map<ResourceLocation, ResourceLocation> aliases) {
        DeferredRegister<T> register = DeferredRegister.create(registry, "tide");
        aliases.forEach(register::addAlias);
        register.register(modBus);
    }

    private static void put(Map<ResourceLocation, ResourceLocation> out, String tidePath, String target) {
        ResourceLocation to = ResourceLocation.parse(target);
        if (!to.getNamespace().equals("tide") && !to.getNamespace().equals(CreateGaloreFishing.ID)
                && !ModList.get().isLoaded(to.getNamespace())) return;
        out.put(ResourceLocation.fromNamespaceAndPath("tide", tidePath), to);
    }
}
