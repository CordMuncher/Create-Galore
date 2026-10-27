package dev.creategalore.bridge.mixin;

import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris turns Sodium's terrain draws into tessellation patches while its "using tessellation" flag is on. A program with
 * tessellation stages turns it on, and Minecraft's glUseProgram turns it off again, but Sodium binds its terrain
 * programs without that call. IrisVeil draws Aeronautics' levitite (whose Veil shader has tessellation stages) in the
 * shadow pass right before the translucent terrain, so the flag was still on there: the driver refused the patches
 * (GL_INVALID_OPERATION), and water and stained glass never reached the shadow map (no water caustics, no coloured
 * shadows through glass) whenever a Sable ship was in view. Each Sodium program now starts with the flag off and turns
 * it on itself if it has tessellation stages.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.SodiumShader", remap = false)
public abstract class IrisSodiumTessellationResetMixin {
    @Inject(method = "setupState", at = @At("HEAD"))
    private void create_galore_bridge$resetTessellation(CallbackInfo ci) {
        ImmediateState.usingTessellation = false;
    }
}
