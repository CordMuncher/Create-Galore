package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.creategalore.bridge.client.ShipShadowWater;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps the ship hull texture's unit out of the pack's own textures in the shadow programs that test for hulls, and
 * points the hull sampler at that unit once, so it never shares a unit with a pack texture of another type.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers", remap = false)
public abstract class IrisShadowHullUnitMixin {
    @ModifyVariable(method = "builder", at = @At("HEAD"), argsOnly = true)
    private static Set<Integer> create_galore_bridge$keepHullUnit(Set<Integer> reserved, @Local(argsOnly = true) int program) {
        if (!ShipShadowWater.hasTest(program)) return reserved;
        ShipShadowWater.setUpProgram(program);
        Set<Integer> kept = new HashSet<>(reserved);
        kept.add(ShipShadowWater.UNIT);
        return kept;
    }
}
