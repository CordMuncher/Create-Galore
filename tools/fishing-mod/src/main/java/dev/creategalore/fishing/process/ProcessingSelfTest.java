package dev.creategalore.fishing.process;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.bait.BaitKind;
import dev.creategalore.fishing.bait.Chum;
import dev.creategalore.fishing.bait.LiveBait;
import dev.creategalore.fishing.bait.LiveBaitBucketItem;
import dev.creategalore.fishing.bait.ThrownChum;import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.compat.IntegratedFarmingNets;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.RegistryManager;

/**
 * {@code /cgfish selftest processing}: the bait and processing recipes load, every fish cooking recipe that could
 * take a big fish is weight-limited and refuses one, the Cleaning Table's strokes and yields follow the weight and a
 * table worked by a fake player (as a Deployer is) fills its outputs, bait from a Live-Bait Bucket counts as live,
 * chum speeds bites near it only, fish oil is Blaze Burner fuel, and Fishing Nets have our catch provider.
 */
public final class ProcessingSelfTest {
    private ProcessingSelfTest() {
    }

    /** Recipes gen_processing.py writes in this mod's namespace. */
    static final List<String> RECIPES = List.of("splashing/dirt", "splashing/coarse_dirt", "splashing/rooted_dirt",
            "splashing/mud", "mixing/maggots", "mixing/boilies", "mixing/chum", "milling/fish_meal_from_scraps",
            "milling/fish_meal_from_bones", "compacting/fish_oil_from_scraps", "compacting/fish_oil_from_oily_fish",
            "crafting/cleaning_table", "crafting/live_bait_bucket", "smoking/leather_from_shark_skin",
            "campfire_cooking/leather_from_shark_skin");

    public static void register() {
        SelfTest.suite("processing", ProcessingSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        for (String name : RECIPES) {
            r.check(server.getRecipeManager().byKey(CreateGaloreFishing.id(name)).isPresent(), "recipe " + name + " loaded");
        }
        cooking(server, r);
        cleaning(r);
        table(server.overworld(), r);
        liveBait(r);
        chum(server.overworld(), r);
        if (ModList.get().isLoaded("createliquidfuel")) {
            var fuel = RegistryManager.getDataMap(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("createliquidfuel", "liquid_fuel"));
            r.check(fuel != null && BuiltInRegistries.FLUID.wrapAsHolder(FishingRegistry.FISH_OIL.get()).getData(fuel) != null,
                    "fish oil is in Create: Liquid Fuel's fuel map");
            r.check(fuel != null && BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA).getData(fuel) != null,
                    "and the map's own entries are kept (lava): our file adds to theirs");
        }
        if (ModList.get().isLoaded(IntegratedFarmingNets.MOD)) {
            r.check(IntegratedFarmingNets.registered(), "Fishing Nets have our catch provider");
        }
    }

    /**
     * Every smelting, smoking and campfire recipe that takes a fish that can reach the weight limit must use the
     * whole-fish ingredient (a new mod's fish recipe that doesn't shows up here), and the furnace must refuse a big
     * fish while still cooking a small one.
     */
    private static void cooking(MinecraftServer server, SelfTest.Report r) {
        int limited = 0;
        for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (holder.value().getType() != type || !(holder.value() instanceof AbstractCookingRecipe cook)) continue;
                Ingredient in = cook.getIngredients().get(0);
                boolean bigFish = false;
                for (ItemStack s : in.getItems()) {
                    Optional<Species> species = SpeciesManager.of(s);
                    if (species.isPresent() && species.get().size().weightKg(species.get().size().record()) >= WholeFishIngredient.MAX_KG) {
                        bigFish = true;
                    }
                }
                if (!bigFish) continue;
                if (in.getCustomIngredient() instanceof WholeFishIngredient) limited++;
                else r.fail(holder.id() + " cooks big fish whole (add it to gen_processing.py)");
            }
        }
        r.check(limited >= 12, limited + " fish cooking recipes are weight-limited");
        ServerLevel level = server.overworld();
        ItemStack small = new ItemStack(Items.SALMON);
        ItemStack trophy = caught(Items.SALMON, SizeClass.TROPHY, 2.5);
        ItemStack big = caught(Items.SALMON, SizeClass.LARGE, 6);
        var smelting = server.getRecipeManager();
        r.check(smelting.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(small), level).isPresent(), "a salmon cooks whole");
        r.check(smelting.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(trophy), level).isPresent(), "a 2.5 kg trophy salmon cooks whole");
        r.check(smelting.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(big), level).isEmpty(), "a 6 kg salmon doesn't fit the furnace");
        r.check(smelting.getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(big), level).isEmpty(), "nor the smoker");
    }

    private static ItemStack caught(Item item, SizeClass size, double kg) {
        ItemStack fish = new ItemStack(item);
        Species species = SpeciesManager.of(fish).orElse(null);
        double cm = species == null ? 60 : species.size().lengthCm(kg);
        fish.set(FishingRegistry.CATCH.get(), CatchInfo.of(size, cm, kg, "Tester", 1));
        return fish;
    }

    private static void cleaning(SelfTest.Report r) {
        r.check(Cleaning.strokes(0.05) == 2 && Cleaning.strokes(3) == 5 && Cleaning.strokes(50) == 16
                && Cleaning.strokes(5000) == Cleaning.MOST_STROKES, "knife strokes grow with weight (2, 5, 16, capped)");
        RandomSource rng = RandomSource.create(7);
        List<ItemStack> minnow = Cleaning.yields(new ItemStack(Items.COD), 0.05, rng);
        r.check(count(minnow, FishingRegistry.FISH_SCRAPS.get()) >= 1 && pieces(minnow) == 1,
                "a minnow-sized fish gives one fillet and scraps: " + describe(minnow));
        ItemStack salmon = caught(Items.SALMON, SizeClass.LARGE, 10);
        r.check(Math.abs(Cleaning.kg(salmon) - 10) < 0.01, "an exact catch is cleaned at its own weight");
        List<ItemStack> big = Cleaning.yields(salmon, 10, rng);
        Item steak = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", "raw_fish_steak"));
        if (ModList.get().isLoaded("hybrid_aquatic")) r.check(count(big, steak) > 0, "a 10 kg salmon gives steaks: " + describe(big));
        r.check(pieces(big) >= 6 && pieces(big) <= 10, "a 10 kg salmon gives 6 to 10 pieces of meat: " + pieces(big));
        boolean roe = false;
        for (int i = 0; i < 40 && !roe; i++) roe = count(Cleaning.yields(salmon, 10, rng), FishingRegistry.FISH_ROE.get()) > 0;
        r.check(roe, "salmon sometimes carry roe");
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("tide", "great_white_shark")).ifPresent(shark -> {
            List<ItemStack> parts = Cleaning.yields(new ItemStack(shark), 400, rng);
            r.check(count(parts, FishingRegistry.SHARK_SKIN.get()) > 0, "a shark gives its skin: " + describe(parts));
            r.check(pieces(parts) <= Cleaning.MOST_PIECES, "even a 400 kg shark gives at most " + Cleaning.MOST_PIECES + " pieces");
        });
        double plain = Cleaning.kg(new ItemStack(Items.COD));
        r.check(plain > 0.3, "a cod with no catch data is cleaned at a common cod's weight: " + CatchInfo.weightText(plain));
        ItemStack stacked = new ItemStack(Items.COD);
        stacked.set(FishingRegistry.CATCH.get(), CatchInfo.of(SizeClass.COMMON, 50, 1.2, "Tester", 1));
        r.check(Cleaning.kg(stacked) < CatchInfo.EXACT_FROM_KG,
                "a stacked catch (no exact weight) is guessed under " + CatchInfo.EXACT_FROM_KG + " kg: " + CatchInfo.weightText(Cleaning.kg(stacked)));
    }

    /** A real table in the world, worked the way a Deployer works it: fish in through the item handler, then strokes. */
    private static void table(ServerLevel level, SelfTest.Report r) {
        BlockPos pos = new BlockPos(0, level.getMaxBuildHeight() - 2, 0);
        level.setBlockAndUpdate(pos, FishingRegistry.CLEANING_TABLE.get().defaultBlockState());
        try {
            if (!(level.getBlockEntity(pos) instanceof CleaningTableBlockEntity table)) {
                r.fail("the Cleaning Table has its block entity");
                return;
            }
            IItemHandler items = table.items();
            ItemStack salmon = caught(Items.SALMON, SizeClass.LARGE, 4);
            r.check(items.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "the table refuses what isn't a fish");
            r.check(items.insertItem(0, salmon.copy(), false).isEmpty() && !table.fish().isEmpty(), "a funnel can lay a fish on the table");
            r.check(items.insertItem(0, salmon.copy(), true).getCount() == 1, "only one fish at a time");
            FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
            int strokes = 0;
            while (!table.fish().isEmpty() && strokes < 50) {
                table.stroke(deployer);
                strokes++;
            }
            r.check(strokes == Cleaning.strokes(4), "a 4 kg fish took " + strokes + " strokes");
            int out = 0;
            for (int slot = 1; slot < items.getSlots(); slot++) out += items.extractItem(slot, 64, false).getCount();
            r.check(out > 0 && !table.hasOutputs(), "the parts wait in the outputs for a hopper (" + out + " items)");
        } finally {
            level.removeBlock(pos, false);
        }
    }

    private static void liveBait(SelfTest.Report r) {
        Item minnow = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aquaculture", "minnow"));
        if (minnow == Items.AIR) {
            r.info("Aquaculture isn't installed: live bait not checked");
            return;
        }
        r.check(new ItemStack(minnow).is(LiveBait.TAG), "minnows go in the Live-Bait Bucket");
        r.check(!new ItemStack(Items.COD).is(LiveBait.TAG), "a cod doesn't");
        ItemStack bucket = new ItemStack(FishingRegistry.LIVE_BAIT_BUCKET.get());
        // The bundle code does nothing for an item without the component, so a new bucket must carry an empty one.
        r.check(BundleContents.EMPTY.equals(bucket.get(DataComponents.BUNDLE_CONTENTS)), "a new Live-Bait Bucket is an empty bundle");
        bucket.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(minnow, 20))));
        ItemStack taken = LiveBaitBucketItem.takeBait(bucket, RodItem.BAIT_CAPACITY, ItemStack.EMPTY);
        r.check(taken.getCount() == RodItem.BAIT_CAPACITY && LiveBait.isLive(taken), "16 live minnows come out of the bucket");
        r.check(bucket.get(DataComponents.BUNDLE_CONTENTS).itemCopyStream().mapToInt(ItemStack::getCount).sum() == 4,
                "four stay in it");
        ItemStack rod = new ItemStack(FishingRegistry.WOODEN_ROD.get());
        RodItem.setParts(rod, RodParts.EMPTY.with(RodParts.Slot.BAIT, taken));
        Optional<TacklePart.Bait> live = RodStats.of(rod).flatMap(RodStats::bait);
        r.check(live.isPresent() && live.get().live() && live.get().bait() == BaitKind.LIVE_FISH,
                "on the rod they are live fish: " + live.map(TacklePart.Bait::bait).orElse(null));
        RodItem.setParts(rod, RodParts.EMPTY.with(RodParts.Slot.BAIT, LiveBait.dead(taken)));
        Optional<TacklePart.Bait> dead = RodStats.of(rod).flatMap(RodStats::bait);
        r.check(dead.isPresent() && !dead.get().live() && dead.get().bait() == BaitKind.CUT_BAIT, "a loose minnow is cut bait");
    }

    private static void chum(ServerLevel level, SelfTest.Report r) {
        Vec3 spot = new Vec3(1_000_000.5, 62, 1_000_000.5);
        Chum.spread(level, spot);
        double here = Chum.factor(level, spot);
        double near = Chum.factor(level, spot.add(4, 0, 0));
        double far = Chum.factor(level, spot.add(Chum.RADIUS + 1, 0, 0));
        r.check(here > 1.7 && near > 1.2 && near < here && far == 1,
                String.format(java.util.Locale.ROOT, "chum: bites x%.2f at the spot, x%.2f 4 blocks off, x%.2f outside", here, near, far));
        r.check(Chum.around(level, spot) == 1, "one chum spot is active there");
        // A real throw: a ball of chum dropped onto a water block spreads when it gets there.
        BlockPos water = new BlockPos(4, level.getMaxBuildHeight() - 3, 0);
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        try {
            ThrownChum ball = new ThrownChum(level, water.getX() + 0.5, water.getY() + 2.5, water.getZ() + 0.5);
            ball.setDeltaMovement(0, -0.6, 0);
            level.addFreshEntity(ball);
            for (int t = 0; t < 20 && !ball.isRemoved(); t++) ball.tick();
            r.check(ball.isRemoved() && Chum.around(level, Vec3.atCenterOf(water)) == 1, "a thrown ball of chum spreads in the water it lands in");
        } finally {
            level.removeBlock(water, false);
        }
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Fillets and steaks (the meat). */
    private static int pieces(List<ItemStack> stacks) {
        return stacks.stream().filter(s -> {
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            return path.equals("fish_fillet_raw") || path.equals("raw_fish_steak");
        }).mapToInt(ItemStack::getCount).sum();
    }

    private static String describe(List<ItemStack> stacks) {
        return String.join(", ", stacks.stream().map(s -> s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).toList());
    }
}
