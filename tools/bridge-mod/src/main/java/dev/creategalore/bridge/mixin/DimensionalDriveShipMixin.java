package dev.creategalore.bridge.mixin;

import com.example.cnbridge.block.entity.DimensionalDriveBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Dimensional Drive looks up "its" ship by returning the first ship whose parent level is the drive's level, so with
 * two ships loaded in one dimension it reads the other ship's altitude and jumps the other ship: a drive on a parked
 * ship fired at y180 and sent a far-away ship to orbit, and a drive on a ship climbing past y5000 never fired while a
 * second ship sat near the ground. Ask Sable for the ship whose plot holds the drive instead.
 */
@Mixin(value = DimensionalDriveBlockEntity.class, remap = false)
public abstract class DimensionalDriveShipMixin {
    @Inject(method = "findSubLevel", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$containingShip(Level level, BlockPos pos, CallbackInfoReturnable<ServerSubLevel> cir) {
        if (level == null || level.isClientSide()) return;
        SubLevel ship = Sable.HELPER.getContaining(level, pos);
        cir.setReturnValue(ship instanceof ServerSubLevel s ? s : null);
    }
}
