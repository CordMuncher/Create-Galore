/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64
 * (TideUtils.getTemperatureAt, findClosestNonWaterBiome and TideFishingHook.getContext),
 * adapted for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.phys.Vec3;

/** Reads the facts about a fishing spot that fish conditions use. */
public final class FishingPlace {
    /** Water biomes (oceans, rivers), skipped when looking for the land biome a lake belongs to. */
    public static final TagKey<Biome> WATER_BIOMES = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("tide", "water_biomes"));

    private FishingPlace() {
    }

    /** Climate temperature at {@code pos}, about -1 (frozen) to 1 (hot). */
    public static float temperatureAt(ServerLevel level, BlockPos pos) {
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()));
        return Climate.unquantizeCoord(point.temperature());
    }

    /** The nearest biome around {@code origin} that isn't a water biome, searching outward. */
    public static Optional<Holder<Biome>> closestLandBiome(Level level, BlockPos origin, int radius, int step) {
        int steps = Math.floorDiv(radius, step);
        int[] relativeYs = Mth.outFromOrigin(0, -radius, radius, step).toArray();
        for (BlockPos.MutableBlockPos offset : BlockPos.spiralAround(BlockPos.ZERO, steps, Direction.EAST, Direction.SOUTH)) {
            int qx = QuartPos.fromBlock(origin.getX() + offset.getX() * step);
            int qz = QuartPos.fromBlock(origin.getZ() + offset.getZ() * step);
            for (int y : relativeYs) {
                Holder<Biome> biome = level.getNoiseBiome(qx, QuartPos.fromBlock(y + origin.getY()), qz);
                if (!biome.is(WATER_BIOMES)) return Optional.of(biome);
            }
        }
        return Optional.empty();
    }

    /**
     * Builds the context for a bite roll at {@code pos}.
     *
     * @param medium            "water", "lava" or "void"
     * @param temperatureOffset added to the sampled temperature (fiery hooks +0.25, permafrost -0.25)
     * @param netherOverride    treat the spot as the Nether (the Blazing rod in lava)
     */
    public static FishingContext context(ServerLevel level, Vec3 pos, ItemStack rod, ItemStack hookItem, int luck,
                                         String medium, float temperatureOffset, boolean netherOverride,
                                         boolean openWater, RandomSource rng) {
        BlockPos block = BlockPos.containing(pos);
        Holder<Biome> here = level.getBiome(block);
        return new FishingContext(level, rod, hookItem, rng, pos, block, luck, medium, here,
                closestLandBiome(level, block, 12, 2).orElse(here),
                netherOverride ? Level.NETHER : level.dimension(),
                Mth.clamp(temperatureAt(level, block) + temperatureOffset, -1f, 1f),
                level.getMoonPhase(), Season.of(level), openWater);
    }
}
