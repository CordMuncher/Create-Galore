package dev.creategalore.bridge.guideai;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** A sequenced assembly recipe as steps a line has to take an item through, in order, as many times as it loops. */
final class Assembly {
    /** One step: what kind of work (deploying, pressing, filling, cutting), what it does, and the machine that does it. */
    record Step(String kind, String what, Item machine) {
    }

    private Assembly() {
    }

    static List<Step> steps(SequencedAssemblyRecipe recipe) {
        List<Step> out = new ArrayList<>();
        for (SequencedRecipe<?> s : recipe.getSequence()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(s.getRecipe().getType());
            String what = type == null ? "?" : type.getPath();
            Item machine = Items.AIR;
            if (s.getRecipe() instanceof IAssemblyRecipe a) {
                what = what(what, s.getRecipe());
                Set<ItemLike> machines = new LinkedHashSet<>();
                a.addRequiredMachines(machines);
                if (!machines.isEmpty()) machine = machines.iterator().next().asItem();
            }
            out.add(new Step(type == null ? "?" : type.getPath(), what, machine));
        }
        return out;
    }

    /** "deploying a Cogwheel", "pressing", "filling with 250 mB Lava": the step and what it adds. */
    private static String what(String kind, com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?> recipe) {
        List<String> adds = new ArrayList<>();
        var ingredients = recipe.getIngredients();
        // The first ingredient is the item being assembled; the rest are what the step adds.
        for (int i = 1; i < ingredients.size(); i++)
            if (!ingredients.get(i).isEmpty()) adds.add(GoalCheck.ingredient(ingredients.get(i)));
        try {
            for (var f : recipe.getFluidIngredients()) {
                var fluids = f.getFluids();
                adds.add(f.amount() + " mB " + (fluids.length == 0 ? "fluid" : fluids[0].getHoverName().getString()));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fluids are only on filling steps.
        }
        return adds.isEmpty() ? kind : kind + " " + String.join(" + ", adds);
    }

    static int loops(SequencedAssemblyRecipe recipe) {
        return Math.max(1, recipe.getLoops());
    }

    /** The steps in words, for a recipe lookup. */
    static String describe(SequencedAssemblyRecipe recipe) {
        List<String> steps = new ArrayList<>();
        int i = 1;
        for (Step s : steps(recipe))
            steps.add(i++ + ". " + s.what() + (s.machine() == Items.AIR ? "" : " (" + s.machine().getDescription().getString() + ")"));
        StringBuilder out = new StringBuilder("start with ").append(GoalCheck.ingredient(recipe.getIngredient()))
                .append(", then in order: ").append(String.join(", ", steps));
        int loops = loops(recipe);
        if (loops > 1) out.append("; the whole sequence ").append(loops).append(" times (the item goes round ").append(loops)
                .append(" times, so ").append(loops * steps.size()).append(" steps in all)");
        List<String> results = new ArrayList<>();
        for (ProcessingOutput o : recipe.resultPool)
            results.add(o.getStack().getCount() + "x " + o.getStack().getHoverName().getString()
                    + String.format(Locale.ROOT, " (%.0f%%)", 100f * o.getChance() / total(recipe)));
        return out.append(" -> ").append(String.join(", ", results)).toString();
    }

    private static float total(SequencedAssemblyRecipe recipe) {
        float sum = 0;
        for (ProcessingOutput o : recipe.resultPool) sum += o.getChance();
        return sum <= 0 ? 1 : sum;
    }
}
