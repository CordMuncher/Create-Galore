package dev.creategalore.bridge.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads the chunks the server holds without asking for one to load (see FlyoverSkyRoute). */
@Mixin(ChunkMap.class)
public interface ChunkMapVisibleInvoker {
    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder create_galore_bridge$visible(long pos);
}
