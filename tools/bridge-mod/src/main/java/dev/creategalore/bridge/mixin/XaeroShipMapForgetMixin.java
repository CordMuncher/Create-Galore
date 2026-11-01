package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.client.ShipMapGone;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * See {@link ShipMapGone}. Create - Xaero's Map keeps a picture of a ship that left view standing still until the
 * player comes within 48 blocks of it, so ships that were long gone piled up on the map.
 */
@Mixin(targets = "com.sablexaeromaps.SublevelMapRenderer", remap = false)
public abstract class XaeroShipMapForgetMixin {
    @Inject(method = "sweepUntracked", at = @At("HEAD"), remap = false)
    private static void create_galore_bridge$dropGone(String map, Set<UUID> tracked, long gameTime, List<?> draws, float scale,
                                                      int caveLayer, CallbackInfo ci) {
        ShipMapGone.sweep(tracked);
    }

    /** The first list restored from the world's file is the ships' (the second, Create contraptions', stays). */
    @WrapOperation(method = "pumpRestore",
            at = @At(value = "INVOKE", target = "Ljava/util/Deque;addAll(Ljava/util/Collection;)Z", ordinal = 0), remap = false)
    private static boolean create_galore_bridge$clearOldOnce(Deque<?> queue, Collection<?> ships, Operation<Boolean> original) {
        if (!ships.isEmpty() && ShipMapGone.clearOldOnce()) return false;
        return original.call(queue, ships);
    }
}
