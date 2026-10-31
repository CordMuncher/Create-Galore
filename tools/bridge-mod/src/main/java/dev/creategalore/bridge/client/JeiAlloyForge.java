package dev.creategalore.bridge.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Alloyed only shows its Alloy Forge recipes in EMI, and the pack runs JEI, so steel tools and armor looked like they
 * had only the Mechanical Crafting recipe. This adds an Alloy Forge page to JEI. The Alloyed classes stay in
 * {@link JeiAlloyForgeCategory}, so nothing loads them when Alloyed isn't there.
 */
@JeiPlugin
public final class JeiAlloyForge implements IModPlugin {
    static boolean present() {
        return ModList.get().isLoaded("alloyed");
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "alloy_forge");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (present()) JeiAlloyForgeCategory.registerCategory(registration);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (present()) JeiAlloyForgeCategory.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (present()) JeiAlloyForgeCategory.registerCatalyst(registration);
    }
}
