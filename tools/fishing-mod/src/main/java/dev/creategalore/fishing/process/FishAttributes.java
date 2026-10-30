package dev.creategalore.fishing.process;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.sim.SizeClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What a fish offers Create's Attribute Filter when it is put in as the reference item, so filters (and through them
 * funnels, arms and the rest) can sort a catch:
 *
 * <ul>
 * <li>{@link Whole}: small enough to cook whole, or too big to (the furnace's, smoker's and press's rule, see
 * {@link WholeFishIngredient}).</li>
 * <li>{@link Weight}: the reference fish's weight as "or more" and "or less". Only a fish that shows its weight has
 * one to offer; a fish under {@link CatchInfo#EXACT_FROM_KG} that keeps only its size class is matched by the usual
 * weight of its class, which is always under that.</li>
 * <li>{@link Size}: its size class (Young to Unique).</li>
 * </ul>
 */
public final class FishAttributes {
    public static final ItemAttributeType WHOLE = new Type<>(Whole.CODEC, Whole.STREAM_CODEC, new Whole(true)) {
        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return Cleaning.isFish(stack) ? List.of(new Whole(WholeFishIngredient.small(stack))) : List.of();
        }
    };
    public static final ItemAttributeType WEIGHT = new Type<>(Weight.CODEC, Weight.STREAM_CODEC, new Weight(3000, true)) {
        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            CatchInfo info = stack.get(FishingRegistry.CATCH.get());
            if (info == null || info.grams().isEmpty() || !Cleaning.isFish(stack)) return List.of();
            List<ItemAttribute> out = new ArrayList<>();
            out.add(new Weight(info.grams().get(), true));
            out.add(new Weight(info.grams().get(), false));
            return out;
        }
    };
    public static final ItemAttributeType SIZE = new Type<>(Size.CODEC, Size.STREAM_CODEC, new Size(SizeClass.COMMON)) {
        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            CatchInfo info = stack.get(FishingRegistry.CATCH.get());
            return info == null ? List.of() : List.of(new Size(info.size()));
        }
    };

    private FishAttributes() {
    }

    /** Small enough to cook whole ({@code small}), or a fish too big to. */
    public record Whole(boolean small) implements ItemAttribute {
        static final MapCodec<Whole> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("small").forGetter(Whole::small)).apply(i, Whole::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Whole> STREAM_CODEC = ByteBufCodecs.BOOL
                .<RegistryFriendlyByteBuf>cast().map(Whole::new, Whole::small);

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            return Cleaning.isFish(stack) && WholeFishIngredient.small(stack) == small;
        }

        @Override
        public ItemAttributeType getType() {
            return WHOLE;
        }

        @Override
        public String getTranslationKey() {
            return small ? "create_galore_fishing.cookable_whole" : "create_galore_fishing.too_big_whole";
        }
    }

    /** At least ({@code atLeast}) or at most {@code grams}. */
    public record Weight(int grams, boolean atLeast) implements ItemAttribute {
        static final MapCodec<Weight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("grams").forGetter(Weight::grams),
                Codec.BOOL.fieldOf("at_least").forGetter(Weight::atLeast)).apply(i, Weight::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Weight> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Weight::grams, ByteBufCodecs.BOOL, Weight::atLeast, Weight::new);

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            if (!Cleaning.isFish(stack)) return false;
            // Rounded to the gram as stored, so the reference fish matches both.
            long g = Math.round(Cleaning.kg(stack) * 1000);
            return atLeast ? g >= grams : g <= grams;
        }

        @Override
        public ItemAttributeType getType() {
            return WEIGHT;
        }

        @Override
        public String getTranslationKey() {
            return atLeast ? "create_galore_fishing.weight_at_least" : "create_galore_fishing.weight_at_most";
        }

        @Override
        public Object[] getTranslationParameters() {
            return new Object[]{CatchInfo.weightText(grams / 1000.0)};
        }
    }

    /** Of this size class. */
    public record Size(SizeClass size) implements ItemAttribute {
        static final MapCodec<Size> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.xmap(s -> SizeClass.valueOf(s.toUpperCase(Locale.ROOT)), SizeClass::key).fieldOf("size")
                        .forGetter(Size::size)).apply(i, Size::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Size> STREAM_CODEC = ByteBufCodecs.VAR_INT
                .<RegistryFriendlyByteBuf>cast().map(n -> new Size(SizeClass.values()[n]), s -> s.size().ordinal());

        @Override
        public boolean appliesTo(ItemStack stack, Level level) {
            CatchInfo info = stack.get(FishingRegistry.CATCH.get());
            return info != null && info.size() == size;
        }

        @Override
        public ItemAttributeType getType() {
            return SIZE;
        }

        @Override
        public String getTranslationKey() {
            return "create_galore_fishing.size_class." + size.key();
        }
    }

    /** An attribute type: its codecs, and the attribute a filter starts from before it reads its saved one. */
    private abstract static class Type<A extends ItemAttribute> implements ItemAttributeType {
        private final MapCodec<A> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, A> streamCodec;
        private final A blank;

        Type(MapCodec<A> codec, StreamCodec<RegistryFriendlyByteBuf, A> streamCodec, A blank) {
            this.codec = codec;
            this.streamCodec = streamCodec;
            this.blank = blank;
        }

        @Override
        public ItemAttribute createAttribute() {
            return blank;
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return codec;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return streamCodec;
        }
    }
}
