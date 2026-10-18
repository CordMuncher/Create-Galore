package dev.creategalore.bridge.mixin;

import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlock;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import dev.creategalore.bridge.TransformerCoils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Large Transformer dropped nothing when mined or wrenched. Each half now drops the Large Coil it was built from, with
 * its turns, and the main half also drops the Block of Steel once it has been fitted. The lubrication oil and the
 * Laminated Magnetic Alloy Block (TFMG never consumes it) are not returned.
 */
@Mixin(value = LargeTransformerBlock.class, remap = false)
public abstract class LargeTransformerDropsMixin {
    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$dropCoils(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LargeTransformerBlockEntity be)) return;
        List<ItemStack> drops = new ArrayList<>();
        ItemStack coil = new ItemStack(TFMGBlocks.LARGE_COIL.get());
        int turns = ((TransformerCoils.Half) be).create_galore_bridge$coilTurns();
        if (turns >= 0) coil.set(TFMGDataComponents.COIL_TURNS, turns);
        drops.add(coil);
        if (state.getValue(LargeTransformerBlock.IS_MAIN_PART)
                && be.constructionState != LargeTransformerBlockEntity.TransformerConstructionState.NEEDS_STEEL) {
            drops.add(new ItemStack(TFMGBlocks.STEEL_BLOCK.get()));
        }
        cir.setReturnValue(drops);
    }
}
