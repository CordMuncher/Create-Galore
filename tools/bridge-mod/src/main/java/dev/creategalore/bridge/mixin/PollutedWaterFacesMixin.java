package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.PollutedWater;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Polluted water is its own fluid, so where it meets clean water both drew their faces: a hard-edged cube of murk
 * inside the sea, which shader packs light up as a solid block. Water and polluted water now hide the faces between
 * them like water does with itself (the vanilla renderer and Sodium both ask this hook).
 */
@Mixin(LiquidBlock.class)
public abstract class PollutedWaterFacesMixin {
    public boolean shouldHideAdjacentFluidFace(BlockState state, Direction selfFace, FluidState adjacentFluid) {
        FluidState own = state.getFluidState();
        if (own.getType().isSame(adjacentFluid.getType())) return true;
        return create_galore_bridge$waterLike(state, own) && create_galore_bridge$waterLike(adjacentFluid.createLegacyBlock(), adjacentFluid);
    }

    private static boolean create_galore_bridge$waterLike(BlockState block, FluidState fluid) {
        return block.getBlock() instanceof PollutedWater || fluid.getType().isSame(Fluids.WATER);
    }
}
