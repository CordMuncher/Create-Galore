package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * A spool of line. Keeps how many metres are left: a snapped line loses what was out plus a bit, and a spool
 * shorter than the reel's capacity limits how far a fish can run.
 */
public class LineItem extends TackleItem {
    public LineItem(Properties properties) {
        super(properties);
    }

    /** Metres left on a spool (a fresh spool has its full length). */
    public static int meters(ItemStack line) {
        Integer left = line.get(FishingRegistry.LINE_METERS.get());
        if (left != null) return left;
        return TackleManager.of(line, TacklePart.Line.class).map(TacklePart.Line::meters).orElse(0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(FishingRegistry.LINE_METERS.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int full = TackleManager.of(stack, TacklePart.Line.class).map(TacklePart.Line::meters).orElse(1);
        return Math.round(13f * Math.min(1, meters(stack) / (float) Math.max(1, full)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x6fb7e0;
    }
}
