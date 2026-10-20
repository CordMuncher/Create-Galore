package dev.creategalore.fishing.compat;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.BiteSampler;
import dev.creategalore.fishing.cast.Catches;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.Season;
import dev.creategalore.fishing.tide.data.fishing.CatchTables;
import dev.creategalore.fishing.tide.data.fishing.CrateData;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.fishing.FishingLootData;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import dev.creategalore.fishing.tide.data.rods.BaitContents;
import dev.creategalore.fishing.tide.registries.TideBlocks;
import dev.creategalore.fishing.tide.registries.TideFish;
import dev.creategalore.fishing.tide.registries.TideItems;
import dev.creategalore.fishing.tide.registries.blocks.entities.FishDisplayBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Self-tests for the Tide port: {@code aliases} (every replaced Tide id loads as its replacement), {@code tide} (each
 * ported fish has its item, bucket, spawn egg, mob, loot and fish data, and Tide's tackle has our tackle data) and
 * {@code crates} (crate and junk/treasure tables exist and roll) and {@code spawns} (Tide's fish are in the biomes'
 * spawn lists, and their spawn rules run on real water without throwing) and {@code components} (a rod rigged with
 * Tide converts to our parts; a fish with only Tide's length still reads as a catch).
 */
public final class TideSelfTest {
    private TideSelfTest() {
    }

    public static void register() {
        SelfTest.suite("aliases", (server, r) -> {
            aliases(r, BuiltInRegistries.ITEM, TideAliases.items());
            aliases(r, BuiltInRegistries.BLOCK, TideAliases.blocks());
            aliases(r, BuiltInRegistries.ENTITY_TYPE, TideAliases.entities());
            writeAliasTable(server, r);
        });
        SelfTest.suite("tide", TideSelfTest::species);
        SelfTest.suite("crates", TideSelfTest::crates);
        SelfTest.suite("spawns", TideSelfTest::spawns);
        SelfTest.suite("components", TideSelfTest::components);
    }

    /**
     * {@code create_galore_fishing/aliases.tsv}: every id that loads as another (the aliases and the rod swap), for
     * {@code tools/fishing-mod/migration/census.py compare}.
     */
    private static void writeAliasTable(MinecraftServer server, SelfTest.Report r) {
        List<String> lines = new ArrayList<>();
        lines.add("# kind\told id\tloads as (written by /cgfish selftest aliases)");
        TideAliases.items().forEach((from, to) -> lines.add("item\t" + from + "\t" + to));
        TideAliases.blocks().forEach((from, to) -> lines.add("block\t" + from + "\t" + to));
        TideAliases.entities().forEach((from, to) -> lines.add("entity\t" + from + "\t" + to));
        lines.add("item\tminecraft:fishing_rod\t" + BuiltInRegistries.ITEM.getKey(RodSwap.swap(Items.FISHING_ROD)));
        lines.add("item\talloyed:steel_fishing_rod\t" + BuiltInRegistries.ITEM.getKey(FishingRegistry.STEEL_ROD.get()));
        try {
            Path out = server.getServerDirectory().resolve(CreateGaloreFishing.ID).resolve("aliases.tsv");
            Files.createDirectories(out.getParent());
            Files.write(out, lines);
            r.info("alias table written to " + out);
        } catch (IOException e) {
            r.fail("couldn't write the alias table: " + e);
        }
    }

    private static <T> void aliases(SelfTest.Report r, Registry<T> registry, Map<ResourceLocation, ResourceLocation> table) {
        for (Map.Entry<ResourceLocation, ResourceLocation> e : table.entrySet()) {
            boolean target = registry.containsKey(e.getValue());
            T resolved = registry.get(e.getKey());
            r.check(target && resolved == registry.get(e.getValue()),
                    registry.key().location().getPath() + " " + e.getKey() + " loads as " + e.getValue());
        }
    }

    private static void species(MinecraftServer server, SelfTest.Report r) {
        List<Item> fish = TideFish.getOrderedFishItems();
        r.check(fish.size() == 90, "90 ported Tide species (" + fish.size() + ")");
        int complete = 0;
        for (Item item : fish) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            StringBuilder missing = new StringBuilder();
            if (FishData.get(item).isEmpty()) missing.append(" fish-data");
            if (SpeciesManager.of(item).isEmpty()) missing.append(" species");
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                if (!BuiltInRegistries.ITEM.containsKey(id.withSuffix("_bucket"))) missing.append(" bucket");
                if (!BuiltInRegistries.ITEM.containsKey(id.withSuffix("_spawn_egg"))) missing.append(" spawn-egg");
                if (lootTable(server, id.withPrefix("entities/")) == LootTable.EMPTY) missing.append(" mob-loot");
            }
            if (missing.isEmpty()) complete++;
            else r.fail(id + " is missing" + missing);
        }
        r.check(complete == fish.size(), complete + " of " + fish.size() + " ported species complete");

        // Season conditions read Serene Seasons' real season (the pack runs it), not a stand-in.
        ServerLevel overworld = server.overworld();
        String serene = Season.sereneSeasonsName(overworld);
        Season seen = Season.of(overworld);
        if (serene == null) {
            r.info("Serene Seasons isn't installed: season conditions always pass (fish see " + seen + ")");
        } else {
            r.check(seen.name().equals(serene) || (serene.equals("AUTUMN") && seen == Season.FALL),
                    "fish see Serene Seasons' season (Serene Seasons " + serene + ", fish data " + seen.getSerializedName() + ")");
        }

        // Tide's tackle is ours now: every hook, bait, bobber and special rod has tackle data.
        for (Item item : List.of(TideItems.FISHING_HOOK, TideItems.LAVAPROOF_HOOK, TideItems.FIERY_HOOK,
                TideItems.PERMAFROST_HOOK, TideItems.TWILIGHT_HOOK, TideItems.VOID_HOOK, TideItems.BAIT,
                TideItems.LUCKY_BAIT, TideItems.MAGNETIC_BAIT, TideItems.INCANDESCENT_BAIT, TideItems.ABYSS_BAIT)) {
            r.check(TackleManager.of(item).isPresent(), "tackle data for " + BuiltInRegistries.ITEM.getKey(item));
        }
        long bobbers = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> id.getNamespace().equals("tide") && id.getPath().endsWith("_bobber"))
                .filter(id -> TackleManager.of(BuiltInRegistries.ITEM.get(id)).map(p -> p.kind() == TacklePart.Kind.FLOAT).orElse(false))
                .count();
        r.check(bobbers == 33, "all 33 Tide bobbers are floats (" + bobbers + ")");
        for (Item rod : List.of(TideItems.ECHO_FISHING_ROD, TideItems.PRISMARINE_FISHING_ROD, TideItems.SUNFLOWER_FISHING_ROD,
                TideItems.VILLAGE_FISHING_ROD, TideItems.BLAZING_FISHING_ROD, TideItems.HONEYCOMB_FISHING_ROD,
                TideItems.MIDAS_FISHING_ROD)) {
            r.check(TackleManager.of(rod).filter(p -> p.kind() == TacklePart.Kind.ROD).isPresent(),
                    "rod data for " + BuiltInRegistries.ITEM.getKey(rod));
        }
    }

    private static void crates(MinecraftServer server, SelfTest.Report r) {
        List<CrateData> crates = CatchTables.crates();
        r.check(crates.size() >= 9, crates.size() + " crate entries (Tide has 9, Hybrid Aquatic adds its own)");
        ServerLevel level = server.overworld();
        for (CrateData crate : crates) {
            if (crate.lootTable().isEmpty()) continue;
            ResourceKey<LootTable> key = crate.lootTable().get();
            LootTable table = server.reloadableRegistries().getLootTable(key);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, level.getSharedSpawnPos().getCenter())
                    .create(LootContextParamSets.CHEST);
            r.check(table != LootTable.EMPTY && !table.getRandomItems(params).isEmpty(), "crate table " + key.location() + " rolls");
        }
        for (FishingLootData loot : CatchTables.loot()) {
            r.check(server.reloadableRegistries().getLootTable(loot.lootTable()) != LootTable.EMPTY,
                    (loot.isTreasure() ? "treasure" : "junk") + " table " + loot.lootTable().location() + " exists");
        }
    }

    private static void spawns(MinecraftServer server, SelfTest.Report r) {
        Set<EntityType<?>> listed = new HashSet<>();
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (Biome biome : biomes) {
            for (MobCategory category : MobCategory.values()) {
                biome.getMobSettings().getMobs(category).unwrap().forEach(s -> listed.add(s.type));
            }
        }
        List<EntityType<?>> tideFish = TideFish.getOrderedFishItems().stream()
                .map(item -> BuiltInRegistries.ITEM.getKey(item))
                .filter(BuiltInRegistries.ENTITY_TYPE::containsKey)
                .<EntityType<?>>map(BuiltInRegistries.ENTITY_TYPE::get).toList();
        // Tide spawns a fish naturally where it has a biome modifier (tide:add_<fish>_spawns); the others (void,
        // space and legendary fish) are only ever caught.
        int modifiers = 0;
        for (ResourceLocation id : server.registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).keySet()) {
            if (!id.getNamespace().equals("tide") || !id.getPath().matches("add_.*_spawns")) continue;
            ResourceLocation fish = Tide.resource(id.getPath().substring(4, id.getPath().length() - 7));
            // A handed-off species (tide:carp is Aquaculture's carp now) spawns through its own mod.
            if (TideAliases.entities().containsKey(fish)) continue;
            modifiers++;
            r.check(BuiltInRegistries.ENTITY_TYPE.getOptional(fish).filter(listed::contains).isPresent(),
                    fish + " is in some biome's spawns");
        }
        r.check(modifiers == 64, modifiers + " Tide spawn files (Tide's 74 less the 10 handed-off species)");
        r.info("only caught, never spawned: " + String.join(", ", tideFish.stream().filter(t -> !listed.contains(t))
                .map(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath()).toList()));

        // Water near spawn (loaded chunks only), and every fish's spawn rule there.
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        List<BlockPos> water = new ArrayList<>();
        for (int dx = -48; dx <= 48 && water.size() < 24; dx += 3) {
            for (int dz = -48; dz <= 48 && water.size() < 24; dz += 3) {
                int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
                if (level.getFluidState(top).is(FluidTags.WATER)) water.add(top);
            }
        }
        r.info(water.size() + " water blocks sampled near spawn");
        int allowed = 0;
        int threw = 0;
        for (EntityType<?> type : tideFish) {
            for (BlockPos pos : water) {
                try {
                    if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, level.getRandom())) allowed++;
                } catch (RuntimeException e) {
                    threw++;
                    r.info(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " spawn rule threw " + e);
                }
            }
        }
        r.check(threw == 0, "spawn rules ran " + tideFish.size() * water.size() + " times without throwing");
        r.info(allowed + " of those would spawn a fish");
    }

    private static void components(MinecraftServer server, SelfTest.Report r) {
        // A Tide iron rod rigged at the Angling Table, as saved by Tide (the rod and line ids go through the aliases).
        ItemStack rod = new ItemStack(BuiltInRegistries.ITEM.get(Tide.resource("iron_fishing_rod")));
        r.check(rod.is(FishingRegistry.IRON_ROD.get()), "tide:iron_fishing_rod loads as our iron rod");
        rod.set(TideDataComponents.FISHING_LINE, encode(server, new ItemStack(BuiltInRegistries.ITEM.get(Tide.resource("golden_line")))));
        rod.set(TideDataComponents.FISHING_HOOK, encode(server, new ItemStack(TideItems.FIERY_HOOK)));
        rod.set(TideDataComponents.FISHING_BOBBER, encode(server, new ItemStack(TideItems.BLUE_BOBBER)));
        rod.set(TideDataComponents.BAIT_CONTENTS, new BaitContents(List.of(new ItemStack(TideItems.LUCKY_BAIT, 20))));
        List<ItemStack> overflow = new ArrayList<>();
        TideRodConversion.convert(rod, overflow::add);
        RodParts parts = RodItem.parts(rod);
        r.check(parts.line().is(FishingRegistry.CLEAR_LINE.get()), "Tide's golden line became clear line (" + parts.line() + ")");
        r.check(parts.hook().is(TideItems.FIERY_HOOK), "hook kept");
        r.check(parts.terminal().is(TideItems.BLUE_BOBBER), "bobber kept as the float");
        r.check(parts.reel().is(FishingRegistry.IRON_REEL.get()), "iron rod got an iron reel");
        r.check(parts.bait().is(TideItems.LUCKY_BAIT) && parts.bait().getCount() == RodItem.BAIT_CAPACITY
                && overflow.size() == 1 && overflow.getFirst().getCount() == 20 - RodItem.BAIT_CAPACITY,
                "bait moved, the rest handed back");
        r.check(!TideRodConversion.needsConversion(rod) && !rod.has(TideDataComponents.BAIT_CONTENTS), "Tide's components removed");
        r.check(RodStats.of(rod).map(RodStats::canCast).orElse(false), "converted rod can cast");

        // A rod Tide never rigged keeps its defaults: plain hook, red bobber, basic line.
        ItemStack plain = new ItemStack(FishingRegistry.WOODEN_ROD.get());
        plain.set(TideDataComponents.FISHING_HOOK, new CompoundTag());
        TideRodConversion.convert(plain, s -> { });
        RodParts defaults = RodItem.parts(plain);
        r.check(defaults.line().is(FishingRegistry.STRING_LINE.get()) && defaults.hook().is(TideItems.FISHING_HOOK)
                && defaults.terminal().is(TideItems.RED_BOBBER) && defaults.reel().is(FishingRegistry.COPPER_REEL.get()),
                "Tide's default accessories fill the gaps");

        // Vanilla and Alloyed rods are ours, and recipes that take a vanilla rod take ours.
        r.check(new ItemStack(Items.FISHING_ROD).is(FishingRegistry.WOODEN_ROD.get()), "a vanilla Fishing Rod is a Wooden Rod");
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("alloyed", "steel_fishing_rod")).ifPresent(steel ->
                r.check(new ItemStack(steel).is(FishingRegistry.STEEL_ROD.get()), "Alloyed's Steel Fishing Rod is a Steel Rod"));
        r.check(Ingredient.of(Items.FISHING_ROD).test(new ItemStack(FishingRegistry.WOODEN_ROD.get())),
                "an ingredient asking for a vanilla rod takes a Wooden Rod");
        CraftingInput carrotOnAStick = CraftingInput.of(2, 2, List.of(new ItemStack(FishingRegistry.WOODEN_ROD.get()),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.CARROT)));
        r.check(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, carrotOnAStick, server.overworld())
                        .map(h -> h.value().getResultItem(server.registryAccess()).is(Items.CARROT_ON_A_STICK)).orElse(false),
                "Wooden Rod + carrot crafts a carrot on a stick");

        // A caught sturgeon goes on a Trophy Mount, at its own length.
        ServerLevel level = server.overworld();
        BlockPos mountAt = level.getSharedSpawnPos().above(40);
        BlockState before = level.getBlockState(mountAt);
        level.setBlock(mountAt, TideBlocks.FISH_DISPLAY.defaultBlockState(), 2);
        if (level.getBlockEntity(mountAt) instanceof FishDisplayBlockEntity mount) {
            ItemStack sturgeon = Catches.fishItem(SpeciesManager.of(TideFish.STURGEON).orElseThrow(),
                    new BiteSampler.Bite(SpeciesManager.of(TideFish.STURGEON).orElseThrow(), 150, 17, SizeClass.COMMON), "Tester", 1);
            r.check(mount.setDisplayStack(sturgeon), "a caught sturgeon goes on a Trophy Mount");
            r.check(Math.abs(mount.getFishLength() - 150) < 0.5, "the mount shows its real length (" + mount.getFishLength() + " cm)");
        } else {
            r.fail("no Trophy Mount block entity");
        }
        level.setBlock(mountAt, before, 2);

        // A fish caught with Tide: only its length.
        ItemStack fish = new ItemStack(BuiltInRegistries.ITEM.get(Tide.resource("largemouth_bass")));
        fish.set(TideDataComponents.FISH_LENGTH, 50.0);
        var info = CatchInfo.read(fish);
        r.check(info.isPresent(), "a Tide fish with only its length reads as a catch");
        info.ifPresent(i -> r.info("50 cm Tide bass reads as " + i));
    }

    private static CompoundTag encode(MinecraftServer server, ItemStack stack) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    private static LootTable lootTable(MinecraftServer server, ResourceLocation id) {
        return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
    }
}
