package dev.creategalore.bridge.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** JEI hands its runtime to plugins; the ingredient list overlay knows whether its search box has keyboard focus. */
@JeiPlugin
public final class JeiSearchFocus implements IModPlugin {
    private static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "search_focus");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    static boolean hasFocus() {
        IJeiRuntime current = runtime;
        return current != null && current.getIngredientListOverlay().hasKeyboardFocus();
    }
}
