package dev.creategalore.fishing.compat;

import dev.creategalore.fishing.FishingRegistry;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * The other rods in the pack become ours: the vanilla Fishing Rod is a Wooden Rod and Alloyed's Steel Fishing Rod
 * is a Steel Rod, wherever a stack of one is made (crafted, looted, loaded from a save, sent over the network,
 * shown in JEI). Enchantments, damage and names carry over. See {@code mixin/ItemStackRodSwapMixin}.
 *
 * <p>Recipes that use a vanilla rod as an ingredient (carrot on a stick and others) keep working: an ingredient
 * matches against stacks of its items, and those stacks are swapped too.
 */
public final class RodSwap {
    private RodSwap() {
    }

    /** Filled once every item exists; until then nothing is swapped. */
    private static volatile Map<Item, Item> swaps = Map.of();

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> {
            Map<Item, Item> map = new IdentityHashMap<>();
            map.put(Items.FISHING_ROD, FishingRegistry.WOODEN_ROD.get());
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("alloyed", "steel_fishing_rod"))
                    .ifPresent(steel -> map.put(steel, FishingRegistry.STEEL_ROD.get()));
            swaps = map;
        });
    }

    /** The rod {@code item} becomes, or null to leave it alone. */
    public static Item swap(Item item) {
        return swaps.get(item);
    }
}
