package dev.creategalore.bridge;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blaze Burners for Create: Smog. Smog scales a burner by its heat level and still counts a smouldering
 * one (a blaze with no fuel) at a third. Smouldering burns nothing, so here it makes no smog. Covers
 * Create's burner, the Crafts & Additions straw burner and the TFMG firebox, which share the heat level.
 */
public final class BurnerActivity {
    private BurnerActivity() {}

    public static float of(BlockState state) {
        return state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)
            && state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.SMOULDERING ? 0f : TfmgActivity.UNKNOWN;
    }
}
