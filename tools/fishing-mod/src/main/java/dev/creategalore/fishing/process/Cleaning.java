package dev.creategalore.fishing.process;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.sim.SizeClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What cleaning a fish at the Cleaning Table gives, and how many knife strokes it takes. Both follow the fish's
 * weight: a minnow is two strokes and a fillet, a 10 kg salmon eight strokes and a stack of fillets and steaks with
 * roe, bones and scraps, a big shark two dozen strokes and its skin.
 *
 * <p>Meat is {@link #MEAT_FRACTION} of the weight. It comes as pieces in proportion to the square root of the meat
 * (so huge fish give a lot, not an absurd lot): fillets for fish under {@link #STEAKS_FROM_KG}, mostly steaks above
 * (Hybrid Aquatic's, when it is installed). The other parts are by tag: {@link #ROE_FISH} may carry roe,
 * {@link #OILY_FISH} give more scraps (for oil), {@link #SHARKS} give skin.
 */
public final class Cleaning {
    private Cleaning() {
    }

    public static final TagKey<Item> ROE_FISH = tag("roe_fish");
    public static final TagKey<Item> OILY_FISH = tag("oily_fish");
    public static final TagKey<Item> SHARKS = tag("sharks");

    static final double MEAT_FRACTION = 0.45;
    static final double STEAKS_FROM_KG = 3;
    static final int MOST_PIECES = 24;
    static final int MOST_STROKES = 24;
    static final double ROE_CHANCE = 0.4;

    private static final ResourceLocation FILLET = ResourceLocation.fromNamespaceAndPath("aquaculture", "fish_fillet_raw");
    private static final ResourceLocation BONES = ResourceLocation.fromNamespaceAndPath("aquaculture", "fish_bones");
    private static final ResourceLocation STEAK = ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", "raw_fish_steak");
    private static final ResourceLocation AQUACULTURE_WEIGHT = ResourceLocation.fromNamespaceAndPath("aquaculture", "fish_weight");

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, CreateGaloreFishing.id(name));
    }

    /** Weight assumed for a fish no species data covers. */
    static final double UNKNOWN_KG = 0.5;

    /** True for anything the table can clean: a fish item of any mod. */
    public static boolean isFish(ItemStack stack) {
        return !stack.isEmpty() && (FishData.get(stack).isPresent() || SpeciesManager.of(stack).isPresent());
    }

    /**
     * The fish's weight: exact if it was kept (a big fish, a trophy, Tide's or Aquaculture's own weight), otherwise
     * the usual weight of its size class (of a common fish if it was never caught with a rod). A catch without exact
     * numbers was under {@link CatchInfo#EXACT_FROM_KG} when it was landed, so its guess stays under that too.
     * Server side.
     */
    public static double kg(ItemStack fish) {
        Optional<CatchInfo> info = CatchInfo.read(fish);
        if (info.isPresent() && info.get().kg().isPresent()) return info.get().kg().get();
        Float aquaculture = aquacultureWeight(fish);
        if (aquaculture != null && aquaculture > 0) return aquaculture;
        SizeClass size = info.map(CatchInfo::size).orElse(SizeClass.COMMON);
        double usual = SpeciesManager.of(fish).map(Species::size).map(s -> s.weightKg(s.usualLengthCm(size))).orElse(UNKNOWN_KG);
        return info.isPresent() ? Math.min(usual, CatchInfo.EXACT_FROM_KG * 0.95) : usual;
    }

    @SuppressWarnings("unchecked")
    private static Float aquacultureWeight(ItemStack fish) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(AQUACULTURE_WEIGHT);
        return type == null ? null : fish.get((DataComponentType<Float>) type);
    }

    /** Knife strokes to clean a fish of {@code kg}: two for a minnow, five at 3 kg, sixteen at 50 kg. */
    public static int strokes(double kg) {
        return Mth.clamp(1 + (int) Math.ceil(2 * Math.sqrt(kg)), 2, MOST_STROKES);
    }

    /** Everything the fish gives, in the order it comes off the table. */
    public static List<ItemStack> yields(ItemStack fish, double kg, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        double meat = kg * MEAT_FRACTION;
        int pieces = Mth.clamp((int) Math.round(4 * Math.sqrt(meat)), 1, MOST_PIECES);
        Item steak = item(STEAK);
        int steaks = kg >= STEAKS_FROM_KG && steak != null ? (int) Math.round(pieces * 0.6) : 0;
        add(out, item(FILLET), pieces - steaks, FishingRegistry.FISH_SCRAPS.get());
        add(out, steak, steaks, null);
        if (fish.is(ROE_FISH) && random.nextDouble() < ROE_CHANCE) {
            add(out, FishingRegistry.FISH_ROE.get(), Math.min(6, 1 + (int) (kg / 6)), null);
        }
        add(out, item(BONES), Math.min(8, 1 + (int) (kg / 4)), Items.BONE_MEAL);
        int scraps = Math.min(10, 1 + (int) Math.round(Math.sqrt(kg)));
        if (fish.is(OILY_FISH)) scraps = Math.min(15, (int) Math.ceil(scraps * 1.5));
        add(out, FishingRegistry.FISH_SCRAPS.get(), scraps, null);
        if (fish.is(SHARKS)) add(out, FishingRegistry.SHARK_SKIN.get(), Math.min(4, 1 + (int) (kg / 40)), null);
        return out;
    }

    private static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static void add(List<ItemStack> out, Item item, int count, Item fallback) {
        Item use = item != null ? item : fallback;
        if (use == null || count <= 0) return;
        while (count > 0) {
            int n = Math.min(count, use.getDefaultMaxStackSize());
            out.add(new ItemStack(use, n));
            count -= n;
        }
    }
}
