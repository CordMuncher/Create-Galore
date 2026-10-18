package dev.creategalore.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Berries and Cherries ships its own Iron Knife next to Farmer's Delight's. Farmer's Delight's is the one every
 * cutting-board recipe and c:tools/knife expects, so the Berries and Cherries knife turns into it wherever a stack is
 * made: inventories, chests, backpacks and contraptions as they load, and anything the mod still hands out.
 * Resolved once the registries are frozen; until then (and without either mod) nothing is swapped.
 */
public final class KnifeUnify {
    private static final ResourceLocation REMOVED = ResourceLocation.fromNamespaceAndPath("berries_and_cherries", "ironknife");
    private static final ResourceLocation CANONICAL = ResourceLocation.fromNamespaceAndPath("farmersdelight", "iron_knife");

    public static Item removed;
    public static Item canonical;

    private KnifeUnify() {}

    static void resolve() {
        var from = BuiltInRegistries.ITEM.getOptional(REMOVED);
        var to = BuiltInRegistries.ITEM.getOptional(CANONICAL);
        if (from.isPresent() && to.isPresent()) {
            canonical = to.get();
            removed = from.get();
        }
    }
}
