/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing: the condition types live in one file and are
 * looked up from a plain map instead of a game registry, so the "tide:" type ids in
 * existing fish data keep working whether or not Tide is installed.
 */
package dev.creategalore.fishing.tide.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/**
 * A test on the fishing spot that a fish's data lists; the fish can only bite where all of its conditions pass.
 * In JSON: {@code {"type": "tide:found_in", "biomes": ["#minecraft:is_ocean"]}}. Type ids without a namespace, or in
 * this mod's namespace, mean the same as the {@code tide:} ones.
 */
public abstract class FishingCondition {
    /** A condition type: its id and how to read it. */
    public record Type(ResourceLocation id, MapCodec<? extends FishingCondition> codec) {
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    private static final Codec<Type> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(id -> {
        Type type = TYPES.get(id.getPath());
        return type != null ? DataResult.success(type) : DataResult.error(() -> "unknown fishing condition " + id);
    }, Type::id);

    public static final Codec<FishingCondition> CODEC = Codec.lazyInitialized(
            () -> TYPE_CODEC.dispatch(FishingCondition::type, Type::codec));

    /** Biomes that count as salt water (Tide's tag, shipped by this mod once Tide is gone). */
    public static final TagKey<Biome> IS_SALTWATER = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("tide", "is_saltwater"));

    public abstract Type type();

    public abstract boolean test(FishingContext context);

    private static <T extends FishingCondition> Type register(String name, MapCodec<T> codec) {
        Type type = new Type(ResourceLocation.fromNamespaceAndPath("tide", name), codec);
        TYPES.put(name, type);
        return type;
    }

    // ---- Logic ----

    public static final class Either extends FishingCondition {
        static final Type TYPE = register("either", RecordCodecBuilder.<Either>mapCodec(i -> i.group(
                CODEC.fieldOf("a").forGetter((Either c) -> c.a),
                CODEC.fieldOf("b").forGetter((Either c) -> c.b)).apply(i, Either::new)));
        final FishingCondition a;
        final FishingCondition b;

        public Either(FishingCondition a, FishingCondition b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return a.test(context) || b.test(context);
        }
    }

    public static final class Not extends FishingCondition {
        static final Type TYPE = register("not", RecordCodecBuilder.<Not>mapCodec(i -> i.group(
                CODEC.fieldOf("a").forGetter((Not c) -> c.a)).apply(i, Not::new)));
        final FishingCondition a;

        public Not(FishingCondition a) {
            this.a = a;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return !a.test(context);
        }
    }

    // ---- Water and place ----

    public static final class Freshwater extends FishingCondition {
        static final Type TYPE = register("freshwater", MapCodec.unit(Freshwater::new));

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return !context.exactBiome().is(IS_SALTWATER);
        }
    }

    public static final class Saltwater extends FishingCondition {
        static final Type TYPE = register("saltwater", MapCodec.unit(Saltwater::new));

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.exactBiome().is(IS_SALTWATER);
        }
    }

    /** {@code "fluid": "water" | "lava" | "void"}. */
    public static final class Medium extends FishingCondition {
        static final Type TYPE = register("fluid", RecordCodecBuilder.<Medium>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("fluid").forGetter((Medium c) -> c.medium)).apply(i, Medium::new)));
        final String medium;

        public Medium(String medium) {
            this.medium = medium;
        }

        public String medium() {
            return medium;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.medium().equals(medium);
        }
    }

    public static final class Dimensions extends FishingCondition {
        static final Type TYPE = register("dimension", RecordCodecBuilder.<Dimensions>mapCodec(i -> i.group(
                Level.RESOURCE_KEY_CODEC.listOf().fieldOf("dimensions").forGetter((Dimensions c) -> c.dimensions)).apply(i, Dimensions::new)));
        final List<ResourceKey<Level>> dimensions;

        public Dimensions(List<ResourceKey<Level>> dimensions) {
            this.dimensions = dimensions;
        }

        public List<ResourceKey<Level>> dimensions() {
            return dimensions;
        }

        public boolean isOverworldOnly() {
            return dimensions.size() == 1 && dimensions.get(0) == Level.OVERWORLD;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return dimensions.contains(context.dimension());
        }
    }

    /** Biomes or biome tags; matches the biome at the hook or the nearest land biome. */
    public static final class FoundIn extends FishingCondition {
        static final Type TYPE = register("found_in", RecordCodecBuilder.<FoundIn>mapCodec(i -> i.group(
                ExtraCodecs.TAG_OR_ELEMENT_ID.listOf().fieldOf("biomes").forGetter(FoundIn::toData)).apply(i, FoundIn::fromData)));
        final List<TagKey<Biome>> tags;
        final List<ResourceLocation> biomes;

        FoundIn(List<TagKey<Biome>> tags, List<ResourceLocation> biomes) {
            this.tags = tags;
            this.biomes = biomes;
        }

        static FoundIn fromData(List<ExtraCodecs.TagOrElementLocation> data) {
            List<TagKey<Biome>> tags = new ArrayList<>();
            List<ResourceLocation> biomes = new ArrayList<>();
            for (ExtraCodecs.TagOrElementLocation entry : data) {
                if (entry.tag()) tags.add(TagKey.create(Registries.BIOME, entry.id()));
                else biomes.add(entry.id());
            }
            return new FoundIn(tags, biomes);
        }

        List<ExtraCodecs.TagOrElementLocation> toData() {
            List<ExtraCodecs.TagOrElementLocation> data = new ArrayList<>();
            tags.forEach(t -> data.add(new ExtraCodecs.TagOrElementLocation(t.location(), true)));
            biomes.forEach(b -> data.add(new ExtraCodecs.TagOrElementLocation(b, false)));
            return data;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            for (TagKey<Biome> tag : tags) {
                if (context.nearestBiome().is(tag) || context.exactBiome().is(tag)) return true;
            }
            for (ResourceLocation id : biomes) {
                if (context.nearestBiome().is(id) || context.exactBiome().is(id)) return true;
            }
            return false;
        }
    }

    public static final class Structures extends FishingCondition {
        static final Type TYPE = register("found_in_structures", RecordCodecBuilder.<Structures>mapCodec(i -> i.group(
                ResourceKey.codec(Registries.STRUCTURE).listOf().fieldOf("structures").forGetter((Structures c) -> c.structures)).apply(i, Structures::new)));
        final List<ResourceKey<Structure>> structures;

        public Structures(List<ResourceKey<Structure>> structures) {
            this.structures = structures;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.level().structureManager().getStructureWithPieceAt(context.blockPos(),
                    holder -> structures.stream().anyMatch(holder::is)).isValid();
        }
    }

    public static final class BlockNearby extends FishingCondition {
        static final Type TYPE = register("block_nearby", RecordCodecBuilder.<BlockNearby>mapCodec(i -> i.group(
                TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter((BlockNearby c) -> c.tag),
                Codec.INT.fieldOf("radius").forGetter((BlockNearby c) -> c.radius)).apply(i, BlockNearby::new)));
        final TagKey<Block> tag;
        final int radius;

        public BlockNearby(TagKey<Block> tag, int radius) {
            this.tag = tag;
            this.radius = radius;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            AABB area = AABB.ofSize(context.pos(), 1, 1, 1).inflate(radius);
            return context.level().getBlockStates(area).anyMatch(state -> state.is(tag));
        }
    }

    public static final class OpenWater extends FishingCondition {
        static final Type TYPE = register("open_water", RecordCodecBuilder.<OpenWater>mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("in_open_water").forGetter((OpenWater c) -> c.inOpenWater)).apply(i, OpenWater::new)));
        final boolean inOpenWater;

        public OpenWater(boolean inOpenWater) {
            this.inOpenWater = inOpenWater;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.openWater() == inOpenWater;
        }
    }

    // ---- Height ----

    /** Anything with a y bound, so tools can show where a fish lives. */
    public interface DepthRange {
        int minY();

        int maxY();
    }

    public static final class Above extends FishingCondition implements DepthRange {
        static final Type TYPE = register("above", RecordCodecBuilder.<Above>mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter((Above c) -> c.y)).apply(i, Above::new)));
        final int y;

        public Above(int y) {
            this.y = y;
        }

        @Override
        public int minY() {
            return y;
        }

        @Override
        public int maxY() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.blockPos().getY() > y;
        }
    }

    public static final class Below extends FishingCondition implements DepthRange {
        static final Type TYPE = register("below", RecordCodecBuilder.<Below>mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter((Below c) -> c.y)).apply(i, Below::new)));
        final int y;

        public Below(int y) {
            this.y = y;
        }

        @Override
        public int minY() {
            return Integer.MIN_VALUE;
        }

        @Override
        public int maxY() {
            return y;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.blockPos().getY() < y;
        }
    }

    public static final class Between extends FishingCondition implements DepthRange {
        static final Type TYPE = register("depth_range", RecordCodecBuilder.<Between>mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_y").forGetter((Between c) -> c.min),
                Codec.INT.fieldOf("max_y").forGetter((Between c) -> c.max)).apply(i, Between::new)));
        final int min;
        final int max;

        public Between(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public int minY() {
            return min;
        }

        @Override
        public int maxY() {
            return max;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            int y = context.blockPos().getY();
            return y >= min && y <= max;
        }
    }

    // ---- Time, weather, sky ----

    /**
     * A time window in ticks of the day (0-24000). As in Tide, a window whose {@code min} is above its {@code max}
     * covers the ticks from {@code max} to {@code min}.
     */
    public record TimeRange(long min, long max) {
        public static final Codec<TimeRange> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("min").forGetter(TimeRange::min),
                Codec.LONG.fieldOf("max").forGetter(TimeRange::max)).apply(i, TimeRange::new));

        public boolean contains(long time) {
            if (min <= max) return time >= min && time <= max;
            return time <= min && time >= max;
        }
    }

    public static final class TimeOfDay extends FishingCondition {
        static final Type TYPE = register("time_of_day", RecordCodecBuilder.<TimeOfDay>mapCodec(i -> i.group(
                TimeRange.CODEC.listOf().fieldOf("ranges").forGetter((TimeOfDay c) -> c.ranges)).apply(i, TimeOfDay::new)));
        final List<TimeRange> ranges;

        public List<TimeRange> ranges() {
            return ranges;
        }

        public TimeOfDay(List<TimeRange> ranges) {
            this.ranges = ranges;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            long time = context.level().getDayTime() % 24000;
            // The Twilight Hook turns the fish's clock around.
            if (!context.hookItem().isEmpty()
                    && BuiltInRegistries.ITEM.getKey(context.hookItem().getItem()).getPath().equals("twilight_hook")) {
                time = (time + 12000) % 24000;
            }
            for (TimeRange range : ranges) {
                if (range.contains(time)) return true;
            }
            return false;
        }
    }

    public enum WeatherType implements StringRepresentable {
        CLEAR, RAIN, STORM;

        public static final Codec<WeatherType> CODEC = StringRepresentable.fromEnum(WeatherType::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final class Weather extends FishingCondition {
        static final Type TYPE = register("weather", RecordCodecBuilder.<Weather>mapCodec(i -> i.group(
                WeatherType.CODEC.listOf().optionalFieldOf("types", List.of()).forGetter((Weather c) -> c.types)).apply(i, Weather::new)));
        final List<WeatherType> types;

        public List<WeatherType> types() {
            return types;
        }

        public Weather(List<WeatherType> types) {
            this.types = types;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            if (types.contains(WeatherType.CLEAR) && !context.level().isRaining()) return true;
            if (types.contains(WeatherType.RAIN) && context.level().isRaining()) return true;
            return types.contains(WeatherType.STORM) && context.level().isThundering();
        }
    }

    public static final class MoonPhase extends FishingCondition {
        static final Type TYPE = register("moon_phase", RecordCodecBuilder.<MoonPhase>mapCodec(i -> i.group(
                Codec.INT.listOf().optionalFieldOf("any_of", List.of()).forGetter((MoonPhase c) -> c.phases)).apply(i, MoonPhase::new)));
        final List<Integer> phases;

        public List<Integer> phases() {
            return phases;
        }

        public MoonPhase(List<Integer> phases) {
            this.phases = phases;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return phases.contains(context.moonPhase());
        }
    }

    public static final class Seasons extends FishingCondition {
        static final Type TYPE = register("seasons", RecordCodecBuilder.<Seasons>mapCodec(i -> i.group(
                Season.CODEC.listOf().optionalFieldOf("seasons", Arrays.asList(Season.values())).forGetter((Seasons c) -> c.seasons)).apply(i, Seasons::new)));
        final List<Season> seasons;

        public List<Season> seasons() {
            return seasons;
        }

        public Seasons(List<Season> seasons) {
            this.seasons = seasons;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return !Season.seasonsActive() || seasons.contains(context.season());
        }
    }

    // ---- The angler and the rod ----

    public static final class Luck extends FishingCondition {
        static final Type TYPE = register("luck", RecordCodecBuilder.<Luck>mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("min", 0).forGetter((Luck c) -> c.min),
                Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter((Luck c) -> c.max)).apply(i, Luck::new)));
        final int min;
        final int max;

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public Luck(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            return context.luck() >= min && context.luck() <= max;
        }
    }

    public static final class Enchantments extends FishingCondition {
        static final Type TYPE = register("has_enchantments", RecordCodecBuilder.<Enchantments>mapCodec(i -> i.group(
                ResourceKey.codec(Registries.ENCHANTMENT).listOf().optionalFieldOf("enchantments", List.of())
                        .forGetter((Enchantments c) -> c.enchantments)).apply(i, Enchantments::new)));
        final List<ResourceKey<Enchantment>> enchantments;

        public Enchantments(List<ResourceKey<Enchantment>> enchantments) {
            this.enchantments = enchantments;
        }

        @Override
        public Type type() {
            return TYPE;
        }

        @Override
        public boolean test(FishingContext context) {
            if (context.rod() == null || context.rod().isEmpty()) return false;
            ItemEnchantments on = context.rod().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            return on.keySet().stream().anyMatch(e -> enchantments.stream().anyMatch(e::is));
        }
    }

    /** Forces the types to register (they register when their class loads). */
    public static void bootstrap() {
        List<Type> all = List.of(Either.TYPE, Not.TYPE, Freshwater.TYPE, Saltwater.TYPE, Medium.TYPE, Dimensions.TYPE,
                FoundIn.TYPE, Structures.TYPE, BlockNearby.TYPE, OpenWater.TYPE, Above.TYPE, Below.TYPE, Between.TYPE,
                TimeOfDay.TYPE, Weather.TYPE, MoonPhase.TYPE, Seasons.TYPE, Luck.TYPE, Enchantments.TYPE);
        if (all.size() != TYPES.size()) throw new IllegalStateException("fishing condition types registered twice");
    }
}
