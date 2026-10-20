package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import dev.creategalore.bridge.guideai.PlacedMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A belt a player connects counts as placed for the Field Guide: Create lays every segment straight into the world,
 * so no placement event fires for them. Segments run in a straight line (or a 45 degree slope) from start to end.
 */
@Mixin(value = BeltConnectorItem.class, remap = false)
public abstract class BeltPlacedMixin {
    @Inject(method = "createBelts", at = @At("RETURN"))
    private static void create_galore_bridge$markBelt(Level level, BlockPos start, BlockPos end, CallbackInfo ci) {
        if (level.isClientSide || !PlacedMoves.tracking()) return;
        int dx = end.getX() - start.getX(), dy = end.getY() - start.getY(), dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps > 64) return;
        for (int i = 0; i <= steps; i++) {
            BlockPos p = steps == 0 ? start : start.offset(dx * i / steps, dy * i / steps, dz * i / steps);
            PlacedMoves.mark(level, p);
        }
    }
}
