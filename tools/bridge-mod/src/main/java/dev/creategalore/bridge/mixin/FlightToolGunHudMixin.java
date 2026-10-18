package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Create Flight Control prints the tool gun's status ("No block selected  Saved selections: 0") 36 px above the
 * bottom of the screen, which is the health and hunger row, so the hearts can't be read while the tool gun is in
 * hand. Draw it above the thirst, air and mana rows and the action bar instead.
 */
@Mixin(targets = "ace.flight.neoforge.client.FlightControlToolGunHudOverlay", remap = false)
public abstract class FlightToolGunHudMixin {
    @ModifyConstant(method = "render", constant = @Constant(intValue = 36))
    private static int create_galore_bridge$aboveStatusBars(int bottomMargin) {
        return 82;
    }
}
