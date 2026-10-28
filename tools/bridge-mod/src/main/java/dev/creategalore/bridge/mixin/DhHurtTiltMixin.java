package dev.creategalore.bridge.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.fml.ModList;
import net.ranold.ssrd.SSRDState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Separate Sable Render Distance 1.8.7 hands Distant Horizons the projection the level renders with, which it saves
 * whenever GameRenderer.resetProjectionMatrix gets a perspective one, told apart by m33 == 0. The hurt tilt and the
 * view bobbing go into that projection without shaders: the tilt turns about the hit's direction, the bobbing then
 * shifts sideways and down, and together they leave m33 a little off 0 (0.004 to 0.007 at a hit while moving). That
 * frame's projection is not saved, so Distant Horizons draws with the last one that was, the held item's from the
 * frame before: field of view 70, no tilt and no bobbing. For the half second of a hit the far terrain stopped
 * following the camera and, at any other field of view, changed size too. The projection now goes over right before
 * the level renders, every frame, whatever is in it. With a shader pack Iris moves the bobbing into the model-view
 * and m33 stays 0, so this changes nothing there.
 */
@Mixin(GameRenderer.class)
public abstract class DhHurtTiltMixin {
    // Looked up on first use, like HalfBuiltNotFoodMixin.
    @Unique
    private static Boolean createGalore$ssrd;

    @ModifyArg(method = "renderLevel", index = 6, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private Matrix4f create_galore_bridge$levelProjectionForDh(Matrix4f projection) {
        if (createGalore$ssrd == null) createGalore$ssrd = ModList.get().isLoaded("ssrd");
        if (createGalore$ssrd) SSRDState.LEVEL_PROJ_MATRIX = new Matrix4f(projection);
        return projection;
    }
}
