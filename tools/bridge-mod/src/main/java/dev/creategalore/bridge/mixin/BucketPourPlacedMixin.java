package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.guideai.PlacedMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Water and lava a player pours from a bucket count as part of their build for the Field Guide, since water wheels
 * and the like depend on them. Emptying a bucket fires no placement event, so this marks the spot. Dispensers
 * (no player) don't count.
 */
@Mixin(BucketItem.class)
public abstract class BucketPourPlacedMixin {
    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"))
    private void create_galore_bridge$markPoured(Player player, Level level, BlockPos pos, BlockHitResult hit, ItemStack container,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (player != null && !level.isClientSide && cir.getReturnValueZ() && level.getFluidState(pos).isSource()) PlacedMoves.poured(level, pos);
    }
}
