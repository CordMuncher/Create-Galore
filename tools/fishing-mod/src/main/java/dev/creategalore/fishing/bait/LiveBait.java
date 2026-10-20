package dev.creategalore.fishing.bait;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Live bait: what the Live-Bait Bucket keeps (baitfish and leeches), and the mark that bait loaded from it carries on
 * the rod. Marked bait counts as live (a baitfish is a live fish, not cut bait), which predators take far more
 * readily. Out of the water it dies: bait taken back off the rod loses the mark.
 */
public final class LiveBait {
    private LiveBait() {
    }

    /** What the bucket takes: the {@code baitfish} tag and leeches. */
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, CreateGaloreFishing.id("live_bait"));

    public static boolean isLive(ItemStack bait) {
        return !bait.isEmpty() && bait.has(FishingRegistry.LIVE.get());
    }

    public static ItemStack alive(ItemStack bait) {
        ItemStack out = bait.copy();
        out.set(FishingRegistry.LIVE.get(), Unit.INSTANCE);
        return out;
    }

    public static ItemStack dead(ItemStack bait) {
        ItemStack out = bait.copy();
        out.remove(FishingRegistry.LIVE.get());
        return out;
    }
}
