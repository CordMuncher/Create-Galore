package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.AnimalCare;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a tamed animal has its daily care check, its level reads as daytime light, see {@link AnimalCare}. */
@Mixin(Level.class)
public abstract class LevelDaylightCheckMixin {
    @Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$daylight(CallbackInfoReturnable<Integer> cir) {
        if (AnimalCare.daylightCheck((Level) (Object) this)) cir.setReturnValue(0);
    }
}
