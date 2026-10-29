package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * How far a thing is from the player, from the server's own data rather than memory: the whole recipe tree down to
 * what has to be gathered, with what the player has, has ever picked up or crafted, and the stations each step
 * needs; and what a block really drops with each tool, rolled from its loot table (block drops only; chest and
 * structure loot stay hidden).
 */
final class Reach {
    private static final int MAX_DEPTH = 6, MAX_LINES = 45, DROP_ROLLS = 200;

    private Reach() {
    }

    // ---------- recipe_tree ----------

    private record Need(Item item, int count) {
    }

    static String tree(ServerPlayer player, MinecraftServer server, String itemQuery, int amount) {
        Item item = WorldTools.resolveItem(itemQuery);
        if (item == null) return "No item called '" + itemQuery + "'. Try find_items first.";
        Map<Item, List<RecipeHolder<?>>> makers = makers(server);
        Map<Item, Integer> have = owned(player);
        List<String> lines = new ArrayList<>();
        Map<Item, String> gather = new LinkedHashMap<>();
        Set<String> stations = new LinkedHashSet<>();
        walk(player, server, new Need(item, Math.max(1, amount)), 0, new LinkedHashSet<>(), makers, have, lines, gather, stations);
        StringBuilder out = new StringBuilder("Recipe tree for ").append(Math.max(1, amount)).append("x ")
                .append(item.getDescription().getString()).append(" (").append(BuiltInRegistries.ITEM.getKey(item))
                .append("), following one recipe per item (the one they can already make, else the plainest); \"have\" "
                + "counts their inventory and the storage they opened nearby; an item they have enough of isn't followed further:\n");
        lines.forEach(l -> out.append(l).append('\n'));
        if (!gather.isEmpty()) {
            out.append("To gather (found in the world, or no recipe: mined, found, dropped, fished or traded):\n");
            gather.forEach((i, note) -> out.append("- ").append(note).append('\n'));
        }
        if (!stations.isEmpty()) out.append("Stations and machines along the way: ").append(String.join("; ", stations)).append(".\n");
        out.append("Dimensions they have been to: ").append(String.join(", ", visited(player, server))).append('.');
        return out.toString();
    }

    /** Every recipe on the server, by the item it makes. */
    /** The last recipe index built, and the recipe manager it was built from (a reload makes a new one). */
    private static Object makersFor;
    private static Map<Item, List<RecipeHolder<?>>> makersCache;

    static Map<Item, List<RecipeHolder<?>>> makers(MinecraftServer server) {
        var recipes = server.getRecipeManager();
        if (makersFor == recipes && makersCache != null) return makersCache;
        makersCache = index(server);
        makersFor = recipes;
        return makersCache;
    }

    private static Map<Item, List<RecipeHolder<?>>> index(MinecraftServer server) {
        var registries = server.registryAccess();
        Map<Item, List<RecipeHolder<?>>> makers = new HashMap<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                for (ItemStack out : WorldTools.outputs(holder.value(), registries))
                    if (!out.isEmpty()) makers.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(holder);
            } catch (RuntimeException ignored) {
                // A recipe that can't say what it makes can't be part of a path.
            }
        }
        return makers;
    }

    // ---------- the parts a model needs ----------

    private static final String ASSEMBLY = "create:sequenced_assembly";

    /**
     * The parts of a model the player can't make yet: a part they have never had whose recipe tree, followed as
     * recipe_tree follows it, needs an item that only a sequenced assembly line makes and that they have never had
     * either (a Mechanical Arm before their first Precision Mechanism). A part that needs the model's own {@code goal}
     * is left to {@link #selfMade}. Null when every part is within reach.
     */
    static BuildCheck.Report parts(ServerPlayer player, MinecraftServer server, java.util.Collection<BlockState> states, Item goal) {
        try {
            Map<Item, List<RecipeHolder<?>>> makers = makers(server);
            Map<Item, Integer> have = owned(player);
            Map<Item, Item> blocked = new LinkedHashMap<>();
            Set<Item> seen = new java.util.HashSet<>();
            Map<Item, Item> known = new HashMap<>();
            for (BlockState s : states) {
                Item part = item(s, player);
                if (part == Items.AIR || !seen.add(part) || had(player, part, have)) continue;
                Item gate = gate(player, null, part, 0, new LinkedHashSet<>(), makers, have, known);
                // A part that needs the model's own product is selfMade's to report.
                if (gate != null && gate != goal) blocked.put(part, gate);
            }
            if (blocked.isEmpty()) return null;
            List<String> faults = new ArrayList<>();
            blocked.forEach((part, gate) -> faults.add(part.getDescription().getString() + (part == gate ? " is" : " needs "
                    + gate.getDescription().getString() + ", which is") + " made only on a sequenced assembly line, and the "
                    + "player has never had " + (part == gate ? "one" : "a " + gate.getDescription().getString())));
            String text = "What the player can make of it" + BuildCheck.FOR_YOU + ":\n- PROBLEM: they can't make every part yet: "
                    + String.join("; ", faults) + ". A line that makes that item can't use a part that needs it: build it "
                    + "without that part (by hand, or with belts alone), and show the way to their first one. Set for_later "
                    + "only when they asked for a build for later on.";
            return new BuildCheck.Report(text, faults);
        } catch (RuntimeException | LinkageError e) {
            return new BuildCheck.Report("(The parts check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    /**
     * Parts of a model that every way of making needs the model's own product (a Mechanical Arm on a line that makes the
     * first Precision Mechanism): such a line can't be built to make its first one. A part the player already has or
     * has had is fine. Null when none.
     */
    static BuildCheck.Report selfMade(ServerPlayer player, MinecraftServer server, java.util.Collection<BlockState> states, Item goal) {
        try {
            Map<Item, List<RecipeHolder<?>>> makers = makers(server);
            Map<Item, Integer> have = player == null ? Map.of() : owned(player);
            if (player != null && had(player, goal, have)) return null;
            Set<Item> seen = new java.util.HashSet<>();
            List<String> faults = new ArrayList<>();
            for (BlockState s : states) {
                Item part = item(s, player);
                if (part == Items.AIR || part == goal || !seen.add(part) || player != null && had(player, part, have)) continue;
                if (gate(player, goal, part, 0, new LinkedHashSet<>(), makers, have, new HashMap<>()) != null)
                    faults.add(part.getDescription().getString() + " needs " + article(goal) + " to make");
            }
            if (faults.isEmpty()) return null;
            String text = "Whether its parts can be made before it runs" + BuildCheck.FOR_YOU + ":\n- PROBLEM: "
                    + String.join("; ", faults) + ", the very thing this model makes, so it can't be built to make the first "
                    + "one. Do that step another way (by hand, or with belts and depots alone).";
            return new BuildCheck.Report(text, faults);
        } catch (RuntimeException | LinkageError e) {
            return new BuildCheck.Report("(The parts check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    /**
     * Whether the player can make one now: they have or had one, or one of its recipes takes only things they have had,
     * gather from the world, or can make that way in turn, three steps down at most. A sequenced assembly's product counts
     * only once they have had one, and a heated basin recipe only once they have had a Blaze Burner. Rougher than
     * recipe_tree, and meant for choosing among parts: what this says no to is a later stage, not a missing ingot.
     */
    static boolean canMake(ServerPlayer player, MinecraftServer server, Item item) {
        try {
            return canMake(player, makers(server), owned(player), item, 0, new HashSet<>(), new HashSet<>(), new HashMap<>());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static final Item BLAZE_BURNER = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:blaze_burner"));

    /** {@code yes} holds items found makeable, {@code no} the shallowest depth each was found not to be. */
    private static boolean canMake(ServerPlayer player, Map<Item, List<RecipeHolder<?>>> makers, Map<Item, Integer> have, Item item,
            int depth, Set<Item> path, Set<Item> yes, Map<Item, Integer> no) {
        if (yes.contains(item) || had(player, item, have) || natural(item)) return true;
        if (depth >= 3 || no.getOrDefault(item, Integer.MAX_VALUE) <= depth || !path.add(item)) return false;
        boolean found = false;
        try {
            for (RecipeHolder<?> holder : makers.getOrDefault(item, List.of())) {
                ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                if (type != null && type.toString().equals(ASSEMBLY)) continue;
                if (holder.value() instanceof ProcessingRecipe<?, ?> processing
                        && processing.getRequiredHeat() != com.simibubi.create.content.processing.recipe.HeatCondition.NONE
                        && !had(player, BLAZE_BURNER, have)) continue;
                Map<Ingredient, Integer> parts = grouped(holder.value());
                if (parts.isEmpty()) continue;
                boolean all = true;
                for (Ingredient ingredient : parts.keySet()) {
                    boolean any = false;
                    for (ItemStack choice : ingredient.getItems())
                        if (canMake(player, makers, have, choice.getItem(), depth + 1, path, yes, no)) {
                            any = true;
                            break;
                        }
                    if (!any) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    found = true;
                    break;
                }
            }
            return found;
        } finally {
            path.remove(item);
            if (found) yes.add(item);
            else no.merge(item, depth, Math::min);
        }
    }

    private static String article(Item item) {
        String name = item.getDescription().getString();
        return ("AEIOUaeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ") + name;
    }

    /** The item a block is placed from: its own, or the one picking it gives (a belt comes from a Belt Connector). */
    private static Item item(BlockState s, ServerPlayer player) {
        Item own = s.getBlock().asItem();
        if (own != Items.AIR || player == null) return own;
        try {
            ItemStack pick = s.getBlock().getCloneItemStack(s, null, player.serverLevel(), player.blockPosition(), player);
            return pick == null ? Items.AIR : pick.getItem();
        } catch (RuntimeException | LinkageError e) {
            return Items.AIR;
        }
    }

    /** Whether the player has one now, or ever picked one up, crafted, mined or placed one. */
    private static boolean had(ServerPlayer player, Item item, Map<Item, Integer> have) {
        return have.getOrDefault(item, 0) > 0 || history(player, item).startsWith(", ever")
                || player.getStats().getValue(Stats.ITEM_USED.get(item)) > 0;
    }

    /**
     * The item made only on a sequenced assembly line, never had by the player, that every way of making {@code item}
     * needs (each recipe, and each choice an ingredient allows); null when some way gets round it. With a
     * {@code target}, the gate is that item instead (and with no player, nothing counts as had). Answers are kept in
     * {@code known}, with {@link Items#AIR} for none.
     */
    private static Item gate(ServerPlayer player, Item target, Item item, int depth, Set<Item> path, Map<Item, List<RecipeHolder<?>>> makers,
            Map<Item, Integer> have, Map<Item, Item> known) {
        if (depth > MAX_DEPTH || natural(item)) return null;
        if (target != null && item == target) return item;
        if (depth > 0 && player != null && had(player, item, have)) return null;
        Item memo = known.get(item);
        if (memo != null) return memo == Items.AIR ? null : memo;
        List<RecipeHolder<?>> options = makers.getOrDefault(item, List.of());
        if (options.isEmpty()) return null;
        boolean onlyAssembly = target == null;
        for (RecipeHolder<?> holder : options) {
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (type == null || !type.toString().equals(ASSEMBLY)) onlyAssembly = false;
        }
        if (onlyAssembly) {
            known.put(item, item);
            return item;
        }
        Item found = null;
        path.add(item);
        try {
            for (RecipeHolder<?> holder : options) {
                Map<Ingredient, Integer> parts = grouped(holder.value());
                if (parts.isEmpty()) return null;
                Item needs = null;
                boolean circular = false;
                for (Ingredient ingredient : parts.keySet()) {
                    Item any = null;
                    boolean free = false;
                    for (ItemStack choice : ingredient.getItems()) {
                        if (path.contains(choice.getItem())) continue;
                        Item g = gate(player, target, choice.getItem(), depth + 1, path, makers, have, known);
                        if (g == null) {
                            free = true;
                            break;
                        }
                        any = g;
                    }
                    if (free) continue;
                    if (any == null) circular = true;
                    else needs = any;
                    break;
                }
                // A recipe that only goes round in a circle says nothing either way.
                if (circular) continue;
                if (needs == null) {
                    known.put(item, Items.AIR);
                    return null;
                }
                found = needs;
            }
        } finally {
            path.remove(item);
        }
        if (depth == 0 || found == null) known.put(item, found == null ? Items.AIR : found);
        return found;
    }

    private static void walk(ServerPlayer player, MinecraftServer server, Need need, int depth, Set<Item> path,
            Map<Item, List<RecipeHolder<?>>> makers, Map<Item, Integer> have, List<String> lines, Map<Item, String> gather,
            Set<String> stations) {
        if (lines.size() >= MAX_LINES) {
            if (lines.size() == MAX_LINES) lines.add("  ".repeat(depth) + "- ...(tree cut short; ask recipe_tree for a part of it)");
            return;
        }
        Item item = need.item;
        String indent = "  ".repeat(depth);
        int owned = have.getOrDefault(item, 0);
        StringBuilder line = new StringBuilder(indent).append("- ").append(need.count).append("x ").append(item.getDescription().getString())
                .append(" (").append(BuiltInRegistries.ITEM.getKey(item)).append("): have ").append(owned);
        String history = history(player, item);
        if (owned >= need.count) {
            lines.add(line.append(", enough").toString());
            return;
        }
        List<RecipeHolder<?>> options = makers.getOrDefault(item, List.of());
        if (natural(item)) {
            // Terrain (stone, End Stone, logs, sand, ores): found in the world, whatever odd recipes also make it.
            lines.add(line.append(history).append("; found in the world").append(options.isEmpty() ? ""
                    : " (" + options.size() + " recipe" + (options.size() > 1 ? "s" : "") + " also make it)").toString());
            gather.putIfAbsent(item, item.getDescription().getString() + " (" + BuiltInRegistries.ITEM.getKey(item)
                    + ", " + WorldTools.modName(BuiltInRegistries.ITEM.getKey(item).getNamespace()) + "): have " + owned + history);
            return;
        }
        RecipeHolder<?> chosen = depth >= MAX_DEPTH ? null : choose(player, item, path, makers, have, server);
        if (chosen == null) {
            boolean looped = !options.isEmpty();
            line.append(history).append(looped ? (depth >= MAX_DEPTH ? "; deeper than this tree goes" : "; only made from things further up this tree")
                    : "; no recipe makes it");
            lines.add(line.toString());
            if (!looped) gather.putIfAbsent(item, item.getDescription().getString() + " (" + BuiltInRegistries.ITEM.getKey(item)
                    + ", " + WorldTools.modName(BuiltInRegistries.ITEM.getKey(item).getNamespace()) + "): have " + owned + history);
            return;
        }
        Recipe<?> recipe = chosen.value();
        String station = station(recipe) + AutoCrafting.phrase(chosen);
        stations.add(station + stationHistory(player, recipe));
        int makes = 1;
        for (ItemStack out : WorldTools.outputs(recipe, server.registryAccess())) if (out.is(item)) makes = Math.max(1, out.getCount());
        int crafts = (int) Math.ceil((need.count - owned) / (double) makes);
        line.append(history).append("; made by ").append(station);
        if (options.size() > 1) line.append(" (").append(options.size() - 1).append(" other recipe").append(options.size() > 2 ? "s" : "").append(")");
        lines.add(line.toString());
        path.add(item);
        for (Map.Entry<Ingredient, Integer> e : grouped(recipe).entrySet()) {
            Item pick = pick(e.getKey(), have, player);
            if (pick == null) continue;
            String alternatives = e.getKey().getItems().length > 1 ? " or " + (e.getKey().getItems().length - 1) + " others" : "";
            int before = lines.size();
            walk(player, server, new Need(pick, e.getValue() * crafts), depth + 1, path, makers, have, lines, gather, stations);
            if (!alternatives.isEmpty() && lines.size() > before) lines.set(before, lines.get(before) + " [any of it" + alternatives + "]");
        }
        path.remove(item);
    }

    private static final List<TagKey<Block>> TERRAIN = List.of(BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER,
            BlockTags.LOGS, BlockTags.DIRT, BlockTags.SAND, block("c:end_stones"), block("c:ores"), block("c:gravels"),
            block("c:sands"), block("c:netherracks"), block("c:obsidians"), block("c:stones"), block("c:cobblestones"));

    private static TagKey<Block> block(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    /** Raw ore a pickaxe gives (Raw Copper), which is mined, not made from its storage block. */
    private static final TagKey<Item> RAW = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:raw_materials"));

    /** A block the world generates as terrain, or raw ore mined from it, so it is gathered rather than made. */
    static boolean natural(Item item) {
        if (item.builtInRegistryHolder().is(RAW)) return true;
        if (!(item instanceof BlockItem blockItem)) return false;
        BlockState state = blockItem.getBlock().defaultBlockState();
        for (TagKey<Block> tag : TERRAIN) if (state.is(tag)) return true;
        return false;
    }

    /** The recipe to follow: never one that needs something further up the tree; one they can make now beats the rest. */
    private static RecipeHolder<?> choose(ServerPlayer player, Item item, Set<Item> path, Map<Item, List<RecipeHolder<?>>> makers, Map<Item, Integer> have,
            MinecraftServer server) {
        RecipeHolder<?> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (RecipeHolder<?> holder : makers.getOrDefault(item, List.of())) {
            Map<Ingredient, Integer> parts = grouped(holder.value());
            if (parts.isEmpty()) continue;
            boolean circular = false, ready = true;
            for (Map.Entry<Ingredient, Integer> e : parts.entrySet()) {
                Item pick = pick(e.getKey(), have, player);
                if (pick == null || path.contains(pick) || pick == item || onlyFrom(pick, item, makers, path)) circular = true;
                else if (have.getOrDefault(pick, 0) < e.getValue()) ready = false;
            }
            if (circular) continue;
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            String t = type == null ? "" : type.toString();
            // Plain ingredients first too: Raw Copper before Create's Crushed Raw Copper, Oak Log before a mod's driftwood.
            int plain = 0;
            for (Ingredient i : parts.keySet()) {
                Item pick = pick(i, have, player);
                String ns = pick == null ? "" : BuiltInRegistries.ITEM.getKey(pick).getNamespace();
                plain += have.getOrDefault(pick, 0) > 0 || ns.equals("minecraft") ? 0 : ns.equals("create") ? 2 : 4;
            }
            int score = (ready ? 0 : 100) + (t.startsWith("minecraft:") ? 0 : t.startsWith("create:") ? 10 : 20) + parts.size() + plain;
            if (score < bestScore) {
                bestScore = score;
                best = holder;
            }
        }
        return best;
    }

    /** True when every recipe for {@code ingredient} uses {@code target} or something further up (ingot <-> block, nugget). */
    private static boolean onlyFrom(Item ingredient, Item target, Map<Item, List<RecipeHolder<?>>> makers, Set<Item> path) {
        List<RecipeHolder<?>> list = makers.getOrDefault(ingredient, List.of());
        if (list.isEmpty()) return false;
        for (RecipeHolder<?> holder : list) {
            boolean uses = false;
            for (Ingredient i : holder.value().getIngredients()) {
                if (i.isEmpty()) continue;
                for (ItemStack s : i.getItems()) if (s.is(target) || path.contains(s.getItem())) uses = true;
            }
            if (!uses) return false;
        }
        return true;
    }

    private static Map<Ingredient, Integer> grouped(Recipe<?> recipe) {
        Map<String, Ingredient> byKey = new LinkedHashMap<>();
        Map<Ingredient, Integer> counts = new LinkedHashMap<>();
        for (Ingredient i : recipe.getIngredients()) {
            if (i.isEmpty() || i.getItems().length == 0) continue;
            StringBuilder key = new StringBuilder();
            for (ItemStack s : i.getItems()) key.append(BuiltInRegistries.ITEM.getKey(s.getItem())).append(',');
            Ingredient same = byKey.computeIfAbsent(key.toString(), k -> i);
            counts.merge(same, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Of an ingredient's options, the one the player has most of; when they have none, the one they have had before,
     * then the plainest (vanilla, then Create) rather than whichever a tag happens to list first (a Mars ore).
     */
    private static Item pick(Ingredient ingredient, Map<Item, Integer> have, ServerPlayer player) {
        Item best = null;
        long bestScore = Long.MIN_VALUE;
        for (ItemStack s : ingredient.getItems()) {
            Item item = s.getItem();
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            long score = (long) have.getOrDefault(item, 0) * 1000 + (history(player, item).startsWith(", ever") ? 100 : 0)
                    + (ns.equals("minecraft") ? 10 : ns.equals("create") ? 5 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best;
    }

    private static String history(ServerPlayer player, Item item) {
        int picked = player.getStats().getValue(Stats.ITEM_PICKED_UP.get(item)), crafted = player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
        int mined = item instanceof BlockItem b ? player.getStats().getValue(Stats.BLOCK_MINED.get(b.getBlock())) : 0;
        if (picked + crafted + mined == 0) return ", never had one";
        List<String> parts = new ArrayList<>();
        if (picked > 0) parts.add("picked up " + picked);
        if (mined > 0) parts.add("mined " + mined);
        if (crafted > 0) parts.add("crafted " + crafted);
        return ", ever " + String.join(", ", parts);
    }

    private static final Map<String, String> STATIONS = Map.ofEntries(
            Map.entry("minecraft:crafting", "Crafting Table|minecraft:crafting_table"),
            Map.entry("minecraft:smelting", "Furnace|minecraft:furnace"),
            Map.entry("minecraft:blasting", "Blast Furnace|minecraft:blast_furnace"),
            Map.entry("minecraft:smoking", "Smoker|minecraft:smoker"),
            Map.entry("minecraft:campfire_cooking", "Campfire|minecraft:campfire"),
            Map.entry("minecraft:stonecutting", "Stonecutter|minecraft:stonecutter"),
            Map.entry("minecraft:smithing", "Smithing Table|minecraft:smithing_table"),
            Map.entry("create:mixing", "Mechanical Mixer over a Basin|create:mechanical_mixer"),
            Map.entry("create:compacting", "Mechanical Press over a Basin|create:mechanical_press"),
            Map.entry("create:pressing", "Mechanical Press|create:mechanical_press"),
            Map.entry("create:crushing", "Crushing Wheels|create:crushing_wheel"),
            Map.entry("create:milling", "Millstone|create:millstone"),
            Map.entry("create:cutting", "Mechanical Saw|create:mechanical_saw"),
            Map.entry("create:mechanical_crafting", "Mechanical Crafters|create:mechanical_crafter"),
            Map.entry("create:sequenced_assembly", "a sequenced assembly line (Deployers, Spouts, Presses, Saws on a belt)|create:deployer"),
            Map.entry("create:deploying", "Deployer|create:deployer"),
            Map.entry("create:filling", "Spout|create:spout"),
            Map.entry("create:emptying", "Item Drain|create:item_drain"),
            Map.entry("create:splashing", "Encased Fan through water|create:encased_fan"),
            Map.entry("create:haunting", "Encased Fan through soul fire|create:encased_fan"),
            Map.entry("create:item_application", "using one item on a block by hand|"));

    private static String station(Recipe<?> recipe) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String t = type == null ? "?" : type.toString();
        String known = STATIONS.get(t);
        String name = known != null ? known.split("\\|", -1)[0] : t + " (" + WorldTools.modName(type == null ? "?" : type.getNamespace()) + ")";
        if (recipe instanceof ProcessingRecipe<?, ?> p) {
            try {
                var heat = p.getRequiredHeat();
                if (heat != null && !heat.name().equals("NONE"))
                    name += " with " + heat.name().toLowerCase(Locale.ROOT) + " heat (a Blaze Burner under the Basin)";
            } catch (RuntimeException | LinkageError ignored) {
                // Only basin recipes carry heat.
            }
        }
        return name;
    }

    /** Whether they have, ever placed or crafted the station itself: the plainest sign of how far along they are. */
    private static String stationHistory(ServerPlayer player, Recipe<?> recipe) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String known = type == null ? null : STATIONS.get(type.toString());
        if (known == null || known.endsWith("|")) return "";
        Item station = BuiltInRegistries.ITEM.get(ResourceLocation.parse(known.split("\\|")[1]));
        if (station == Items.AIR) return "";
        int placed = player.getStats().getValue(Stats.ITEM_USED.get(station)), crafted = player.getStats().getValue(Stats.ITEM_CRAFTED.get(station));
        // Create's advancement for using a machine (create:mechanical_press) shows they have run one, whatever the statistics say.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(station);
        boolean used = player.getServer() != null && done(player, player.getServer(), id.toString());
        return " (" + station.getDescription().getString() + ": " + (placed + crafted > 0 ? "placed " + placed + ", crafted " + crafted
                : used ? "they have its advancement, so they have run one" : "never made or placed one") + ")";
    }

    private static List<String> visited(ServerPlayer player, MinecraftServer server) {
        List<String> out = new ArrayList<>(List.of("the Overworld"));
        if (done(player, server, "minecraft:story/enter_the_nether")) out.add("the Nether");
        if (done(player, server, "minecraft:story/enter_the_end")) out.add("the End");
        String here = player.serverLevel().dimension().location().toString();
        if (!here.startsWith("minecraft:")) out.add(here + " (where they are now)");
        return out;
    }

    private static boolean done(ServerPlayer player, MinecraftServer server, String id) {
        var holder = server.getAdvancements().get(ResourceLocation.parse(id));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Items in their inventory and in the storage they opened nearby, by item. */
    static Map<Item, Integer> owned(ServerPlayer player) {
        Map<Item, Integer> have = new HashMap<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) have.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        TouchedStorage storage = GuideAi.storage();
        if (storage != null) storage.counts(player).forEach((item, n) -> have.merge(item, n, Integer::sum));
        return have;
    }

    // ---------- block_drops ----------

    static String drops(ServerPlayer player, String query) {
        Block block = resolveBlock(query);
        if (block == null) return "No block called '" + query + "'. Try find_items first.";
        ServerLevel level = player.serverLevel();
        BlockState state = block.defaultBlockState();
        Item tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? Items.NETHERITE_PICKAXE : state.is(BlockTags.MINEABLE_WITH_AXE)
                ? Items.NETHERITE_AXE : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? Items.NETHERITE_SHOVEL
                : state.is(BlockTags.MINEABLE_WITH_HOE) ? Items.NETHERITE_HOE : Items.AIR;
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        StringBuilder out = new StringBuilder("What ").append(block.getName().getString()).append(" (")
                .append(BuiltInRegistries.BLOCK.getKey(block)).append(") drops, rolled ").append(DROP_ROLLS)
                .append(" times from this server's loot table with each tool (average per block, and the range):\n");
        String toolName = tool == Items.AIR ? "a bare hand" : "a " + tool.getDescription().getString();
        out.append("- ").append(toolName).append(": ").append(roll(level, player, state, new ItemStack(tool))).append('\n');
        ItemStack silk = new ItemStack(tool == Items.AIR ? Items.NETHERITE_PICKAXE : tool);
        silk.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        out.append("- with Silk Touch: ").append(roll(level, player, state, silk)).append('\n');
        for (int lvl = 1; lvl <= 3; lvl++) {
            ItemStack fortune = new ItemStack(tool == Items.AIR ? Items.NETHERITE_PICKAXE : tool);
            fortune.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), lvl);
            out.append("- with Fortune ").append("I".repeat(lvl)).append(": ").append(roll(level, player, state, fortune)).append('\n');
        }
        return out.toString().strip();
    }

    private static String roll(ServerLevel level, ServerPlayer player, BlockState state, ItemStack tool) {
        Map<String, int[]> totals = new LinkedHashMap<>(); // sum, min, max
        for (int i = 0; i < DROP_ROLLS; i++) {
            LootParams.Builder params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .withOptionalParameter(LootContextParams.BLOCK_STATE, state);
            Map<String, Integer> once = new HashMap<>();
            for (ItemStack s : state.getDrops(params)) once.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
            for (String name : totals.keySet()) once.putIfAbsent(name, 0);
            boolean first = i == 0; // a drop first seen later was zero on the rolls before
            for (Map.Entry<String, Integer> e : once.entrySet()) {
                int[] t = totals.computeIfAbsent(e.getKey(), k -> new int[] {0, first ? Integer.MAX_VALUE : 0, 0});
                t[0] += e.getValue();
                t[1] = Math.min(t[1], e.getValue());
                t[2] = Math.max(t[2], e.getValue());
            }
        }
        if (totals.isEmpty()) return "nothing";
        List<String> parts = new ArrayList<>();
        totals.forEach((name, t) -> parts.add(String.format(Locale.ROOT, "%.2f %s (%d to %d)", t[0] / (double) DROP_ROLLS, name, t[1], t[2])));
        return String.join(", ", parts);
    }

    private static Block resolveBlock(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(q);
        if (id != null && q.contains(":") && BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
        Item item = WorldTools.resolveItem(query);
        return item instanceof BlockItem b ? b.getBlock() : null;
    }
}
