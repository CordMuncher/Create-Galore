package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create already skips direct neighbours in unloaded chunks when it connects a new kinetic block, but large cogs and
 * Simulated's extra-kinetics hook (which reads the block at every candidate) also look at spots it doesn't check.
 * A kinetic block starting up at the edge of the loaded area then generated the next chunk on the server thread
 * (2 to 3 s freeze). Drop unloaded candidates first; that block connects from its own side once its chunk loads.
 * Priority below the default so this runs before Simulated's hook on the same return.
 */
@Mixin(value = RotationPropagator.class, remap = false, priority = 500)
public abstract class KineticNeighboursLoadedMixin {
    @Inject(method = "getPotentialNeighbourLocations", at = @At("RETURN"), cancellable = true)
    private static void create_galore_bridge$onlyLoaded(KineticBlockEntity be, CallbackInfoReturnable<List<BlockPos>> cir) {
        Level level = be.getLevel();
        List<BlockPos> found = cir.getReturnValue();
        if (level == null || found.stream().allMatch(level::isLoaded)) return;
        List<BlockPos> loaded = new ArrayList<>(found.size());
        for (BlockPos pos : found) if (level.isLoaded(pos)) loaded.add(pos);
        cir.setReturnValue(loaded);
    }
}
