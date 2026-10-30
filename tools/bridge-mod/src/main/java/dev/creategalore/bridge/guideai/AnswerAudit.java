package dev.creategalore.bridge.guideai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Checks a finished answer's own words against the server before the player keeps it: every {item id} is an item
 * here, every [recipe id] a recipe, every [diagram N] one this answer made, and every model or drawing made for it
 * placed in it. What it finds goes back to the book once, to rewrite the answer.
 */
final class AnswerAudit {
    private static final Pattern ITEM = Pattern.compile("\\{([a-z0-9_.-]+:[a-z0-9_/.-]+)}");
    private static final Pattern RECIPE = Pattern.compile("\\[recipe ([^\\]\\s]+)]");
    private static final Pattern DIAGRAM = Pattern.compile("\\[diagram (\\d+)]");
    private static final Pattern INSIDE = Pattern.compile("\"text\"\\s*:\\s*\"(\\d+)\"");

    private AnswerAudit() {
    }

    /** What is wrong with the answer's markers; empty when nothing is. */
    static List<String> check(String answer, List<String> made, MinecraftServer server) {
        int diagrams = made.size();
        List<String> out = new ArrayList<>();
        Set<String> badItems = new LinkedHashSet<>();
        Matcher m = ITEM.matcher(answer);
        while (m.find()) {
            ResourceLocation id = ResourceLocation.tryParse(m.group(1));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) badItems.add(m.group(1));
        }
        if (!badItems.isEmpty()) out.add("{" + String.join("}, {", badItems) + "} " + (badItems.size() == 1 ? "isn't an item" : "aren't items")
                + " on this server, so the player sees a blank icon: use an id your tools gave, or the plain name");
        m = RECIPE.matcher(answer);
        Set<String> badRecipes = new LinkedHashSet<>();
        while (m.find()) {
            ResourceLocation id = ResourceLocation.tryParse(m.group(1));
            if (id == null || server.getRecipeManager().byKey(id).isEmpty()) badRecipes.add(m.group(1));
        }
        if (!badRecipes.isEmpty()) out.add("[recipe " + String.join("], [recipe ", badRecipes) + "] "
                + (badRecipes.size() == 1 ? "isn't a recipe" : "aren't recipes") + " here, so nothing is drawn: use the recipe id "
                + "lookup_recipes gives (in brackets after the recipe type), not the item's id");
        m = DIAGRAM.matcher(answer);
        Set<Integer> placed = new LinkedHashSet<>();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > diagrams) out.add("[diagram " + n + "] doesn't exist: this answer made " + (diagrams == 0 ? "no diagrams"
                    : "diagrams 1 to " + diagrams) + " (drafts are never diagrams)");
            else placed.add(n);
        }
        // A model can also go into the answer drawn inside a drawing (a shape of kind model whose text is its number).
        for (String d : made) {
            if (!d.contains("\"model\"")) continue;
            Matcher t = INSIDE.matcher(d);
            while (t.find()) placed.add(Integer.parseInt(t.group(1)));
        }
        for (int n = 1; n <= diagrams; n++)
            if (!placed.contains(n))
                out.add("diagram " + n + " was made for this answer but isn't in it: place it with [diagram " + n + "], or "
                        + "leave out the text that relies on it");
        return out;
    }
}
