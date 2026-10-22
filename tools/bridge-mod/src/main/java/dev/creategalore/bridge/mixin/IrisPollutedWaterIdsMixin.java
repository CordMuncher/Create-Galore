package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.PollutedWater;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shader packs only know water by the block id they give minecraft:water; polluted water got none, so packs drew it as
 * a plain lit block (a bright, flat yellow cube underwater). Each polluted water state now shares the id of the water
 * state at the same level, so packs render it as water (waves, see-through, refraction) and it keeps its own tint.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", remap = false)
public abstract class IrisPollutedWaterIdsMixin {
    @ModifyVariable(method = "setBlockStateIds", at = @At("HEAD"), argsOnly = true)
    private Object2IntMap<BlockState> create_galore_bridge$pollutedAsWater(Object2IntMap<BlockState> ids) {
        if (ids == null || !ids.containsKey(Blocks.WATER.defaultBlockState())) return ids;
        Object2IntOpenHashMap<BlockState> copy = new Object2IntOpenHashMap<>(ids);
        copy.defaultReturnValue(ids.defaultReturnValue());
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof PollutedWater)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (ids.containsKey(state)) continue;
                BlockState water = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, state.getValue(LiquidBlock.LEVEL));
                copy.put(state, ids.containsKey(water) ? ids.getInt(water) : ids.getInt(Blocks.WATER.defaultBlockState()));
            }
        }
        return copy;
    }
}
