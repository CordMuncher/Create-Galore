package dev.creategalore.bridge.guideai;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * The Create machines that also run a crafting-table recipe on this server, by the same rules and server settings the
 * machines use: a Mechanical Mixer runs shapeless recipes of two or more items, a Mechanical Press packs a 2x2 or 3x3
 * square of one item, and Mechanical Crafters run any regular crafting recipe. Without this the recipe tools said
 * "Crafting Table" alone, and the book told players such a recipe can't be automated.
 */
final class AutoCrafting {
    private AutoCrafting() {
    }

    /** One way a machine runs the recipe: its name as the player reads it, and the blocks it needs. */
    record Machine(String name, List<String> blocks) {
    }

    static List<Machine> machines(RecipeHolder<?> holder) {
        List<Machine> out = new ArrayList<>();
        if (!(holder.value() instanceof CraftingRecipe recipe) || recipe instanceof CustomRecipe
                || recipe instanceof MechanicalCraftingRecipe) return out;
        try {
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) return out;
            var config = AllConfigs.server().recipes;
            boolean compress = MechanicalPressBlockEntity.canCompress(recipe);
            if (compress && config.allowShapedSquareInPress.get())
                out.add(new Machine("Mechanical Press over a Basin (it packs a square of one item)",
                        List.of("create:mechanical_press", "create:basin")));
            if (!compress && !(recipe instanceof ShapedRecipe) && config.allowShapelessInMixer.get()
                    && recipe.getIngredients().stream().filter(i -> !i.isEmpty()).count() > 1)
                out.add(new Machine("Mechanical Mixer over a Basin (it runs shapeless crafting)",
                        List.of("create:mechanical_mixer", "create:basin")));
            if (config.allowRegularCraftingInCrafter.get())
                out.add(new Machine("Mechanical Crafters", List.of("create:mechanical_crafter")));
        } catch (RuntimeException | LinkageError e) {
            // Create's server config isn't loaded yet; say nothing rather than guess.
            out.clear();
        }
        return out;
    }

    /** The machines as a phrase to add after a crafting recipe, or "" when none runs it. */
    static String phrase(RecipeHolder<?> holder) {
        List<Machine> machines = machines(holder);
        if (machines.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (Machine m : machines) names.add(m.name());
        return "; automated by " + String.join(", or ", names);
    }
}
