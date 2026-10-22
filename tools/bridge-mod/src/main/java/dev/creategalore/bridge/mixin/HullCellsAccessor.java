package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the hold cells of Deep Seas' private Hull record (see HighSeasHoldFloorsMixin). */
@Mixin(targets = "com.maxenonyme.highseas.BoatBuoyancySystem$Hull", remap = false)
public interface HullCellsAccessor {
    @Accessor("cells")
    double[] create_galore_bridge$cells();

    @Accessor("count")
    int create_galore_bridge$count();
}
