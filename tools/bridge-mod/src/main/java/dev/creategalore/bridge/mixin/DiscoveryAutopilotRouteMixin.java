package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.FlyoverSkyRoute;
import me.corvino.aeronauticsdiscovery.autopilot.Autopilot;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every Discovery pilot (flyover planes, patrols, the Soaring Trader) steers by the sum of its plan's goals. The
 * route planner gets the last word on that sum, so a plane climbs over or turns around what lies ahead whatever plan
 * it was given, including planes already flying in a saved world. See {@link FlyoverSkyRoute}.
 */
@Mixin(value = Autopilot.class, remap = false)
public abstract class DiscoveryAutopilotRouteMixin {
    @Unique
    private final FlyoverSkyRoute.State create_galore_bridge$route = new FlyoverSkyRoute.State();

    @Inject(method = "bias", at = @At("RETURN"), cancellable = true)
    private void create_galore_bridge$planRoute(AutopilotContext context, CallbackInfoReturnable<AutopilotBias> cir) {
        cir.setReturnValue(FlyoverSkyRoute.steer(create_galore_bridge$route, context, cir.getReturnValue()));
    }
}
