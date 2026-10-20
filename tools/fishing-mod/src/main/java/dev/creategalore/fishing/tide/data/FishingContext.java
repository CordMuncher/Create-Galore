/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a fish's conditions and modifiers can ask about the spot being fished: where, which fluid, biome,
 * time, weather, moon, season and how lucky the angler is. Built by the hook (or a test) once per bite roll.
 *
 * @param medium       "water", "lava" or "void"
 * @param exactBiome   the biome at the hook
 * @param nearestBiome the closest biome that isn't a water biome (so a lake in a jungle counts as jungle)
 * @param temperature  climate temperature at the hook, -1..1, shifted by fiery/permafrost hooks
 * @param hookItem     the hook part on the rod (empty if none), for hooks that change conditions
 * @param openWater    whether the hook is in open water (5x5, two deep, sky above)
 */
public record FishingContext(
        ServerLevel level,
        ItemStack rod,
        ItemStack hookItem,
        RandomSource rng,
        Vec3 pos,
        BlockPos blockPos,
        int luck,
        String medium,
        Holder<Biome> exactBiome,
        Holder<Biome> nearestBiome,
        ResourceKey<Level> dimension,
        float temperature,
        int moonPhase,
        Season season,
        boolean openWater) {
}
