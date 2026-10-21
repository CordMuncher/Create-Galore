/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.Optional;

public record SizeData(double typicalLowCm, double typicalHighCm, Optional<Double> recordLowCm, double recordHighCm) {
    public static final Codec<SizeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("typical_low_cm").forGetter(SizeData::typicalLowCm),
            Codec.DOUBLE.fieldOf("typical_high_cm").forGetter(SizeData::typicalHighCm),
            Codec.DOUBLE.optionalFieldOf("record_low_cm").forGetter(SizeData::recordLowCm),
            Codec.DOUBLE.fieldOf("record_high_cm").forGetter(SizeData::recordHighCm)
    ).apply(instance, SizeData::new));

    public SizeData(double typicalLowCm, double typicalHighCm, double recordHighCm) {
        this(typicalLowCm, typicalHighCm, Optional.empty(), recordHighCm);
    }

    public SizeData(double typicalLowCm, double typicalHighCm, double recordLowCm, double recordHighCm) {
        this(typicalLowCm, typicalHighCm, Optional.of(recordLowCm), recordHighCm);
    }

    public SizeData() {
        this(35.0, 60.0, 100.0);
    }

    public double sample(RandomSource rng, double trophyChance) {
        return FishSizeModel.sampleLengthCm(rng, typicalLowCm, typicalHighCm, recordLowCm, recordHighCm, trophyChance);
    }
}
