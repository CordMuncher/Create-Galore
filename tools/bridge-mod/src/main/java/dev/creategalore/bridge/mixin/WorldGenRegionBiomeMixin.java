package dev.creategalore.bridge.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Aquamirae checks the biome inside every Biome.shouldFreeze call. Water lakes (Alex's Caves' Primordial Caves lakes)
 * call it while a chunk generates, and the biome lookup blends neighbouring quarts, so near the edge of the area a
 * chunk may touch it asks for a chunk outside that area. WorldGenRegion throws "Requested chunk unavailable during
 * world generation" and the server crashes. Outside the area, read the biome straight from the biome source instead
 * (the same biome the chunk will get); inside it, this does exactly what LevelReader.getNoiseBiome does.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionBiomeMixin {
    @Shadow
    @Final
    private ChunkAccess center;

    @Shadow
    @Final
    private ChunkStep generatingStep;

    @Shadow
    public abstract ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean nonnull);

    @Shadow
    public abstract Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        int cx = QuartPos.toSection(x);
        int cz = QuartPos.toSection(z);
        // WorldGenRegion.getChunk only serves a chunk whose status at this distance from the centre reaches BIOMES.
        int distance = center.getPos().getChessboardDistance(cx, cz);
        ChunkDependencies deps = generatingStep.directDependencies();
        boolean available = distance < deps.size() && ChunkStatus.BIOMES.isOrBefore(deps.get(distance));
        ChunkAccess chunk = available ? getChunk(cx, cz, ChunkStatus.BIOMES, false) : null;
        return chunk != null ? chunk.getNoiseBiome(x, y, z) : getUncachedNoiseBiome(x, y, z);
    }
}
