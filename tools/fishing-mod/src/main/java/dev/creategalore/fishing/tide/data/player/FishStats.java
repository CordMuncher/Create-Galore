/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.player;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import java.util.Optional;

public class FishStats {
    public static final Codec<FishStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("amount_caught").forGetter(FishStats::getAmountCaught),
            CatchTimestamp.CODEC.optionalFieldOf("initial_catch_date").forGetter(FishStats::getInitialCatchDate),
            Codec.DOUBLE.fieldOf("largest_catch").forGetter(FishStats::getLargestCatch),
            Codec.DOUBLE.fieldOf("smallest_catch").forGetter(FishStats::getSmallestCatch),
            Codec.DOUBLE.optionalFieldOf("heaviest_catch_kg", 0.0).forGetter(FishStats::getHeaviestCatch)
    ).apply(instance, FishStats::new));

    private int amountCaught;
    private Optional<CatchTimestamp> initialCatchDate;
    private double largestCatch;
    private double smallestCatch;
    /** Heaviest one landed with this mod, kg (Tide had no weights; 0 until then). */
    private double heaviestCatch;

    public FishStats() {
        this(0, Optional.empty(), 0, Double.POSITIVE_INFINITY, 0);
    }

    public FishStats(int amountCaught, Optional<CatchTimestamp> initialCatchDate, double largestCatch, double smallestCatch,
                     double heaviestCatch) {
        this.amountCaught = amountCaught;
        this.initialCatchDate = initialCatchDate;
        this.largestCatch = largestCatch;
        this.smallestCatch = smallestCatch;
        this.heaviestCatch = heaviestCatch;
    }

    public int getAmountCaught() {
        return amountCaught;
    }

    public Optional<CatchTimestamp> getInitialCatchDate() {
        return initialCatchDate;
    }

    public double getLargestCatch() {
        return largestCatch;
    }

    public double getSmallestCatch() {
        return smallestCatch;
    }

    public double getHeaviestCatch() {
        return heaviestCatch;
    }

    public boolean isEmpty() {
        return amountCaught == 0;
    }

    /** Two records of one fish together: catches added, the earlier first catch, the bigger and smaller extremes. */
    public static FishStats combine(FishStats a, FishStats b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        Optional<CatchTimestamp> first = a.initialCatchDate.isEmpty() ? b.initialCatchDate : b.initialCatchDate.isEmpty()
                ? a.initialCatchDate : Optional.of(a.initialCatchDate.get().date().isBefore(b.initialCatchDate.get().date())
                ? a.initialCatchDate.get() : b.initialCatchDate.get());
        return new FishStats(a.amountCaught + b.amountCaught, first, Math.max(a.largestCatch, b.largestCatch),
                Math.min(a.smallestCatch, b.smallestCatch), Math.max(a.heaviestCatch, b.heaviestCatch));
    }

    public void logCatch(CatchTimestamp timestamp, double size) {
        this.amountCaught++;
        if (initialCatchDate.isEmpty()) this.initialCatchDate = Optional.of(timestamp);
        if (size <= 0.0 || size > largestCatch) this.largestCatch = size;
        if (size <= 0.0 || size < smallestCatch) this.smallestCatch = size;
    }

    public void logCatch(CatchTimestamp timestamp, double size, double kg) {
        logCatch(timestamp, size);
        if (kg > heaviestCatch) this.heaviestCatch = kg;
    }
}
