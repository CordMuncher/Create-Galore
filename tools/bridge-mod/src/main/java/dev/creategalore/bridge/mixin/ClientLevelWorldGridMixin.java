package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.grid.WorldGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The block grid (see {@link WorldGrid}) hears of every block that changes, every section whose blocks or light change, and every chunk that comes or goes. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWorldGridMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void create_galore_bridge$gridBlock(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        WorldGrid.blockChanged(pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
    private void create_galore_bridge$gridSection(int x, int y, int z, CallbackInfo ci) {
        WorldGrid.sectionChanged(x, y, z);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void create_galore_bridge$gridChunk(ChunkPos pos, CallbackInfo ci) {
        WorldGrid.chunkChanged(pos.x, pos.z);
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void create_galore_bridge$gridUnload(LevelChunk chunk, CallbackInfo ci) {
        WorldGrid.chunkChanged(chunk.getPos().x, chunk.getPos().z);
    }
}
