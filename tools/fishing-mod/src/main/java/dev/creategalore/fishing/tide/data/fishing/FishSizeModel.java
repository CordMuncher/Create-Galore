/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.fishing;

import net.minecraft.util.RandomSource;

import java.util.Optional;

public final class FishSizeModel {
    // standard normal quantiles for 10% and 90%
    private static final double Z10 = -1.2815515655446004;
    private static final double Z90 =  1.2815515655446004;

    private record LogNormalParams(double mu, double sigma) {}

    private static LogNormalParams fitFromTypicalRange(double typicalLowCm, double typicalHighCm) {
        double lnLow = Math.log(typicalLowCm);
        double lnHigh = Math.log(typicalHighCm);
        double sigma = (lnHigh - lnLow) / (Z90 - Z10);
        double mu = lnLow - Z10 * sigma;
        return new LogNormalParams(mu, sigma);
    }

    private static double logNormalDist(LogNormalParams params, RandomSource rng) {
        return Math.exp(params.mu + params.sigma * rng.nextGaussian());
    }

    public static double sampleLengthCm(
            RandomSource rng,
            double typicalLowCm,
            double typicalHighCm,
            Optional<Double> recordLowCm,
            double recordHighCm,
            double trophyChance
    ) {
        LogNormalParams params = fitFromTypicalRange(typicalLowCm, typicalHighCm);
        double absoluteMin = recordLowCm.orElse(0.6 * typicalLowCm);

        double length = logNormalDist(params, rng);
        if (rng.nextDouble() < trophyChance) {
            // try draw from upper side
            do length = logNormalDist(params, rng);
            while (length < typicalHighCm);
        }

        // handle extremes
        if (length <= absoluteMin) length = Math.max(logNormalDist(params, rng), absoluteMin);
        if (length >= recordHighCm) length = Math.min(logNormalDist(params, rng), recordHighCm);
        return length;
    }
}
