package dev.creategalore.bridge.client;

import java.util.ArrayList;
import java.util.List;

import com.molybdenum.alloyed.common.content.recipes.AbstractForgingRecipe;
import com.molybdenum.alloyed.common.content.recipes.ModRecipes;
import com.molybdenum.alloyed.common.content.recipes.ShapedForgingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/** The Alloy Forge page: a 3x3 grid (shaped or shapeless), the fire, the time it takes and what comes out. */
final class JeiAlloyForgeCategory implements IRecipeCategory<RecipeHolder<AbstractForgingRecipe>> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final RecipeType<RecipeHolder<AbstractForgingRecipe>> TYPE =
            new RecipeType<>(ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "alloy_forging"), (Class) RecipeHolder.class);
    static final ResourceLocation FORGE = ResourceLocation.fromNamespaceAndPath("alloyed", "forge");
    static final int WIDTH = 118, HEIGHT = 54;

    private final IDrawable icon, slot, arrowEmpty, flameEmpty;
    private final IDrawableAnimated flame;
    private final IGuiHelper gui;

    JeiAlloyForgeCategory(IGuiHelper gui) {
        this.gui = gui;
        this.icon = gui.createDrawableItemStack(forge());
        this.slot = gui.getSlotDrawable();
        this.arrowEmpty = gui.getRecipeArrow();
        this.flameEmpty = gui.getRecipeFlameEmpty();
        this.flame = gui.createAnimatedRecipeFlame(300);
    }

    static ItemStack forge() {
        return new ItemStack(BuiltInRegistries.ITEM.get(FORGE));
    }

    static void registerCategory(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new JeiAlloyForgeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @SuppressWarnings("unchecked")
    static void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        List<RecipeHolder<AbstractForgingRecipe>> all = new ArrayList<>();
        for (var h : level.getRecipeManager().getAllRecipesFor(ModRecipes.SHAPED_FORGING_TYPE.get()))
            all.add((RecipeHolder<AbstractForgingRecipe>) (RecipeHolder<?>) h);
        for (var h : level.getRecipeManager().getAllRecipesFor(ModRecipes.SHAPELESS_FORGING_TYPE.get()))
            all.add((RecipeHolder<AbstractForgingRecipe>) (RecipeHolder<?>) h);
        registration.addRecipes(TYPE, all);
    }

    static void registerCatalyst(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(forge(), TYPE);
    }

    @Override
    public RecipeType<RecipeHolder<AbstractForgingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.alloyed.forge");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AbstractForgingRecipe> holder, IFocusGroup focuses) {
        AbstractForgingRecipe recipe = holder.value();
        List<Ingredient> ins = recipe.getIngredients();
        // A shaped recipe fills its own width and height of the grid, row by row; a shapeless one fills it in order.
        int w = recipe instanceof ShapedForgingRecipe s ? s.getWidth() : 3;
        for (int i = 0; i < ins.size() && i < 9; i++) {
            int x = i % w, y = i / w;
            var slotBuilder = builder.addSlot(RecipeIngredientRole.INPUT, 1 + x * 18, 1 + y * 18);
            if (!ins.get(i).isEmpty()) slotBuilder.addIngredients(ins.get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 97, 19).addItemStack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void draw(RecipeHolder<AbstractForgingRecipe> holder, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) slot.draw(g, x * 18, y * 18);
        gui.getOutputSlot().draw(g, 92, 14);
        arrowEmpty.draw(g, 61, 18);
        flameEmpty.draw(g, 65, 37);
        flame.draw(g, 65, 37);
        int ticks = holder.value().getCookTime();
        if (ticks > 0) {
            Component time = Component.literal((ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.format("%.1f", ticks / 20f)) + " s");
            var font = Minecraft.getInstance().font;
            g.drawString(font, time, WIDTH - font.width(time), 0, 0xFF808080, false);
        }
    }
}
