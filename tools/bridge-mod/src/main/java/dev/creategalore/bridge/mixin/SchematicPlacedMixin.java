package dev.creategalore.bridge.mixin;

import com.simibubi.create.foundation.utility.BlockHelper;
import dev.creategalore.bridge.guideai.PlacedMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks a schematicannon prints are a player's build too, for the Field Guide; it sets them without an event. */
@Mixin(value = BlockHelper.class, remap = false)
public abstract class SchematicPlacedMixin {
    @Inject(method = "placeSchematicBlock", at = @At("RETURN"))
    private static void create_galore_bridge$markPrinted(Level level, BlockState state, BlockPos target, ItemStack stack, CompoundTag data, CallbackInfo ci) {
        if (!level.isClientSide && PlacedMoves.tracking()) PlacedMoves.mark(level, target);
    }
}
