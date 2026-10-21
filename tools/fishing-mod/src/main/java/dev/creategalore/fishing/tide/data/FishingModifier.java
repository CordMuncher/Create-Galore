/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing (types in one file, plain map instead of a registry).
 */
package dev.creategalore.fishing.tide.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Scales a fish's selection weight at a spot instead of ruling it out: a fish that prefers warm water bites less
 * the further the water is from its preferred temperature.
 */
public abstract class FishingModifier {
    public record Type(ResourceLocation id, MapCodec<? extends FishingModifier> codec) {
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    private static final Codec<Type> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(id -> {
        Type type = TYPES.get(id.getPath());
        return type != null ? DataResult.success(type) : DataResult.error(() -> "unknown fishing modifier " + id);
    }, Type::id);

    public static final Codec<FishingModifier> CODEC = Codec.lazyInitialized(
            () -> TYPE_CODEC.dispatch(FishingModifier::type, Type::codec));

    public abstract Type type();

    /** Returns the weight after this modifier; 0 or less means the fish won't bite here. */
    public abstract double apply(double weight, FishingContext context);

    private static <T extends FishingModifier> Type register(String name, MapCodec<T> codec) {
        Type type = new Type(ResourceLocation.fromNamespaceAndPath("tide", name), codec);
        TYPES.put(name, type);
        return type;
    }

    /** Full weight at the preferred temperature, falling to 0 at {@code preferred +- tolerance}. */
    public static final class Temperature extends FishingModifier {
        static final Type TYPE = register("temperature", RecordCodecBuilder.<Temperature>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("preferred_temperature").forGetter((Temperature m) -> m.preferred),
                Codec.FLOAT.fieldOf("temperature_tolerance").forGetter((Temperature m) -> m.tolerance)).apply(i, Temperature::new)));
        final float preferred;
        final float tolerance;

        public float preferred() {
            return preferred;
        }

        public float tolerance() {
            return tolerance;
        }

        public Temperature(float preferred, float tolerance) {
            this.preferred = preferred;
            this.tolerance = tolerance;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public double apply(double weight, FishingContext context) {
            double inner = Math.abs((context.temperature() - preferred) / tolerance);
            return weight * Math.max(0, 1 - inner * inner);
        }
    }

    /** Multiplies the weight when a condition passes. */
    public static final class Conditional extends FishingModifier {
        static final Type TYPE = register("conditional", RecordCodecBuilder.<Conditional>mapCodec(i -> i.group(
                FishingCondition.CODEC.fieldOf("if").forGetter((Conditional m) -> m.condition),
                Codec.FLOAT.fieldOf("multiplier").forGetter((Conditional m) -> m.multiplier)).apply(i, Conditional::new)));
        final FishingCondition condition;
        final float multiplier;

        public Conditional(FishingCondition condition, float multiplier) {
            this.condition = condition;
            this.multiplier = multiplier;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public double apply(double weight, FishingContext context) {
            return condition.test(context) ? weight * multiplier : weight;
        }
    }

    public static void bootstrap() {
        if (Temperature.TYPE == null || Conditional.TYPE == null) throw new IllegalStateException();
    }
}
