package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a build can be made of: the building blocks a player holds, grouped by material, and the trees and ground
 * around them. A material is everything a crafting table or stonecutter makes from one block out of that block
 * alone (Oak Log, then Oak Planks, then its stairs, slabs and fences; Andesite, then Polished Andesite and its
 * stairs), read from this server's recipes, so the book can tell what a player has of a look and what it would take
 * to build a model out of it.
 */
final class Materials {
    static final int AROUND = 48;
    private static final int MAX_FAMILIES = 30, MAX_OTHER = 30, PLENTY = 16;
    private static final Set<String> WOOD_WORDS = Set.of("stripped", "wood", "log", "stem", "hyphae", "block");

    private Materials() {
    }

    /** One step down a material: one of this item is made from {@code factor} of {@code parent}. */
    private record Edge(Item parent, double factor) {
    }

    /** Where an item sits in its material: the material's first block and how many of it one item takes. */
    private record Place(Item root, double factor) {
    }

    /** The recipe steps of every material on this server, worked out once per recipe reload. */
    private record Graph(RecipeManager recipes, Map<Item, Place> places, Map<Item, Double> unit, Map<Item, Item> unitItem,
            Set<Item> parents) {
    }

    private static volatile Graph graph;

    private static Graph graph(MinecraftServer server) {
        RecipeManager recipes = server.getRecipeManager();
        Graph g = graph;
        if (g != null && g.recipes == recipes) return g;
        g = build(server, recipes);
        graph = g;
        return g;
    }

    private static Graph build(MinecraftServer server, RecipeManager recipes) {
        Map<Item, Edge> best = new IdentityHashMap<>();
        Map<Item, Boolean> bestNatural = new IdentityHashMap<>();
        // Options of the same ingredient that are the same wood (Stripped Oak Log for Oak Log), worth one each.
        Map<Item, Item> alias = new IdentityHashMap<>();
        // Blocks made from any planks or any wool, which belong to no one material whatever else also makes them.
        Set<Item> anyOf = new HashSet<>();
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getType() != RecipeType.CRAFTING && recipe.getType() != RecipeType.STONECUTTING) continue;
            ItemStack out;
            try {
                out = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException e) {
                continue;
            }
            if (out.isEmpty() || !(out.getItem() instanceof BlockItem)) continue;
            Ingredient only = null;
            double count = 0, stickCount = 0;
            boolean single = true;
            for (Ingredient i : recipe.getIngredients()) {
                if (i.isEmpty() || i.getItems().length == 0) continue;
                if (sticks(i)) {
                    stickCount++;
                    continue;
                }
                if (only == null) only = i;
                else if (!same(only, i)) single = false;
                count++;
            }
            if (!single || only == null) continue;
            Item child = out.getItem();
            Item parent = representative(only, child);
            if (parent == null || parent == child) continue;
            // Any planks or any wool (a chest, a crafting table) is no one wood's: it would count against the one
            // named first, not the one the player has.
            // (Two vanilla options of different woods or colours; a tag that adds a mod's own white wool is still white wool.)
            List<String> parentStem = stem(parent);
            boolean mixed = false;
            for (ItemStack s : only.getItems())
                if (s.getItem() != parent && id(s.getItem()).startsWith("minecraft:") && id(parent).startsWith("minecraft:")
                        && !sameWood(s.getItem(), parentStem)) mixed = true;
            if (mixed) {
                anyOf.add(child);
                continue;
            }
            // Sticks are half a plank each, so fences, gates and signs stay with their wood; they cost wool or stone nothing.
            if (wood(parent)) count += 0.5 * stickCount;
            // Terrain is a material of its own (Cobblestone isn't Stone, whatever a stonecutter makes of it); only
            // wood blocks belong to their log.
            if (Reach.natural(child) && !(log(child) && log(parent))) continue;
            double factor = count / out.getCount();
            boolean natural = Reach.natural(parent);
            Edge was = best.get(child);
            boolean better = was == null || (natural && !bestNatural.get(child))
                    || (natural == bestNatural.get(child) && factor < was.factor - 1e-9);
            if (better) {
                best.put(child, new Edge(parent, factor));
                bestNatural.put(child, natural);
            }
            List<String> stem = stem(parent);
            for (ItemStack s : only.getItems()) {
                Item other = s.getItem();
                if (other != parent && other != child && other instanceof BlockItem && sameWood(other, stem)) alias.putIfAbsent(other, parent);
            }
        }
        best.keySet().removeAll(anyOf);
        // Ingots and their block make each other; neither is the start of the other, so neither is followed.
        for (Item child : new ArrayList<>(best.keySet())) {
            Edge e = best.get(child);
            Edge back = e == null ? null : best.get(e.parent);
            if (back != null && back.parent == child && !Reach.natural(e.parent) && !Reach.natural(child)) {
                best.remove(child);
                best.remove(e.parent);
            }
        }
        alias.keySet().removeIf(best::containsKey);
        Map<Item, Place> places = new IdentityHashMap<>();
        Set<Item> parents = new HashSet<>();
        for (Edge e : best.values()) parents.add(e.parent);
        Set<Item> all = new HashSet<>(best.keySet());
        all.addAll(parents);
        for (Item item : all) places.put(item, walk(item, best));
        alias.forEach((item, rep) -> {
            Place p = places.getOrDefault(rep, new Place(rep, 1));
            places.put(item, p);
            parents.add(item);
        });
        // A material's unit is its plainest full block: vanilla's where it has one (White Wool, not a mod's envelope
        // cut from it), and the one a block of it makes most of (Oak Planks, not the log that makes four).
        Map<Item, Double> unit = new IdentityHashMap<>();
        Map<Item, Item> unitItem = new IdentityHashMap<>();
        places.forEach((item, p) -> {
            if (!(item instanceof BlockItem b) || !fullBlock(b.getBlock())) return;
            Double was = unit.get(p.root);
            Item other = unitItem.get(p.root);
            boolean vanilla = id(item).startsWith("minecraft:"), wasVanilla = other != null && id(other).startsWith("minecraft:");
            // From an item (String, an ingot) only a vanilla block is a unit a builder knows (White Wool); a mod's floor
            // tile made from Andesite Alloy isn't, so shafts and the like are counted as themselves.
            if (!(p.root instanceof BlockItem) && !vanilla) return;
            boolean better = was == null || vanilla && !wasVanilla || vanilla == wasVanilla && (p.factor < was - 1e-9
                    || Math.abs(p.factor - was) <= 1e-9 && plainer(item, other));
            if (better) {
                unit.put(p.root, p.factor);
                unitItem.put(p.root, item);
            }
        });
        return new Graph(recipes, places, unit, unitItem, parents);
    }

    /** Vanilla before a mod's, then the shorter name (Oak Planks before a mod's oak panel). */
    private static boolean plainer(Item a, Item b) {
        String x = id(a), y = id(b);
        boolean va = x.startsWith("minecraft:"), vb = y.startsWith("minecraft:");
        if (va != vb) return va;
        return x.length() < y.length();
    }

    private static Place walk(Item item, Map<Item, Edge> best) {
        double factor = 1;
        Item at = item;
        Set<Item> seen = new HashSet<>();
        while (seen.add(at)) {
            Edge e = best.get(at);
            if (e == null) break;
            factor *= e.factor;
            at = e.parent;
        }
        return new Place(at, factor);
    }

    private static boolean sticks(Ingredient ingredient) {
        for (ItemStack s : ingredient.getItems()) if (s.is(Items.STICK)) return true;
        return false;
    }

    private static boolean same(Ingredient a, Ingredient b) {
        ItemStack[] x = a.getItems(), y = b.getItems();
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (x[i].getItem() != y[i].getItem()) return false;
        return true;
    }

    /** Of an ingredient's options, the one a material starts from: terrain first, then the one named most like the result. */
    private static Item representative(Ingredient ingredient, Item child) {
        List<String> words = words(child);
        Item best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack s : ingredient.getItems()) {
            Item item = s.getItem();
            List<String> w = words(item);
            int shared = 0;
            while (shared < Math.min(w.size(), words.size()) && w.get(shared).equals(words.get(shared))) shared++;
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            int score = (Reach.natural(item) ? 100 : 0) + shared * 10 - (w.contains("stripped") ? 5 : 0)
                    + (ns.equals("minecraft") ? 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private static List<String> words(Item item) {
        return List.of(BuiltInRegistries.ITEM.getKey(item).getPath().split("_"));
    }

    /** Oak Log's stem is [oak]; Dark Oak Log's is [dark, oak]. */
    private static List<String> stem(Item item) {
        List<String> w = new ArrayList<>(words(item));
        w.removeIf(WOOD_WORDS::contains);
        return w;
    }

    private static boolean sameWood(Item other, List<String> stem) {
        if (stem.isEmpty()) return false;
        List<String> w = stem(other);
        return w.equals(stem);
    }

    /** A log or planks, which sticks are made from. */
    private static boolean wood(Item item) {
        return item instanceof BlockItem b && (b.getBlock().defaultBlockState().is(BlockTags.LOGS) || b.getBlock().defaultBlockState().is(BlockTags.PLANKS));
    }

    private static boolean log(Item item) {
        return item instanceof BlockItem b && b.getBlock().defaultBlockState().is(BlockTags.LOGS);
    }

    private static boolean fullBlock(Block block) {
        try {
            return block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A block a build is made of, rather than a machine, chest, ore or loose plant. */
    private static boolean building(Item item) {
        if (!(item instanceof BlockItem b)) return false;
        Block block = b.getBlock();
        if (block instanceof EntityBlock) return false;
        BlockState s = block.defaultBlockState();
        return !s.is(ORES) && !s.is(BlockTags.SAPLINGS) && !s.is(BlockTags.FLOWERS) && !s.is(BlockTags.CROPS);
    }

    private static final List<TagKey<Item>> METALS = List.of(itemTag("c:ingots"), itemTag("c:nuggets"), itemTag("c:gems"));

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    /** Ingots and gems make blocks too, but they are kept for crafting rather than built with. */
    private static boolean metal(Item item) {
        ItemStack s = new ItemStack(item);
        for (TagKey<Item> t : METALS) if (s.is(t)) return true;
        return false;
    }

    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:ores"));

    private static String kind(Item root) {
        if (!(root instanceof BlockItem b)) return "other";
        BlockState s = b.getBlock().defaultBlockState();
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) return "wood";
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(STONES) || s.is(COBBLESTONES)
                || s.is(SANDSTONES) || s.is(END_STONES)) return "stone";
        return "other";
    }

    private static final TagKey<Block> STONES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:stones")),
            COBBLESTONES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:cobblestones")),
            SANDSTONES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:sandstone/blocks")),
            END_STONES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:end_stones"));

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String name(Item item) {
        return item.getDescription().getString();
    }

    /** One material the player holds: what of it, and how many full blocks it all makes. */
    private static final class Family {
        final Item root;
        final Map<Item, Integer> held = new LinkedHashMap<>();
        double worth; // in the root's units
        double pool; // in the root's units, only what can still be made into other blocks of it (logs, planks, stone)

        Family(Item root) {
            this.root = root;
        }

        long blocks(Graph g) {
            return Math.round(worth / g.unit.getOrDefault(root, 1.0));
        }
    }

    /** Where an item sits in a material, or null when it belongs to none (a torch, made from coal and a stick). */
    private static Place material(Item item, Graph g) {
        Place p = g.places.get(item);
        return p != null && g.unit.containsKey(p.root) ? p : null;
    }

    private static Map<Item, Family> families(Map<Item, Integer> owned, Graph g) {
        Map<Item, Family> out = new LinkedHashMap<>();
        owned.forEach((item, n) -> {
            Place p = material(item, g);
            // Bricks and other items a material's blocks are made from count with it; loose ingots don't.
            if (p == null || !building(item) && !(g.parents.contains(item) && !metal(item))) return;
            Family f = out.computeIfAbsent(p.root, Family::new);
            f.held.merge(item, n, Integer::sum);
            f.worth += n * p.factor;
            if (g.parents.contains(item)) f.pool += n * p.factor;
        });
        return out;
    }

    // ---------- building_materials ----------

    static String describe(ServerPlayer player, MinecraftServer server) {
        Graph g = graph(server);
        Map<Item, Integer> owned = Reach.owned(player);
        Map<Item, Family> families = families(owned, g);
        List<Family> sorted = new ArrayList<>(families.values());
        sorted.sort((a, b) -> Long.compare(b.blocks(g), a.blocks(g)));
        StringBuilder out = new StringBuilder("Building blocks the player holds (inventory and the storage they opened "
                + "within " + TouchedStorage.RANGE + " blocks), by material. A material is one block and everything a "
                + "crafting table or stonecutter makes out of it alone (planks, stairs, slabs, fences, polished and brick "
                + "forms); \"makes about N full blocks\" counts what all of it is worth in its plainest full block.\n");
        for (String kind : List.of("wood", "stone", "other")) {
            List<String> lines = new ArrayList<>();
            for (Family f : sorted) {
                if (!kind(f.root).equals(kind) || lines.size() >= MAX_FAMILIES) continue;
                lines.add("- " + family(f, g));
            }
            if (lines.isEmpty()) continue;
            out.append(switch (kind) {
                case "wood" -> "Wood:\n";
                case "stone" -> "Stone:\n";
                default -> "Other materials:\n";
            });
            lines.forEach(l -> out.append(l).append('\n'));
        }
        // Blocks that belong to no material (glass, wool, terracotta, concrete, copper): just counted.
        Map<Item, Integer> loose = new TreeMap<>((a, b) -> id(a).compareTo(id(b)));
        owned.forEach((item, n) -> {
            if (material(item, g) == null && building(item)) loose.merge(item, n, Integer::sum);
        });
        if (!loose.isEmpty()) {
            out.append("Other building blocks: ");
            List<Map.Entry<Item, Integer>> list = new ArrayList<>(loose.entrySet());
            list.sort((a, b) -> b.getValue() - a.getValue());
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Math.min(MAX_OTHER, list.size()); i++)
                parts.add(list.get(i).getValue() + " " + id(list.get(i).getKey()));
            out.append(String.join(", ", parts)).append(list.size() > MAX_OTHER ? ", and " + (list.size() - MAX_OTHER) + " more kinds" : "").append('\n');
        }
        if (families.isEmpty() && loose.isEmpty()) out.append("They hold no building blocks.\n");
        out.append(around(player));
        return out.toString().strip();
    }

    private static String family(Family f, Graph g) {
        List<Map.Entry<Item, Integer>> held = new ArrayList<>(f.held.entrySet());
        held.sort((a, b) -> b.getValue() - a.getValue());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(6, held.size()); i++) parts.add(held.get(i).getValue() + " " + id(held.get(i).getKey()));
        if (held.size() > 6) parts.add("and " + (held.size() - 6) + " more kinds");
        return title(f.root, g) + ": makes about " + blocks(f.blocks(g)) + "; holds " + String.join(", ", parts);
    }

    /** A material by its plainest full block, which is how a builder thinks of it: Oak Planks, Stone, Bricks. */
    private static String title(Item root, Graph g) {
        Item unit = g.unitItem.getOrDefault(root, root);
        return name(unit) + " (" + id(unit) + ")";
    }

    private static String blocks(long n) {
        return n + (n == 1 ? " full block" : " full blocks");
    }

    /**
     * The trees and ground open to the sky within {@link #AROUND} blocks, which the player can see for themselves:
     * logs counted by kind (not ones players placed), and the top block of the ground.
     */
    static String around(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PlacedBlocks placed = GuideAi.placed();
        BlockPos here = player.blockPosition();
        Map<Block, Integer> logs = new HashMap<>(), ground = new HashMap<>();
        int columns = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -AROUND; dx <= AROUND; dx += 2) {
            for (int dz = -AROUND; dz <= AROUND; dz += 2) {
                if (dx * dx + dz * dz > AROUND * AROUND) continue;
                int x = here.getX() + dx, z = here.getZ() + dz;
                if (!level.isLoaded(p.set(x, here.getY(), z))) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                columns++;
                for (int y = top; y > top - 40 && y > level.getMinBuildHeight(); y--) {
                    BlockState s = level.getBlockState(p.set(x, y, z));
                    if (s.isAir() || s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty()) continue;
                    boolean byPlayer = placed != null && placed.isPlaced(level, p);
                    if (s.is(BlockTags.LOGS)) {
                        if (!byPlayer) logs.merge(s.getBlock(), 1, Integer::sum);
                        continue;
                    }
                    if (!byPlayer) ground.merge(s.getBlock(), 1, Integer::sum);
                    break;
                }
            }
        }
        if (columns == 0) return "";
        StringBuilder out = new StringBuilder("Around them (within " + AROUND + " blocks, what lies open to the sky, every "
                + "second block sampled): ");
        out.append("trees: ").append(logs.isEmpty() ? "none" : top(logs, 8)).append("; ground: ").append(top(ground, 8));
        return out.append('.').toString();
    }

    private static String top(Map<Block, Integer> counts, int max) {
        List<Map.Entry<Block, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(max, list.size()); i++)
            parts.add(BuiltInRegistries.BLOCK.getKey(list.get(i).getKey()) + " " + list.get(i).getValue());
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    // ---------- a model against what the player holds ----------

    /**
     * The model's blocks against what the player holds, by material: how many full blocks of each material it takes
     * and how many they have, with the blocks the model needs that they lack. Null when the player is unknown.
     */
    static String against(Map<BlockPos, BlockState> cells, ServerPlayer player, MinecraftServer server) {
        if (player == null) return null;
        Graph g = graph(server);
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (BlockState s : cells.values()) {
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                continue;
            if (s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) continue;
            Item item = s.getBlock().asItem();
            if (item == Items.AIR) continue;
            int n = s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
            need.merge(item, n, Integer::sum);
        }
        if (need.isEmpty()) return null;
        Map<Item, Integer> owned = Reach.owned(player);
        Map<Item, Family> held = families(owned, g);
        // Each material the model uses: what it takes beyond the very blocks they hold, against what they can still make.
        Map<Item, Map<Item, Integer>> byRoot = new LinkedHashMap<>();
        Map<Item, Integer> loose = new LinkedHashMap<>();
        need.forEach((item, n) -> {
            Place p = material(item, g);
            if (p == null) loose.put(item, n);
            else byRoot.computeIfAbsent(p.root, k -> new LinkedHashMap<>()).put(item, n);
        });
        StringBuilder out = new StringBuilder("The model's blocks against what the player holds (inventory and storage "
                + "they opened nearby; what a crafting table or stonecutter makes from a material they hold counts as theirs):\n");
        List<String> shortWood = new ArrayList<>(), shortStone = new ArrayList<>();
        double unitsShortWood = 0, unitsShortStone = 0;
        for (Map.Entry<Item, Map<Item, Integer>> e : byRoot.entrySet()) {
            Item root = e.getKey();
            double unit = g.unit.getOrDefault(root, 1.0);
            double takes = 0, missing = 0, pool = 0;
            Family f = held.get(root);
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Item, Integer> n : e.getValue().entrySet()) {
                double factor = g.places.get(n.getKey()).factor;
                int have = owned.getOrDefault(n.getKey(), 0);
                takes += n.getValue() * factor;
                missing += Math.max(0, n.getValue() - have) * factor;
                parts.add(n.getValue() + " " + id(n.getKey()));
            }
            if (f != null) {
                pool = f.pool;
                // The planks or stone the model uses as they are can't also be made into its stairs.
                for (Map.Entry<Item, Integer> n : e.getValue().entrySet())
                    if (g.parents.contains(n.getKey())) pool -= Math.min(n.getValue(), owned.getOrDefault(n.getKey(), 0)) * g.places.get(n.getKey()).factor;
            }
            long takesBlocks = Math.max(1, Math.round(takes / unit));
            long holdsBlocks = f == null ? 0 : f.blocks(g);
            long shortBlocks = Math.round(Math.max(0, missing - Math.max(0, pool)) / unit);
            out.append("- ").append(title(root, g)).append(": ").append(String.join(", ", parts))
                    .append(", about ").append(blocks(takesBlocks)).append("; they hold about ").append(holdsBlocks)
                    .append(shortBlocks > 0 ? ", short by about " + shortBlocks : ", enough").append('\n');
            if (shortBlocks > 0) {
                String kind = kind(root);
                if (kind.equals("wood")) {
                    shortWood.add(name(g.unitItem.getOrDefault(root, root)));
                    unitsShortWood = Math.max(unitsShortWood, takesBlocks);
                } else if (kind.equals("stone")) {
                    shortStone.add(name(g.unitItem.getOrDefault(root, root)));
                    unitsShortStone = Math.max(unitsShortStone, takesBlocks);
                }
            }
        }
        List<String> lacking = new ArrayList<>();
        loose.forEach((item, n) -> {
            int have = owned.getOrDefault(item, 0);
            lacking.add(n + " " + id(item) + (have >= n ? " (they hold " + have + ", enough)" : " (they hold " + have + ")"));
        });
        if (!lacking.isEmpty()) out.append("- Other blocks: ").append(String.join(", ", lacking)).append('\n');
        // Where they are short of one wood or stone, the ones of the same kind they hold enough of.
        if (!shortWood.isEmpty()) out.append(alternatives("wood", shortWood, held, byRoot.keySet(), unitsShortWood, g));
        if (!shortStone.isEmpty()) out.append(alternatives("stone", shortStone, held, byRoot.keySet(), unitsShortStone, g));
        return out.toString().strip();
    }

    private static String alternatives(String kind, List<String> shortOf, Map<Item, Family> held, Set<Item> used, double takes, Graph g) {
        List<Family> list = new ArrayList<>();
        for (Family f : held.values())
            if (kind(f.root).equals(kind) && !used.contains(f.root) && f.blocks(g) >= Math.max(PLENTY, takes * 0.5)) list.add(f);
        if (list.isEmpty()) return "";
        list.sort((a, b) -> Long.compare(b.blocks(g), a.blocks(g)));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(4, list.size()); i++)
            parts.add(title(list.get(i).root, g) + ", about " + blocks(list.get(i).blocks(g)));
        return "- Of " + kind + " like " + String.join(" and ", shortOf) + " they hold: " + String.join("; ", parts) + ".\n";
    }
}
