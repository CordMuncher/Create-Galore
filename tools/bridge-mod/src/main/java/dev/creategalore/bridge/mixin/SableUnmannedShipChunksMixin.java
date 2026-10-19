package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.creategalore.bridge.ChunkLoadGuard;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicket;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Discovery's flyover airships are force-loaded Sable ships. Each tick Sable tickets the chunks under them and then
 * waits for those chunks on the server thread; over unexplored land that meant generating terrain there, which
 * froze the server for up to 20 s while a flyover crossed new ground (they cruise at y 170-260, far above it).
 * For ships with nobody aboard, keep the ticket but don't wait: the terrain loads in the background and its
 * collision is added once it's there. See {@link ChunkLoadGuard#deferUnmannedShipLoads}.
 */
@Mixin(value = PhysicsChunkTicketManager.class, remap = false)
public abstract class SableUnmannedShipChunksMixin {
    // The force-loaded branch; the second call is Sable's own "a player is aboard" branch, which keeps waiting.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", ordinal = 0,
        target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;inhabitChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/DistanceManager;Ljava/util/UUID;JJII)V"))
    private void create_galore_bridge$forcedNoWait(PhysicsChunkTicketManager self, ServerLevel level, DistanceManager distances,
                                                   UUID id, long gameTime, long chunk, int x, int z, Operation<Void> original,
                                                   @Local ServerSubLevel subLevel) {
        if (create_galore_bridge$playerAboard(level, subLevel)) {
            original.call(self, level, distances, id, gameTime, chunk, x, z);
            return;
        }
        ChunkLoadGuard.deferUnmannedShipLoads = true;
        try {
            original.call(self, level, distances, id, gameTime, chunk, x, z);
        } finally {
            ChunkLoadGuard.deferUnmannedShipLoads = false;
        }
    }

    @WrapOperation(method = "inhabitChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess create_galore_bridge$skipWait(ServerLevel level, int x, int z, ChunkStatus status, boolean load,
                                                     Operation<ChunkAccess> original) {
        return ChunkLoadGuard.deferUnmannedShipLoads ? null : original.call(level, x, z, status, load);
    }

    // Adding a terrain section to the physics world reads the chunk; leave a not-yet-loaded one for a later tick.
    @WrapOperation(method = "update", at = @At(value = "INVOKE", ordinal = 1,
        target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;addTicket(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;Lnet/minecraft/core/SectionPos;IIIIJ)Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicket;"))
    private PhysicsChunkTicket create_galore_bridge$sectionWhenLoaded(PhysicsChunkTicketManager self, Level level, PhysicsPipeline pipeline,
                                                                      SectionPos section, int x, int y, int z, int index, long gameTime,
                                                                      Operation<PhysicsChunkTicket> original, @Local ServerSubLevel subLevel) {
        if (level.getChunkSource().getChunkNow(x, z) == null && !create_galore_bridge$playerAboard((ServerLevel) level, subLevel)) return null;
        return original.call(self, level, pipeline, section, x, y, z, index, gameTime);
    }

    /** Sable's own test for "a player is in this ship", with its one-block margin. */
    private static boolean create_galore_bridge$playerAboard(ServerLevel level, ServerSubLevel subLevel) {
        BoundingBox3dc box = subLevel.boundingBox();
        return !level.getPlayers(p -> {
            Vec3 c = p.getBoundingBox().getCenter();
            return c.x >= box.minX() - 1 && c.x <= box.maxX() + 1 && c.y >= box.minY() - 1 && c.y <= box.maxY() + 1
                    && c.z >= box.minZ() - 1 && c.z <= box.maxZ() + 1;
        }).isEmpty();
    }
}
