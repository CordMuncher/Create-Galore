package dev.creategalore.bridge.mixin;

import com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create Deep Seas binds its hull culling depth textures to texture units 8 and 9 while the terrain draws. Up to
 * 3.4.1 it kept those two units out of every Iris shader program; since 3.4.2 only programs that read the hull
 * textures keep them free, so the shader pack's own textures can land on 8 and 9 in the other programs and get
 * swapped for the hull textures: with any shader pack the sea drew as flat grey and ice in it looked like dark
 * glass. Keep units 8 and 9 free in every program again, unless that program has no room for two more textures
 * (the case 3.4.2 fixed for very heavy packs on graphics cards with few units).
 */
@Mixin(value = SodiumWaterOcclusionBridge.class, remap = false)
public abstract class SubmarineHullUnitsMixin {
    @Inject(method = "reserveUnits", at = @At("RETURN"), cancellable = true)
    private static void create_galore_bridge$keepUnitsFree(int program, Set<Integer> reserved, CallbackInfoReturnable<Set<Integer>> cir) {
        Set<Integer> units = cir.getReturnValue();
        if (units.contains(8) && units.contains(9)) return;
        if (SubmarineOcclusionBridgeInvoker.create_galore_bridge$samplerUnits(program) + units.size() + 2
                > SubmarineOcclusionBridgeInvoker.create_galore_bridge$maxTextureUnits()) return;
        Set<Integer> kept = new HashSet<>(units);
        kept.add(8);
        kept.add(9);
        cir.setReturnValue(kept);
    }
}
