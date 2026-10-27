package dev.creategalore.bridge.guideai;

import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Works lens's goal test: a model that says what it makes ({@code goal}) has to hold the machines of a recipe that
 * makes it, and a sequenced assembly line something that carries the item from step to step. A model that holds none
 * of them is refused; a goal no machine recipe can be matched against is reported as not shown, never as working.
 */
final class GoalCheck {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    private static final Set<String> CARRIERS = Set.of("create:belt", "create:depot", "create:weighted_ejector");

    private GoalCheck() {
    }

    /** The item the model's goal names first, or null when it names none this server knows. */
    static Item goal(JsonObject input) {
        String text = GuideText.string(input, "goal");
        Matcher m = ID.matcher(text.toLowerCase(java.util.Locale.ROOT));
        while (m.find()) {
            ResourceLocation id = ResourceLocation.tryParse(m.group());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
        }
        return null;
    }

    private record Way(String name, Set<Item> machines, SequencedAssemblyRecipe assembly) {
    }

    static BuildCheck.Report check(Map<BlockPos, BlockState> cells, Item goal, String goalText, net.minecraft.server.level.ServerLevel level) {
        try {
            return run(cells, goal, goalText, level);
        } catch (RuntimeException | LinkageError e) {
            return new BuildCheck.Report("(The goal check couldn't run on this model: " + e.getClass().getSimpleName() + ".)", List.of());
        }
    }

    private static BuildCheck.Report run(Map<BlockPos, BlockState> cells, Item goal, String goalText, net.minecraft.server.level.ServerLevel level) {
        MinecraftServer server = level.getServer();
        String name = goal.getDescription().getString();
        String heading = "Whether it makes " + name + BuildCheck.FOR_YOU + ":\n";
        Set<Item> has = new HashSet<>();
        boolean carrier = false;
        for (BlockState s : cells.values()) {
            has.add(s.getBlock().asItem());
            carrier |= CARRIERS.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        }
        List<Way> ways = new ArrayList<>();
        boolean unknown = false;
        for (RecipeHolder<?> holder : Reach.makers(server).getOrDefault(goal, List.of())) {
            Way w = way(holder);
            if (w == null) unknown = true;
            else ways.add(w);
        }
        if (ways.isEmpty()) {
            return new BuildCheck.Report(heading + "- Not shown: no machine recipe for " + name + " could be matched against "
                    + "this model, so nothing here proves it makes it. Say how it is made from the recipes, not from the model.", List.of());
        }
        List<String> missing = new ArrayList<>();
        for (Way w : ways) {
            Set<Item> lack = new LinkedHashSet<>(w.machines());
            lack.removeAll(has);
            if (lack.isEmpty()) {
                if (w.assembly() != null && !carrier) {
                    String fault = "nothing carries the item from one step of the " + w.name() + " to the next: every step "
                            + "works on an item lying under it on a belt or depot, and the item has to pass under each "
                            + "machine in order, once per loop";
                    return new BuildCheck.Report(heading + "- PROBLEM: " + fault + ".", List.of(fault));
                }
                BuildCheck.Report holds = new BuildCheck.Report(heading + "- It holds what the " + w.name() + " that makes " + name
                        + " needs: " + names(w.machines()) + ".", List.of());
                if (w.assembly() == null) return holds;
                BuildCheck.Report line = ItemLines.check(cells, BuildCheck.rpm(cells, level, new ArrayList<>()), w.assembly(), goalText);
                if (line == null) return holds;
                List<String> faults = new ArrayList<>(line.faults());
                return new BuildCheck.Report(holds.text() + "\n" + line.text(), faults);
            }
            missing.add("the " + w.name() + " needs " + names(lack));
        }
        if (unknown) {
            return new BuildCheck.Report(heading + "- Not shown: it lacks the machines of every Create recipe for " + name + " ("
                    + String.join("; ", missing) + "), and the other ways to make it aren't machines a model can show.", List.of());
        }
        String fault = "nothing in it makes " + name + ": " + String.join("; ", missing);
        return new BuildCheck.Report(heading + "- PROBLEM: " + fault + ".", List.of(fault));
    }

    /** The machines one recipe needs, or null for a recipe made by hand or by a machine this check doesn't know. */
    private static Way way(RecipeHolder<?> holder) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if (type == null) return null;
        if (holder.value() instanceof SequencedAssemblyRecipe assembly) {
            Set<ItemLike> machines = new LinkedHashSet<>();
            for (SequencedRecipe<?> step : assembly.getSequence())
                if (step.getRecipe() instanceof IAssemblyRecipe a) a.addRequiredMachines(machines);
            Set<Item> items = new LinkedHashSet<>();
            machines.forEach(m -> items.add(m.asItem()));
            return new Way("sequenced assembly", items, assembly);
        }
        String[] blocks = switch (type.toString()) {
            case "create:pressing" -> new String[] {"create:mechanical_press"};
            case "create:mixing" -> new String[] {"create:mechanical_mixer", "create:basin"};
            case "create:compacting" -> new String[] {"create:mechanical_press", "create:basin"};
            case "create:crushing" -> new String[] {"create:crushing_wheel"};
            case "create:milling" -> new String[] {"create:millstone"};
            case "create:cutting" -> new String[] {"create:mechanical_saw"};
            case "create:deploying", "create:item_application" -> new String[] {"create:deployer"};
            case "create:filling" -> new String[] {"create:spout"};
            case "create:splashing", "create:haunting" -> new String[] {"create:encased_fan"};
            case "create:mechanical_crafting" -> new String[] {"create:mechanical_crafter"};
            default -> null;
        };
        if (blocks == null) return null;
        Set<Item> items = new LinkedHashSet<>();
        for (String b : blocks) items.add(BuiltInRegistries.ITEM.get(ResourceLocation.parse(b)));
        items.remove(Items.AIR);
        String name = type.getPath().replace('_', ' ') + " recipe";
        return new Way(name, items, null);
    }

    static String ingredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        var items = ingredient.getItems();
        return items.length == 0 ? "?" : items[0].getHoverName().getString() + (items.length > 1 ? " (or another of its kind)" : "");
    }

    private static String names(Set<Item> items) {
        List<String> out = new ArrayList<>();
        for (Item i : items) out.add(i.getDescription().getString());
        return String.join(", ", out);
    }
}
