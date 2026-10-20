package dev.creategalore.fishing.fish;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What is known about one caught fish, stored on its item.
 *
 * <p>Small fish (under {@link #EXACT_FROM_KG}) keep only their size class, so fish of one species and class stack.
 * Heavier fish and every trophy keep their exact length, weight, who caught them and on which day, and stack to 1:
 * a 40 kg tuna is its own item anyway, and the numbers are the brag.
 *
 * @param size    size class
 * @param lengthMm exact length in millimetres (big fish and trophies only)
 * @param grams    exact weight in grams (big fish and trophies only)
 * @param angler   name of the angler (big fish and trophies only)
 * @param day      world day it was caught (big fish and trophies only)
 */
public record CatchInfo(SizeClass size, Optional<Integer> lengthMm, Optional<Integer> grams, Optional<String> angler,
                        Optional<Long> day) {
    /** Fish at least this heavy keep their exact numbers. */
    public static final double EXACT_FROM_KG = 3.0;

    private static final Codec<SizeClass> SIZE_CODEC = Codec.STRING.xmap(
            name -> SizeClass.valueOf(name.toUpperCase(Locale.ROOT)), SizeClass::key);

    public static final Codec<CatchInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            SIZE_CODEC.fieldOf("size").forGetter(CatchInfo::size),
            Codec.INT.optionalFieldOf("length_mm").forGetter(CatchInfo::lengthMm),
            Codec.INT.optionalFieldOf("grams").forGetter(CatchInfo::grams),
            Codec.STRING.optionalFieldOf("angler").forGetter(CatchInfo::angler),
            Codec.LONG.optionalFieldOf("day").forGetter(CatchInfo::day)
    ).apply(i, CatchInfo::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CatchInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> SizeClass.values()[i], SizeClass::ordinal), CatchInfo::size,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs::optional), CatchInfo::lengthMm,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs::optional), CatchInfo::grams,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), CatchInfo::angler,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs::optional), CatchInfo::day,
            CatchInfo::new);

    /** The info for a fish of this length and weight: exact numbers only when it is big or a trophy. */
    public static CatchInfo of(SizeClass size, double lengthCm, double kg, String angler, long day) {
        if (kg < EXACT_FROM_KG && !size.isTrophy()) {
            return new CatchInfo(size, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
        // No angler for a fish no one landed (a Fishing Net's catch, an old Tide fish).
        return new CatchInfo(size, Optional.of((int) Math.round(lengthCm * 10)), Optional.of((int) Math.round(kg * 1000)),
                angler.isEmpty() ? Optional.empty() : Optional.of(angler), Optional.of(day));
    }

    /**
     * What is known about the fish on {@code stack}: its catch info, or for a fish caught with Tide (only Tide's
     * length on it) the info its length gives through the species' length-weight curve. Server side (the client has
     * no species table); empty for anything that isn't a known fish.
     */
    public static Optional<CatchInfo> read(ItemStack stack) {
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        if (info != null) return Optional.of(info);
        Double cm = stack.get(TideDataComponents.FISH_LENGTH);
        if (cm == null || cm <= 0) return Optional.empty();
        return SpeciesManager.of(stack).map(s -> of(s.size().classify(cm), cm, s.size().weightKg(cm), "", 0));
    }

    public boolean exact() {
        return grams.isPresent();
    }

    public Optional<Double> kg() {
        return grams.map(g -> g / 1000.0);
    }

    public Optional<Double> cm() {
        return lengthMm.map(mm -> mm / 10.0);
    }

    /** "2.45 kg", "870 g". */
    public static String weightText(double kg) {
        if (kg < 1) return Math.round(kg * 1000) + " g";
        if (kg < 100) return String.format(Locale.ROOT, "%.2f kg", kg);
        return String.format(Locale.ROOT, "%.0f kg", kg);
    }
}
