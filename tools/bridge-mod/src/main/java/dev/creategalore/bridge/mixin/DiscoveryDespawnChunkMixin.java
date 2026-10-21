package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import java.util.UUID;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * See {@link ChunkLoadGuard}. When a flyover airship despawns (it drifted out of range or got too old), Discovery
 * force-loads the chunks under it to clear its crew and loose items, and NeoForge loaded each one on the server thread
 * right away: the ship is far from every player by then, often over land nobody has generated, so the server froze
 * for 2 to 14 s. Discovery already waits until those chunks are ticking before it removes anything, so the load
 * can happen in the background like any other ticket.
 */
@Mixin(value = FlyoverUtils.class, remap = false)
public abstract class DiscoveryDespawnChunkMixin {
    @WrapOperation(
        method = "forEachChunk",
        at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/world/chunk/TicketController;forceChunk(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;IIZZ)Z"))
    private static boolean create_galore_bridge$deferLoad(TicketController controller, ServerLevel level, UUID owner, int x, int z,
                                                          boolean add, boolean ticking, Operation<Boolean> original) {
        ChunkLoadGuard.deferForcedLoads = true;
        try {
            return original.call(controller, level, owner, x, z, add, ticking);
        } finally {
            ChunkLoadGuard.deferForcedLoads = false;
        }
    }
}
