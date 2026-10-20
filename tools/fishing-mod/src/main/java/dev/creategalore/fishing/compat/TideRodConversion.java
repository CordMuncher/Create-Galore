package dev.creategalore.fishing.compat;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import dev.creategalore.fishing.tide.data.rods.BaitContents;
import dev.creategalore.fishing.tide.registries.TideItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Turns a rod rigged with Tide (its line, hook, bobber and bait kept in Tide's own components) into a rod with our
 * parts: the line, hook and bobber (as a float) move into the rod's parts, the bait too, and the rod gets a reel to
 * match it, since Tide's rods had theirs built in. Tide treated a missing accessory as its default (a red bobber, a
 * plain hook and basic line), so those fill any gap. Enchantments, damage and names stay as they are.
 *
 * <p>Only rods that carry one of Tide's components are touched: an unrigged Tide rod looks exactly like our blank
 * of the same tier (the aliases send it there) and stays one.
 */
public final class TideRodConversion {
    private TideRodConversion() {
    }

    private static final List<DataComponentType<?>> TIDE_COMPONENTS = List.of(TideDataComponents.FISHING_LINE,
            TideDataComponents.FISHING_HOOK, TideDataComponents.FISHING_BOBBER, TideDataComponents.BAIT_CONTENTS);

    public static boolean needsConversion(ItemStack rod) {
        return rod.getItem() instanceof RodItem && TIDE_COMPONENTS.stream().anyMatch(rod::has);
    }

    /**
     * Converts {@code rod} in place if it was rigged with Tide. Bait that doesn't fit the rod's bait slot (a different
     * bait, or more than it holds) goes to {@code overflow}.
     */
    public static void convert(ItemStack rod, Consumer<ItemStack> overflow) {
        if (!needsConversion(rod)) return;
        RodParts parts = RodItem.parts(rod);
        if (parts.line().isEmpty()) parts = parts.with(RodParts.Slot.LINE,
                accessory(rod, TideDataComponents.FISHING_LINE, FishingRegistry.STRING_LINE.get()));
        if (parts.hook().isEmpty()) parts = parts.with(RodParts.Slot.HOOK,
                accessory(rod, TideDataComponents.FISHING_HOOK, TideItems.FISHING_HOOK));
        if (parts.terminal().isEmpty()) parts = parts.with(RodParts.Slot.TERMINAL,
                accessory(rod, TideDataComponents.FISHING_BOBBER, TideItems.RED_BOBBER));
        if (parts.reel().isEmpty() && hasReelSeat(rod)) parts = parts.with(RodParts.Slot.REEL, new ItemStack(reelFor(rod.getItem())));

        List<ItemStack> bait = new ArrayList<>();
        BaitContents contents = rod.get(TideDataComponents.BAIT_CONTENTS);
        if (contents != null) contents.items().forEach(s -> bait.add(s.copy()));
        ItemStack slot = parts.bait().copy();
        for (ItemStack stack : bait) {
            if (slot.isEmpty() && TackleManager.of(stack).filter(p -> p.kind() == TacklePart.Kind.BAIT).isPresent()) {
                slot = stack.split(Math.min(stack.getCount(), RodItem.BAIT_CAPACITY));
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(stack.getCount(), RodItem.BAIT_CAPACITY - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
            if (!stack.isEmpty()) overflow.accept(stack);
        }
        parts = parts.with(RodParts.Slot.BAIT, slot);

        TIDE_COMPONENTS.forEach(rod::remove);
        RodItem.setParts(rod, parts);
    }

    private static ItemStack accessory(ItemStack rod, DataComponentType<CompoundTag> type, Item fallback) {
        CompoundTag tag = rod.get(type);
        if (tag == null || tag.isEmpty()) return new ItemStack(fallback);
        return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().filter(s -> !s.isEmpty())
                .orElseGet(() -> new ItemStack(fallback));
    }

    private static boolean hasReelSeat(ItemStack rod) {
        return TackleManager.of(rod).map(p -> !(p instanceof TacklePart.Rod r) || r.reel()).orElse(true);
    }

    /** The reel a Tide rod of this tier had built in, roughly: ours by rod, Tide's special rods an iron reel. */
    private static Item reelFor(Item rod) {
        if (rod == FishingRegistry.WOODEN_ROD.get()) return FishingRegistry.COPPER_REEL.get();
        if (rod == FishingRegistry.STEEL_ROD.get()) return FishingRegistry.BRASS_REEL.get();
        if (rod == FishingRegistry.NETHERITE_ROD.get() || rod == TideItems.BLAZING_FISHING_ROD) return FishingRegistry.STEEL_BAITCASTER.get();
        return FishingRegistry.IRON_REEL.get();
    }
}
