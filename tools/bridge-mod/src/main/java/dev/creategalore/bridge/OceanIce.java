package dev.creategalore.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;

/**
 * Oceans freeze only where the biome itself is frozen (frozen ocean, deep frozen ocean), so boats can always get out to
 * sea. Serene Seasons leaves oceans out of its seasons, so it neither freezes nor melts them: ice that Winds & Weather
 * blizzards (before 0.3.0) or Cold Sweat's own freezing (before 0.3.3, or wherever it is still on) left on a cold
 * ocean stayed there for good, scattered over the water. Rivers, lakes and land biomes keep their seasonal freezing.
 */
public final class OceanIce {
    private OceanIce() {
    }

    /** True for an ocean biome that is not frozen: water there never turns to ice, and ice on it melts. */
    public static boolean isOpenOcean(LevelReader level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (!biome.is(Tags.Biomes.IS_OCEAN)) return false;
        // Frozen ocean is cold by temperature; deep frozen ocean is warm on paper and frozen by its temperature
        // modifier, which Biome keeps private, so it goes by name (as would any modded "frozen" ocean).
        if (biome.value().getBaseTemperature() < 0.15f || biome.is(Biomes.DEEP_FROZEN_OCEAN)) return false;
        return biome.unwrapKey().map(k -> !k.location().getPath().contains("frozen")).orElse(true);
    }
}
