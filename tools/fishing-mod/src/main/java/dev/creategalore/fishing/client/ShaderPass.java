package dev.creategalore.fishing.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/**
 * Whether a shader pack (Iris) is drawing its shadow map right now. That pass draws entities and held items again
 * from the sun's point of view, with its own matrices, so nothing measured during it is where the player sees it.
 * Looked up by reflection: Iris is optional.
 */
final class ShaderPass {
    private ShaderPass() {
    }

    private static Object api;
    private static Method shadowPass;
    private static boolean looked;

    static boolean shadow() {
        if (!looked) {
            looked = true;
            if (ModList.get().isLoaded("iris")) {
                try {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    shadowPass = c.getMethod("isRenderingShadowPass");
                } catch (ReflectiveOperationException | LinkageError e) {
                    shadowPass = null;
                }
            }
        }
        if (shadowPass == null) return false;
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
