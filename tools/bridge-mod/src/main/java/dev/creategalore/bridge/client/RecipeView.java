package dev.creategalore.bridge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A recipe drawn on the book page from the recipes the server sent the game: a crafting grid with its result, or a
 * machine recipe as inputs, the machine that does it, and what comes out (with chances and heat). Ingredients that
 * accept several items cycle through them. The book asks for one with a [recipe id] line.
 */
final class RecipeView {
    private static final int SLOT = 18, SLOT_FILL = 0xFFE6D6B0, SLOT_EDGE = 0xFF9C8058, INK = 0xFF2B2014, FADED = 0xFF7A6A50,
            ARROW = 0xFF5A4630, HEAT = 0xFFB4501E;
    /** The block each machine recipe type is done in. */
    private static final Map<String, String> STATIONS = Map.ofEntries(
            Map.entry("minecraft:crafting", "minecraft:crafting_table"), Map.entry("minecraft:smelting", "minecraft:furnace"),
            Map.entry("minecraft:blasting", "minecraft:blast_furnace"), Map.entry("minecraft:smoking", "minecraft:smoker"),
            Map.entry("minecraft:campfire_cooking", "minecraft:campfire"), Map.entry("minecraft:stonecutting", "minecraft:stonecutter"),
            Map.entry("minecraft:smithing", "minecraft:smithing_table"), Map.entry("create:mixing", "create:mechanical_mixer"),
            Map.entry("create:pressing", "create:mechanical_press"), Map.entry("create:compacting", "create:mechanical_press"),
            Map.entry("create:crushing", "create:crushing_wheel"), Map.entry("create:milling", "create:millstone"),
            Map.entry("create:cutting", "create:mechanical_saw"), Map.entry("create:deploying", "create:deployer"),
            Map.entry("create:filling", "create:spout"), Map.entry("create:emptying", "create:item_drain"),
            Map.entry("create:splashing", "create:encased_fan"), Map.entry("create:haunting", "create:encased_fan"),
            Map.entry("create:sandpaper_polishing", "create:sand_paper"), Map.entry("create:mechanical_crafting", "create:mechanical_crafter"),
            Map.entry("create:sequenced_assembly", "create:precision_mechanism"), Map.entry("create:item_application", "create:andesite_casing"));
    private static final Map<String, String> NAMES = Map.of("create:splashing", "Washing (fan through water)",
            "create:haunting", "Haunting (fan through soul fire)", "create:compacting", "Compacting (press over a basin)",
            "create:mixing", "Mixing (mixer over a basin)", "create:item_application", "Use the item on the block",
            "create:sequenced_assembly", "Sequenced assembly");

    private record Slot(Ingredient ingredient, ItemStack stack, String note) {
        ItemStack shown() {
            if (stack != null) return stack;
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) return ItemStack.EMPTY;
            return options[(int) (Util.getMillis() / 1000 % options.length)];
        }
    }

    private final boolean grid;
    private final int gridW;
    private final List<Slot> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    private final ItemStack station;
    private final String label;
    private final String missing;
    private String extra = "";

    private RecipeView(boolean grid, int gridW, ItemStack station, String label, String missing) {
        this.grid = grid;
        this.gridW = gridW;
        this.station = station;
        this.label = label;
        this.missing = missing;
    }

    /** The recipe with this id, or else the first recipe that makes the item with this id. */
    static RecipeView of(String id) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (mc.level == null || rl == null) return new RecipeView(false, 0, ItemStack.EMPTY, "", "(recipe " + id + ")");
        var manager = mc.level.getRecipeManager();
        RegistryAccess access = mc.level.registryAccess();
        RecipeHolder<?> holder = manager.byKey(rl).orElse(null);
        if (holder == null) {
            var item = BuiltInRegistries.ITEM.getOptional(rl);
            if (item.isPresent()) {
                for (RecipeHolder<?> h : manager.getRecipes()) {
                    if (h.value().getResultItem(access).is(item.get())) {
                        holder = h;
                        if (h.value().getType() == RecipeType.CRAFTING) break;
                    }
                }
            }
        }
        if (holder == null) return new RecipeView(false, 0, ItemStack.EMPTY, "", "(no recipe " + id + " on this server)");
        try {
            return build(holder.value(), access);
        } catch (RuntimeException | LinkageError e) {
            return new RecipeView(false, 0, ItemStack.EMPTY, "", "(recipe " + id + ")");
        }
    }

    private static RecipeView build(Recipe<?> recipe, RegistryAccess access) {
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String type = typeId == null ? "" : typeId.toString();
        String stationId = STATIONS.get(type);
        ItemStack station = stationId == null ? recipe.getToastSymbol() : stack(stationId);
        String label = NAMES.getOrDefault(type, station.isEmpty() ? "" : station.getHoverName().getString());
        boolean grid = recipe instanceof ShapedRecipe || recipe.getType() == RecipeType.CRAFTING;
        int gridW = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        RecipeView v = new RecipeView(grid, Math.max(1, gridW), station, label, null);
        List<Ingredient> ingredients = recipe.getIngredients();
        if (grid) {
            for (Ingredient i : ingredients) v.inputs.add(new Slot(i, null, null));
        } else {
            for (Ingredient i : ingredients) if (!i.isEmpty()) v.inputs.add(new Slot(i, null, null));
        }
        if (Create.isProcessing(recipe)) {
            Create.addFluids(recipe, v.inputs);
            Create.addOutputs(recipe, v.outputs);
            String heat = Create.heat(recipe);
            if (!heat.isEmpty()) v.extra = heat;
        } else {
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty()) v.outputs.add(new Slot(null, result, null));
        }
        return v;
    }

    private static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private int cols() {
        return grid ? gridW : Math.min(4, Math.max(1, inputs.size()));
    }

    private int rows() {
        return Math.max(1, (inputs.size() + cols() - 1) / cols());
    }

    /** The arrow's height from the top: room for the machine icon above it and the inputs centred on it. */
    private int mid() {
        return 2 + Math.max(rows() * SLOT / 2, 19);
    }

    int height(Font font) {
        if (missing != null) return font.lineHeight + 4;
        int labels = (label.isEmpty() ? 0 : 1) + (extra.isEmpty() ? 0 : 1);
        int below = Math.max(rows() * SLOT / 2, Math.max(5 + labels * font.lineHeight, SLOT / 2
                + (outputs.stream().anyMatch(o -> o.note != null) ? font.lineHeight + 1 : 0)));
        return mid() + below + 4;
    }

    /** Paints at left/top within width; returns the item under the mouse. */
    ItemStack paint(GuiGraphics g, Font font, int left, int top, int width, int mouseX, int mouseY) {
        if (missing != null) {
            g.drawString(font, missing, left, top + 2, FADED, false);
            return ItemStack.EMPTY;
        }
        ItemStack hovered = ItemStack.EMPTY;
        int cols = cols(), rows = rows();
        int midY = top + mid(), x = left + 2, y = midY - rows * SLOT / 2;
        for (int i = 0; i < inputs.size(); i++) {
            int sx = x + (i % cols) * SLOT, sy = y + (i / cols) * SLOT;
            ItemStack s = slot(g, font, sx, sy, inputs.get(i));
            if (!s.isEmpty() && inside(mouseX, mouseY, sx, sy)) hovered = s;
        }
        // The machine (or table) above an arrow, its name under it.
        int ax = x + cols * SLOT + 6, room = left + width - ax - outputs.size() * (SLOT + 2) - 6;
        int aw = Math.max(34, Math.min(Math.max(34, room), Math.max(font.width(label), font.width(extra)) + 6));
        if (!station.isEmpty()) {
            g.renderItem(station, ax + (aw - 16) / 2, midY - 19);
            if (inside(mouseX, mouseY, ax + (aw - 16) / 2 - 1, midY - 20)) hovered = station;
        }
        g.fill(ax + 2, midY, ax + aw - 4, midY + 2, ARROW);
        for (int k = 0; k < 4; k++) g.fill(ax + aw - 6 - k, midY - k + 0, ax + aw - 5 - k + 1, midY + 2 + k, ARROW);
        if (!label.isEmpty()) {
            String l = font.plainSubstrByWidth(label, aw);
            g.drawString(font, l, ax + (aw - font.width(l)) / 2, midY + 5, FADED, false);
        }
        if (!extra.isEmpty()) g.drawString(font, extra, ax + (aw - font.width(extra)) / 2, midY + 5 + font.lineHeight, HEAT, false);
        int ox = ax + aw + 4;
        for (int i = 0; i < outputs.size() && ox + SLOT <= left + width + 4; i++) {
            Slot o = outputs.get(i);
            int sy = midY - SLOT / 2;
            ItemStack s = slot(g, font, ox, sy, o);
            if (!s.isEmpty() && inside(mouseX, mouseY, ox, sy)) hovered = s;
            if (o.note != null) g.drawString(font, o.note, ox + (SLOT - font.width(o.note)) / 2, sy + SLOT + 1, FADED, false);
            ox += SLOT + 2;
        }
        return hovered;
    }

    private static boolean inside(int mx, int my, int x, int y) {
        return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
    }

    private static ItemStack slot(GuiGraphics g, Font font, int x, int y, Slot slot) {
        g.fill(x, y, x + SLOT, y + SLOT, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_FILL);
        if (slot.ingredient != null && slot.ingredient.isEmpty() && slot.stack == null) return ItemStack.EMPTY;
        ItemStack s = slot.shown();
        if (s.isEmpty()) return s;
        g.renderItem(s, x + 1, y + 1);
        g.renderItemDecorations(font, s, x + 1, y + 1);
        return s;
    }

    /** Create's machine recipes: fluids in, chanced outputs, heat. Only touched when Create is there. */
    private static final class Create {
        static boolean isProcessing(Recipe<?> recipe) {
            try {
                return recipe instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>;
            } catch (LinkageError e) {
                return false;
            }
        }

        static void addFluids(Recipe<?> recipe, List<Slot> inputs) {
            var p = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) recipe;
            try {
                for (var f : p.getFluidIngredients()) {
                    var fluids = f.getFluids();
                    if (fluids.length == 0) continue;
                    ItemStack bucket = new ItemStack(fluids[0].getFluid().getBucket());
                    if (bucket.isEmpty() || bucket.is(Items.AIR)) bucket = new ItemStack(Items.BUCKET);
                    inputs.add(new Slot(null, bucket, null));
                }
            } catch (RuntimeException ignored) {
                // Fluids are a bonus; the items are what matter most.
            }
        }

        static void addOutputs(Recipe<?> recipe, List<Slot> outputs) {
            var p = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) recipe;
            for (var o : p.getRollableResults()) {
                String note = o.getChance() < 1f ? String.format(Locale.ROOT, "%.0f%%", o.getChance() * 100) : null;
                outputs.add(new Slot(null, o.getStack(), note));
            }
            try {
                for (var f : p.getFluidResults()) {
                    ItemStack bucket = new ItemStack(f.getFluid().getBucket());
                    if (!bucket.isEmpty()) outputs.add(new Slot(null, bucket, null));
                }
            } catch (RuntimeException ignored) {
                // As above.
            }
        }

        static String heat(Recipe<?> recipe) {
            try {
                var heat = ((com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) recipe).getRequiredHeat();
                return heat == null || heat.name().equals("NONE") ? "" : heat.name().equals("HEATED") ? "heated" : "superheated";
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }
    }
}
