package dev.creategalore.fishing.fish;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.bait.BaitKind;
import dev.creategalore.fishing.tide.data.TideFishData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Our additions to a fish's Tide data, from {@code data/<namespace>/fishing/species/**.json}. Every field but
 * {@code fish} is optional; whatever is missing comes from {@link SpeciesDefaults}, which reads Tide's minigame
 * strength, speed and behaviour plus the fish's name. Example:
 *
 * <pre>{@code
 * {
 *   "fish": "aquaculture:muskellunge",
 *   "size": {"typical_low_cm": 70, "typical_high_cm": 120, "record_high_cm": 160},
 *   "fight": {"per_kg": 0.95, "stamina": 1.2, "speed": 1.6, "profile": "early",
 *             "runs": 0.1, "jumps": 0.05, "charges": 0.04, "toothy": true},
 *   "length_weight": {"a": 0.006, "b": 3.0},
 *   "feeding": "predator",
 *   "diet": {"live_fish": 1.0, "spoon": 0.9},
 *   "wary": 0.4
 * }
 * }</pre>
 *
 * {@code size} replaces Tide's sizes (some compatibility data had nonsense ones, like a metre-long bluegill).
 * {@code profile} is a preset name (see {@link dev.creategalore.fishing.sim.Curve#PRESETS}) or a list of
 * {@code [progress, intensity]} points. {@code diet} entries are merged over the feeding type's defaults.
 */
public record SpeciesExtension(ResourceLocation fish, Optional<TideFishData.Size> size, Optional<FightData> fight,
                               Optional<LengthWeight> lengthWeight,
                               Optional<Feeding> feeding, Map<BaitKind, Double> diet, Optional<Double> wary,
                               Optional<Double> fillet, Optional<Boolean> oily) {

    public record LengthWeight(double a, double b) {
        public static final Codec<LengthWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("a").forGetter(LengthWeight::a),
                Codec.DOUBLE.optionalFieldOf("b", 3.0).forGetter(LengthWeight::b)).apply(i, LengthWeight::new));
    }

    /** Each field overrides the default of the same name. */
    public record FightData(Optional<Double> perKg, Optional<Double> stamina, Optional<Double> speed,
                            Optional<Either<String, List<List<Double>>>> profile, Optional<Double> runs,
                            Optional<Double> jumps, Optional<Double> charges, Optional<Boolean> toothy) {
        public static final Codec<FightData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("per_kg").forGetter(FightData::perKg),
                Codec.DOUBLE.optionalFieldOf("stamina").forGetter(FightData::stamina),
                Codec.DOUBLE.optionalFieldOf("speed").forGetter(FightData::speed),
                Codec.either(Codec.STRING, Codec.DOUBLE.listOf().listOf()).optionalFieldOf("profile").forGetter(FightData::profile),
                Codec.DOUBLE.optionalFieldOf("runs").forGetter(FightData::runs),
                Codec.DOUBLE.optionalFieldOf("jumps").forGetter(FightData::jumps),
                Codec.DOUBLE.optionalFieldOf("charges").forGetter(FightData::charges),
                Codec.BOOL.optionalFieldOf("toothy").forGetter(FightData::toothy)).apply(i, FightData::new));
    }

    public static final Codec<SpeciesExtension> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("fish").forGetter(SpeciesExtension::fish),
            TideFishData.Size.CODEC.optionalFieldOf("size").forGetter(SpeciesExtension::size),
            FightData.CODEC.optionalFieldOf("fight").forGetter(SpeciesExtension::fight),
            LengthWeight.CODEC.optionalFieldOf("length_weight").forGetter(SpeciesExtension::lengthWeight),
            Feeding.CODEC.optionalFieldOf("feeding").forGetter(SpeciesExtension::feeding),
            Codec.unboundedMap(BaitKind.CODEC, Codec.DOUBLE).optionalFieldOf("diet", Map.of()).forGetter(SpeciesExtension::diet),
            Codec.DOUBLE.optionalFieldOf("wary").forGetter(SpeciesExtension::wary),
            Codec.DOUBLE.optionalFieldOf("fillet_yield").forGetter(SpeciesExtension::fillet),
            Codec.BOOL.optionalFieldOf("oily").forGetter(SpeciesExtension::oily)
    ).apply(i, SpeciesExtension::new));
}
